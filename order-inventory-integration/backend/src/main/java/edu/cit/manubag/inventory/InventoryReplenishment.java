package edu.cit.manubag.inventory;

import edu.cit.manubag.event.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class InventoryReplenishmentListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryReplenishmentListener.class);

    private final InventoryService inventoryService;

    InventoryReplenishmentListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    @Transactional
    void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        inventoryService.restock(event.productId(), event.units());
        log.info("Restocked {} unit(s) of {} from supplier order {}",
                event.units(), event.productId(), event.supplierOrderId());
    }
}
