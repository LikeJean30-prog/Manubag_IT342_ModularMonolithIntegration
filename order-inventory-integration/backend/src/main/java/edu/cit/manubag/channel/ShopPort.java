package edu.cit.manubag.channel;

public interface ShopPort {
    void publishListings();
    void syncStock(String productId, int availableQuantity);
    void sendHeartbeat();
}