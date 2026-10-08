package edu.cit.manubag.shop;

import edu.cit.manubag.event.OrderPlacedEvent;
import edu.cit.manubag.event.OrderRejectedEvent;
import edu.cit.manubag.inventory.InventoryItem;
import edu.cit.manubag.inventory.InventoryService;
import edu.cit.manubag.inventory.ProductNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        List<Validation> validations = new ArrayList<>();
        boolean allValid = true;

        for (OrderItemRequest item : request.items()) {
            try {
                InventoryItem current = inventoryService.getItem(item.productId());
                if (item.quantity() > current.stock()) {
                    validations.add(new Validation(item, "INSUFFICIENT_STOCK", current));
                    allValid = false;
                } else {
                    validations.add(new Validation(item, "RESERVED", current));
                }
            } catch (ProductNotFoundException ex) {
                validations.add(new Validation(item, "PRODUCT_NOT_FOUND", null));
                allValid = false;
            }
        }

        return allValid ? confirmOrder(request, validations) : rejectOrder(validations);
    }

    private OrderResponse rejectOrder(List<Validation> validations) {
        String reason = validations.stream()
                .filter(v -> !"RESERVED".equals(v.outcome()))
                .map(this::describeFailure)
                .collect(Collectors.joining("; "));

        Order order = new Order(OrderStatus.REJECTED, reason);
        List<OrderLineResult> lineResults = new ArrayList<>();

        for (Validation v : validations) {
            order.addItem(v.item().productId(), v.item().quantity());
            String outcome = "RESERVED".equals(v.outcome()) ? "NOT_RESERVED" : v.outcome();
            lineResults.add(new OrderLineResult(v.item().productId(), outcome));
        }

        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), reason));

        return new OrderResponse(order.getOrderId(), OrderStatus.REJECTED.name(), reason, lineResults, List.of());
    }

    private OrderResponse confirmOrder(OrderRequest request, List<Validation> validations) {
        Order order = new Order(OrderStatus.CONFIRMED, null);
        List<OrderLineResult> lineResults = new ArrayList<>();

        for (Validation v : validations) {
            boolean reserved = inventoryService.reserve(v.item().productId(), v.item().quantity());
            if (!reserved) {
                // race between validation and reservation — abort, @Transactional rolls back everything
                throw new IllegalStateException(
                        "Stock for " + v.item().productId() + " changed before it could be reserved");
            }
            order.addItem(v.item().productId(), v.item().quantity());
            lineResults.add(new OrderLineResult(v.item().productId(), "RESERVED"));
        }

        orderRepository.save(order);

        List<InventoryItem> inventorySnapshot = request.items().stream()
                .map(item -> inventoryService.getItem(item.productId()))
                .toList();

        eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId()));

        return new OrderResponse(order.getOrderId(), OrderStatus.CONFIRMED.name(), null, lineResults, inventorySnapshot);
    }

    @Transactional
    public CancelResponse cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new OrderCancellationException(orderId, order.getStatus());
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.restock(item.getProductId(), item.getQuantity());
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        return new CancelResponse(orderId, OrderStatus.CANCELLED.name(), "Order cancelled and stock restored.");
    }

    /** Records an order we cannot fill (for example an unknown product) as REJECTED, without touching Inventory. */
    @Transactional
    public Long recordRejectedOrder(OrderRequest request, String reason) {
        Order order = new Order(OrderStatus.REJECTED, reason);
        // order_items.product_id has a foreign key to inventory, so lines for unknown products are left out
        Set<String> known = inventoryService.getAllItems().stream()
                .map(InventoryItem::productId)
                .collect(Collectors.toSet());
        for (OrderItemRequest item : request.items()) {
            if (known.contains(item.productId())) {
                order.addItem(item.productId(), item.quantity());
            }
        }
        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), reason));
        return order.getOrderId();
    }

    /** Records an order that cannot be filled yet because the missing stock is on its way. Reserves nothing. */
    @Transactional
    public Long placeBackorder(OrderRequest request, String reason) {
        Order order = new Order(OrderStatus.BACKORDERED, reason);
        for (OrderItemRequest item : request.items()) {
            order.addItem(item.productId(), item.quantity());
        }
        orderRepository.save(order);
        return order.getOrderId();
    }

    @Transactional(readOnly = true)
    public List<OrderLineItem> getOrderLines(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return order.getItems().stream()
                .map(item -> new OrderLineItem(item.getProductId(), item.getQuantity()))
                .toList();
    }

    /**
     * Fills a backordered order: reserves every line (all-or-nothing) and confirms it, or cancels it
     * if the stock still is not there. Returns CONFIRMED or CANCELLED; any other status is returned unchanged.
     */
    @Transactional
    public OrderStatus resolveBackorder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.BACKORDERED) {
            return order.getStatus();
        }

        boolean canFill = true;
        for (OrderItem item : order.getItems()) {
            if (inventoryService.getItem(item.getProductId()).stock() < item.getQuantity()) {
                canFill = false;
                break;
            }
        }

        if (canFill) {
            for (OrderItem item : order.getItems()) {
                if (!inventoryService.reserve(item.getProductId(), item.getQuantity())) {
                    throw new IllegalStateException("Stock for " + item.getProductId() + " changed before it could be reserved");
                }
            }
            order.setStatus(OrderStatus.CONFIRMED);
            order.setReason(null);
            orderRepository.save(order);
            eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId()));
            return OrderStatus.CONFIRMED;
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setReason("Could not be filled after the supplier delivery");
        orderRepository.save(order);
        return OrderStatus.CANCELLED;
    }

    /**
     * Cancels a confirmed order (restocking it) or a backordered one, without throwing when there is
     * nothing to cancel. Returns true if this call cancelled it.
     */
    @Transactional
    public boolean cancelIfActive(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return false;
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
            }
        } else if (order.getStatus() != OrderStatus.BACKORDERED) {
            return false;
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        return true;
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> getOrderHistory() {
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(order -> new OrderSummary(
                        order.getOrderId(), order.getStatus().name(), order.getReason(), order.getCreatedAt(),
                        order.getItems().stream()
                                .map(item -> new OrderLineItem(item.getProductId(), item.getQuantity()))
                                .toList()))
                .toList();
    }

    private String describeFailure(Validation v) {
        if ("INSUFFICIENT_STOCK".equals(v.outcome())) {
            return "Requested quantity (%d) exceeds available stock (%d) for %s"
                    .formatted(v.item().quantity(), v.current().stock(), v.item().productId());
        }
        return "No product found with id: " + v.item().productId();
    }

    private record Validation(OrderItemRequest item, String outcome, InventoryItem current) {}
}