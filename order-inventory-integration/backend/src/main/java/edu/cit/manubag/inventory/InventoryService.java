package edu.cit.manubag.inventory;
import java.util.List;

public interface InventoryService {

    InventoryItem getItem(String productId);
    List<InventoryItem> getAllItems();

    boolean reserve(String productId, int quantity);
    void restock(String productId, int quantity);
}
