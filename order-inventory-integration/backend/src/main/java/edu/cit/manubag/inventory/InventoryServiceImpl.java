package edu.cit.manubag.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItem getItem(String productId) {
        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return toItem(inventory);
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {

        Inventory inventory = inventoryRepository.lockByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (quantity <= 0 || quantity > inventory.getStock()) {
            return false;
        }

        inventory.setStock(inventory.getStock() - quantity);
        inventoryRepository.save(inventory);
        return true;
    }

    private InventoryItem toItem(Inventory inventory) {
        return new InventoryItem(inventory.getProductId(), inventory.getName(), inventory.getStock());
    }
}
