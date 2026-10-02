package edu.cit.manubag.channel;

import edu.cit.manubag.inventory.InventoryItem;
import edu.cit.manubag.inventory.InventoryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TianggeShopAdapter implements ShopPort {

    private final TianggeClient tianggeClient;
    private final InventoryService inventoryService;

    private static final Map<String, String> SUPPLIER_SKU_MAP = Map.of(
            "P100", "QEX-1215",
            "P200", "QEX-6534",
            "P300", "QEX-7491"
    );

    public TianggeShopAdapter(TianggeClient tianggeClient, InventoryService inventoryService) {
        this.tianggeClient = tianggeClient;
        this.inventoryService = inventoryService;
    }

    @Override
    public void publishListings() {
        try {
            List<InventoryItem> items = inventoryService.getAllItems();
            List<TianggeDtos.ListingItem> listings = new ArrayList<>();

            for (InventoryItem item : items) {
                String supplierSku = SUPPLIER_SKU_MAP.get(item.productId());
                if (supplierSku != null) {
                    listings.add(new TianggeDtos.ListingItem(
                            item.productId(), // sellerSku
                            item.name() != null ? item.name() : item.productId(),
                            supplierSku
                    ));
                }
            }

            if (!listings.isEmpty()) {
                tianggeClient.publishListings(listings);
            }
        } catch (Exception e) {
            System.err.println("!!! Error in publishListings: " + e.getMessage());
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