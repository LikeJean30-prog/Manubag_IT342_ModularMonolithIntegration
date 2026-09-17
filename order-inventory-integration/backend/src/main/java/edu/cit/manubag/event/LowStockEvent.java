package edu.cit.manubag.event;

public record LowStockEvent(String productId, String productName, int remainingStock) {
}