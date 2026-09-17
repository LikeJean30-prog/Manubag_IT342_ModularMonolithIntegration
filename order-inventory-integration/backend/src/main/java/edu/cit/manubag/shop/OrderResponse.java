package edu.cit.manubag.shop;

import edu.cit.manubag.inventory.InventoryItem;
import java.util.List;

public record OrderResponse(
        Long orderId, String status, String reason,
        List<OrderLineResult> items, List<InventoryItem> inventory
) {
}
