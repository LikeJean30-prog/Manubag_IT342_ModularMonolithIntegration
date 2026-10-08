package edu.cit.manubag.channel;

import edu.cit.manubag.inventory.InventoryService;
import edu.cit.manubag.inventory.ProductNotFoundException;
import edu.cit.manubag.shop.OrderItemRequest;
import edu.cit.manubag.shop.OrderRequest;
import edu.cit.manubag.shop.OrderResponse;
import edu.cit.manubag.shop.OrderService;
import edu.cit.manubag.shop.OrderStatus;
import edu.cit.manubag.supplier.ReorderResult;
import edu.cit.manubag.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;


@Component
class FeedProcessor {

    private static final Logger log = LoggerFactory.getLogger(FeedProcessor.class);

    private enum Plan { FILL, BACKORDER, REJECT, UNKNOWN_PRODUCT }

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final ProcessedEventRepository processedRepository;
    private final TianggeOrderRepository tianggeOrders;
    private final OutboxRepository outbox;
    private final TransactionTemplate tx;
    private final int reorderTarget;

    FeedProcessor(OrderService orderService, InventoryService inventoryService, SupplierGateway supplierGateway,
                  ProcessedEventRepository processedRepository, TianggeOrderRepository tianggeOrders,
                  OutboxRepository outbox, PlatformTransactionManager transactionManager,
                  @Value("${app.supplier.reorder-target-level:20}") int reorderTarget) {
        this.reorderTarget = reorderTarget;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.processedRepository = processedRepository;
        this.tianggeOrders = tianggeOrders;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void processEvent(TianggeDtos.FeedEvent event) {
        if (processedRepository.existsById(event.eventId())) {
            return; // redelivered event
        }
        if ("ORDER_PLACED".equals(event.type()) || "ORDER_CREATED".equals(event.type())) {
            handleNewOrder(event);
        } else if ("ORDER_CANCELLED".equals(event.type())) {
            handleCancellation(event);
        } else {
            log.info("Ignoring feed event {} of type {}", event.eventId(), event.type());
            tx.executeWithoutResult(s -> processedRepository.markProcessed(event.eventId()));
        }
    }

    // ---------------------------------------------------------------- new orders

    private void handleNewOrder(TianggeDtos.FeedEvent event) {
        String tianggeId = event.orderId();
        if (tianggeOrders.existsById(tianggeId)) {
            // same order delivered again under another eventId: it already became exactly one order
            tx.executeWithoutResult(s -> processedRepository.markProcessed(event.eventId()));
            return;
        }

        List<OrderItemRequest> items = mergeLines(event.lines());
        if (items.isEmpty()) {
            tx.executeWithoutResult(s -> {
                tianggeOrders.save(new TianggeOrder(tianggeId, null, TianggeOrderStatus.REJECTED));
                outbox.save(OutboxMessage.decision(tianggeId, "REJECTED", tianggeId, "Order has no lines"));
                processedRepository.markProcessed(event.eventId());
            });
            return;
        }

        Plan plan = plan(items); // may call LegacySupply, so it runs before (not inside) the transaction

        tx.executeWithoutResult(s -> {
            String decision;
            String reason = null;
            Long localId;
            TianggeOrderStatus status;

            if (plan == Plan.UNKNOWN_PRODUCT) {
                reason = "One or more products are not sold by this shop";
                localId = orderService.recordRejectedOrder(new OrderRequest(items), reason);
                decision = "REJECTED";
                status = TianggeOrderStatus.REJECTED;
            } else if (plan == Plan.BACKORDER) {
                localId = orderService.placeBackorder(new OrderRequest(items), "Waiting for supplier delivery");
                decision = "BACKORDERED";
                status = TianggeOrderStatus.BACKORDERED;
            } else {
                OrderResponse response = orderService.placeOrder(new OrderRequest(items));
                localId = response.orderId();
                if (OrderStatus.CONFIRMED.name().equals(response.status())) {
                    decision = "ACCEPTED";
                    status = TianggeOrderStatus.ACCEPTED;
                } else {
                    decision = "REJECTED";
                    status = TianggeOrderStatus.REJECTED;
                    reason = response.reason();
                }
            }

            tianggeOrders.save(new TianggeOrder(tianggeId, localId, status));
            outbox.save(OutboxMessage.decision(tianggeId, decision, String.valueOf(localId), reason));
            processedRepository.markProcessed(event.eventId());
            log.info("Tiangge order {} -> local order {} -> {}", tianggeId, localId, decision);
        });
    }

    /**
     * FILL: every line is in stock. UNKNOWN_PRODUCT: a line names a product we do not have. BACKORDER: some lines are short, but each short product already has
     * a LegacySupply purchase order placed. REJECT: anything else.
     */
    private Plan plan(List<OrderItemRequest> items) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            try {
                int stock = inventoryService.getItem(item.productId()).stock();
                if (stock < item.quantity()) {
                    missing.put(item.productId(), item.quantity() - stock);
                }
            } catch (ProductNotFoundException e) {
                return Plan.UNKNOWN_PRODUCT;
            }
        }
        if (missing.isEmpty()) {
            return Plan.FILL;
        }

        for (Map.Entry<String, Integer> line : missing.entrySet()) {
            try {
                ReorderResult reorder = supplierGateway.reorder(line.getKey(), Math.max(line.getValue(), reorderTarget));
                if (reorder == null || reorder.poNumber() == null) {
                    return Plan.REJECT; // no purchase order really placed, so nothing is coming
                }
            } catch (Exception e) {
                log.warn("Could not reorder {} for a Tiangge order: {}", line.getKey(), e.getMessage());
                return Plan.REJECT;
            }
        }
        return Plan.BACKORDER;
    }

    private static List<OrderItemRequest> mergeLines(List<TianggeDtos.FeedItem> lines) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        if (lines != null) {
            for (TianggeDtos.FeedItem line : lines) {
                if (line.sellerSku() != null && line.qty() > 0) {
                    merged.merge(line.sellerSku(), line.qty(), Integer::sum);
                }
            }
        }
        return merged.entrySet().stream()
                .map(e -> new OrderItemRequest(e.getKey(), e.getValue()))
                .toList();
    }

    // ---------------------------------------------------------------- cancellations

    private void handleCancellation(TianggeDtos.FeedEvent event) {
        String tianggeId = event.orderId();
        Optional<TianggeOrder> known = tianggeOrders.findById(tianggeId);

        tx.executeWithoutResult(s -> {
            if (known.isPresent()) {
                TianggeOrder order = known.get();
                if (order.getLocalOrderId() != null) {
                    boolean cancelled = orderService.cancelIfActive(order.getLocalOrderId()); // restocks inventory
                    log.info("Tiangge cancellation {} -> local order {} cancelled={}",
                            tianggeId, order.getLocalOrderId(), cancelled);
                }
                order.setStatus(TianggeOrderStatus.CANCELLED_BY_CUSTOMER);
                tianggeOrders.save(order);
            } else {
                log.warn("Cancellation for unknown Tiangge order {}; confirming without a local change", tianggeId);
            }
            outbox.save(OutboxMessage.cancellation(tianggeId)); // confirmed only after the local cancel committed
            processedRepository.markProcessed(event.eventId());
        });
    }
}
