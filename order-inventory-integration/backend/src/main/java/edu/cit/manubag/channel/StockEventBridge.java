package edu.cit.manubag.channel;

import edu.cit.manubag.event.LowStockEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class StockEventBridge {

    private final TianggeClient tianggeClient;

    StockEventBridge(TianggeClient tianggeClient) {
        this.tianggeClient = tianggeClient;
    }

    @EventListener
    public void onStockChanged(LowStockEvent event) {
        // Pass a list containing the stock item
        tianggeClient.updateStock(List.of(
                new TianggeDtos.StockItem(event.productId(), event.remainingStock())
        ));
    }
}