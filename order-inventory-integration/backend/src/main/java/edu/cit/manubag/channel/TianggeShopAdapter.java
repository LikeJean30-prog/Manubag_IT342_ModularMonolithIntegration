package edu.cit.manubag.channel;

import edu.cit.manubag.inventory.InventoryItem;
import edu.cit.manubag.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
class TianggeShopAdapter implements ShopPort {

    private static final Logger log = LoggerFactory.getLogger(TianggeShopAdapter.class);

    // Lab 3 mapping: our product -> LegacySupply SupplierSku
    private static final Map<String, String> SUPPLIER_SKU_MAP = Map.of(
            "P100", "QEX-1215",
            "P200", "QEX-6534",
            "P300", "QEX-7491"
    );

    private final TianggeClient tianggeClient;
    private final InventoryService inventoryService;
    private final StockSync stockSync;

    TianggeShopAdapter(TianggeClient tianggeClient, InventoryService inventoryService, StockSync stockSync) {
        this.tianggeClient = tianggeClient;
        this.inventoryService = inventoryService;
        this.stockSync = stockSync;
    }

    static boolean isListed(String productId) {
        return SUPPLIER_SKU_MAP.containsKey(productId);
    }

    static java.util.Set<String> listedProductIds() {
        return SUPPLIER_SKU_MAP.keySet();
    }

    @Override
    public boolean publishListings() {
        try {
            List<TianggeDtos.ListingItem> listings = new ArrayList<>();
            for (InventoryItem item : inventoryService.getAllItems()) {
                String supplierSku = SUPPLIER_SKU_MAP.get(item.productId());
                if (supplierSku != null) {
                    listings.add(new TianggeDtos.ListingItem(
                            item.productId(),
                            item.name() != null ? item.name() : item.productId(),
                            supplierSku));
                }
            }
            if (listings.isEmpty()) {
                log.warn("No inventory products match the supplier mapping; nothing to publish");
                return false;
            }
            // Buyers only see listings that have stock information, so stock follows immediately.
            return tianggeClient.publishListings(listings) && stockSync.publishAll();
        } catch (Exception e) {
            log.error("Publishing listings failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void syncStock(String productId, int availableQuantity) {
        tianggeClient.updateStock(List.of(new TianggeDtos.StockItem(productId, availableQuantity)));
    }

    @Override
    public void sendHeartbeat() {
        tianggeClient.sendHeartbeat();
    }
}
