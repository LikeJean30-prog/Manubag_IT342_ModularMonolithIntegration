package edu.cit.manubag.shop;

import edu.cit.manubag.inventory.InventoryItem;
import edu.cit.manubag.inventory.InventoryService;
import edu.cit.manubag.inventory.ProductNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        try {
            boolean reserved = inventoryService.reserve(request.productId(), request.quantity());

            if (!reserved) {
                InventoryItem current = inventoryService.getItem(request.productId());
                String reason = "Requested quantity (%d) exceeds available stock (%d) for %s"
                        .formatted(request.quantity(), current.stock(), current.productId());

                orderRepository.save(new Order(request.productId(), request.quantity(),
                        OrderStatus.REJECTED, reason));

                return new OrderResponse(OrderStatus.REJECTED.name(), reason, current);
            }

            orderRepository.save(new Order(request.productId(), request.quantity(),
                    OrderStatus.CONFIRMED, null));

            InventoryItem updated = inventoryService.getItem(request.productId());
            return new OrderResponse(OrderStatus.CONFIRMED.name(), null, updated);

        } catch (ProductNotFoundException ex) {
            orderRepository.save(new Order(request.productId(), request.quantity(),
                    OrderStatus.REJECTED, ex.getMessage()));
            return new OrderResponse(OrderStatus.REJECTED.name(), ex.getMessage(), null);
        }
    }
}
