package edu.cit.manubag.inventory;

public interface InventoryService {

    InventoryItem getItem(String productId);

    boolean reserve(String productId, int quantity);
}
