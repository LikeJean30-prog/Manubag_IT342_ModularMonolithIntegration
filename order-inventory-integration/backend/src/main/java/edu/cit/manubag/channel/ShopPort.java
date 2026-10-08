package edu.cit.manubag.channel;

public interface ShopPort {
    boolean publishListings();
    void syncStock(String productId, int availableQuantity);
    void sendHeartbeat();
}

