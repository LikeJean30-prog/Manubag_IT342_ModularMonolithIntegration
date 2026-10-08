package edu.cit.manubag.channel;

import edu.cit.manubag.inventory.InventoryItem;
import edu.cit.manubag.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class StockSync {

    private static final Logger log = LoggerFactory.getLogger(StockSync.class);

    private final TianggeClient tianggeClient;
    private final InventoryService inventoryService;
    private final Map<String, Long> dirty = new ConcurrentHashMap<>(); // productId -> when it changed
    private volatile boolean initialised = false; // true once the first full stock publish has reached Tiangge

    StockSync(TianggeClient tianggeClient, InventoryService inventoryService) {
        this.tianggeClient = tianggeClient;
        this.inventoryService = inventoryService;
    }

    void markDirty(String productId) {
        if (TianggeShopAdapter.isListed(productId)) {
            dirty.putIfAbsent(productId, System.currentTimeMillis());
        }
    }

    /** After a decision that changes stock has reached Tiangge, a fresh stock update must follow it. */
    void markAllDirty() {
        TianggeShopAdapter.listedProductIds().forEach(this::markDirty);
    }

    /** Sends the stock of every listed product (right after the listings are published). */
    boolean publishAll() {
        List<TianggeDtos.StockItem> items = inventoryService.getAllItems().stream()
                .filter(i -> TianggeShopAdapter.isListed(i.productId()))
                .map(i -> new TianggeDtos.StockItem(i.productId(), i.stock()))
                .toList();
        boolean ok = items.isEmpty() || tianggeClient.updateStock(items);
        if (ok) {
            initialised = true;
        }
        return ok;
    }

    /** Nothing may be decided or resolved before Tiangge has our current stock. */
    boolean isInitialised() {
        return initialised;
    }

    /**
     * Publishes one product's current stock immediately (single attempt). Used right after a supplier delivery,
     * so Tiangge knows about the new units before any backorder that depends on them is resolved.
     */
    boolean publishNow(String productId) {
        if (!TianggeShopAdapter.isListed(productId)) {
            return true;
        }
        Long changedAt = dirty.remove(productId);
        try {
            InventoryItem item = inventoryService.getItem(productId);
            if (tianggeClient.updateStockQuick(List.of(new TianggeDtos.StockItem(item.productId(), item.stock())))) {
                log.info("Stock sent to Tiangge after delivery [{}={}]", item.productId(), item.stock());
                return true;
            }
        } catch (Exception e) {
            log.warn("Could not publish stock of {} after delivery: {}", productId, e.getMessage());
        }
        dirty.putIfAbsent(productId, changedAt != null ? changedAt : System.currentTimeMillis());
        return false;
    }

    void flush() {
        if (dirty.isEmpty()) {
            return;
        }

        Map<String, Long> batch = new HashMap<>();
        for (String productId : new ArrayList<>(dirty.keySet())) {
            Long changedAt = dirty.remove(productId);
            if (changedAt != null) {
                batch.put(productId, changedAt);
            }
        }
        long oldestChange = batch.values().stream().min(Long::compare).orElse(System.currentTimeMillis());

        List<TianggeDtos.StockItem> items = new ArrayList<>();
        for (String productId : batch.keySet()) {
            try {
                InventoryItem item = inventoryService.getItem(productId); // read right before sending
                items.add(new TianggeDtos.StockItem(item.productId(), item.stock()));
            } catch (Exception e) {
                log.warn("Could not read stock of {} for Tiangge: {}", productId, e.getMessage());
            }
        }
        if (items.isEmpty()) {
            return;
        }

        if (tianggeClient.updateStockQuick(items)) { // one attempt; the next round reads fresh numbers again
            log.info("Stock sent to Tiangge {} ({} ms after the first change)", items,
                    System.currentTimeMillis() - oldestChange);
        } else {
            batch.forEach(dirty::putIfAbsent);
        }
    }
}