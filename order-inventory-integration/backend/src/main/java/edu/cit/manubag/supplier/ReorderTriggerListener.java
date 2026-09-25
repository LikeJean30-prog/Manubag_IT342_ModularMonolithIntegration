package edu.cit.manubag.supplier;

import edu.cit.manubag.event.LowStockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class ReorderTriggerListener {

    private static final Logger log = LoggerFactory.getLogger(ReorderTriggerListener.class);

    private final SupplierGateway supplierGateway;
    private final SupplierProperties properties;

    ReorderTriggerListener(SupplierGateway supplierGateway, SupplierProperties properties) {
        this.supplierGateway = supplierGateway;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onLowStock(LowStockEvent event) {
        int unitsNeeded = Math.max(1, properties.getReorderTargetLevel() - event.remainingStock());
        log.info("Low stock on {} ({} left) - triggering reorder for {} unit(s)",
                event.productId(), event.remainingStock(), unitsNeeded);
        ReorderResult result = supplierGateway.reorder(event.productId(), unitsNeeded);
        log.info("Reorder for {}: {} (buyerRef={}, po={})",
                event.productId(), result.status(), result.buyerRef(), result.poNumber());
    }
}
