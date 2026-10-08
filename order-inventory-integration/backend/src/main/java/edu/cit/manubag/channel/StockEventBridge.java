package edu.cit.manubag.channel;

import edu.cit.manubag.event.StockChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Task 3: any stock change (order, cancellation, delivery, UI) reaches Tiangge, after the change is committed. */
@Component
class StockEventBridge {

    private final StockSync stockSync;

    StockEventBridge(StockSync stockSync) {
        this.stockSync = stockSync;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onStockChanged(StockChangedEvent event) {
        stockSync.markDirty(event.productId());
    }
}
