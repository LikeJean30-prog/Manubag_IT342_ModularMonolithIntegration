package edu.cit.manubag.channel;

import edu.cit.manubag.inventory.InventoryItem;
import edu.cit.manubag.inventory.InventoryService;
import edu.cit.manubag.inventory.ProductNotFoundException;
import edu.cit.manubag.shop.OrderItemRequest;
import edu.cit.manubag.shop.OrderRequest;
import edu.cit.manubag.shop.OrderResponse;
import edu.cit.manubag.shop.OrderService;
import edu.cit.manubag.shop.OrderStatus;
import edu.cit.manubag.supplier.ReorderResult;
import edu.cit.manubag.supplier.SupplierGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class FeedProcessor {

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final TianggeClient tianggeClient;
    private final ProcessedEventRepository processedRepository;

    FeedProcessor(OrderService orderService, InventoryService inventoryService,
                  SupplierGateway supplierGateway, TianggeClient tianggeClient,
                  ProcessedEventRepository processedRepository) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.tianggeClient = tianggeClient;
        this.processedRepository = processedRepository;
    }

    @Transactional
    public void processEvent(TianggeDtos.FeedEvent event) {
        if (processedRepository.existsById(event.eventId())) {
            return;
        }

        if ("ORDER_PLACED".equals(event.type()) || "ORDER_CREATED".equals(event.type())) {
            handleNewOrder(event);
        } else if ("ORDER_CANCELLED".equals(event.type())) {
            handleCancellation(event);
        }

        processedRepository.markProcessed(event.eventId());
    }

    private void handleNewOrder(TianggeDtos.FeedEvent event) {
        // Updated field names: lines(), sellerSku(), qty()
        List<OrderItemRequest> orderItems = event.lines().stream()
                .map(item -> new OrderItemRequest(item.sellerSku(), item.qty()))
                .toList();

        // 1. Check stock for all items
        boolean hasStock = true;
        for (OrderItemRequest item : orderItems) {
            try {
                InventoryItem stockItem = inventoryService.getItem(item.productId());
                if (stockItem.stock() < item.quantity()) {
                    hasStock = false;
                    break;
                }
            } catch (ProductNotFoundException e) {
                hasStock = false;
                break;
            }
        }

        if (hasStock) {
            OrderResponse response = orderService.placeOrder(new OrderRequest(orderItems));
            String shopOrderId = response.orderId() != null ? String.valueOf(response.orderId()) : event.orderId();

            if (OrderStatus.CONFIRMED.name().equals(response.status())) {
                tianggeClient.sendOrderDecision(event.orderId(), "ACCEPTED", shopOrderId);
            } else {
                tianggeClient.sendOrderDecision(event.orderId(), "REJECTED", shopOrderId);
            }
        } else {
            boolean canBackorder = false;
            for (OrderItemRequest item : orderItems) {
                ReorderResult result = supplierGateway.reorder(item.productId(), item.quantity());
                if (result != null && result.poNumber() != null) {
                    canBackorder = true;
                }
            }

            if (canBackorder) {
                tianggeClient.sendOrderDecision(event.orderId(), "BACKORDERED", event.orderId());
            } else {
                tianggeClient.sendOrderDecision(event.orderId(), "REJECTED", event.orderId());
            }
        }
    }

    private void handleCancellation(TianggeDtos.FeedEvent event) {
        try {
            Long localId = Long.parseLong(event.orderId());
            orderService.cancelOrder(localId);
            tianggeClient.confirmCancellation(event.orderId());
        } catch (Exception e) {
            tianggeClient.confirmCancellation(event.orderId());
        }
    }
}