package edu.cit.manubag.inventory;

import edu.cit.manubag.event.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.inventory.low-stock-threshold:5}")
    private int lowStockThreshold;

    InventoryServiceImpl(InventoryRepository inventoryRepository, ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItem getItem(String productId) {
        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return toItem(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll().stream()
                .map(this::toItem)
                .toList();
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

        if (inventory.getStock() < lowStockThreshold) {
            eventPublisher.publishEvent(
                    new LowStockEvent(inventory.getProductId(), inventory.getName(), inventory.getStock()));
        }

        return true;
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        Inventory inventory = inventoryRepository.lockByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        inventory.setStock(inventory.getStock() + quantity);
        inventoryRepository.save(inventory);
    }

    private InventoryItem toItem(Inventory inventory) {
        return new InventoryItem(inventory.getProductId(), inventory.getName(), inventory.getStock());
    }
}
