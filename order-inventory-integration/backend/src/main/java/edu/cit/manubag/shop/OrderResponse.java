package edu.cit.manubag.shop;

import edu.cit.manubag.inventory.InventoryItem;

public record OrderResponse(
        String status,
        String reason,
        InventoryItem inventory
) {
}
