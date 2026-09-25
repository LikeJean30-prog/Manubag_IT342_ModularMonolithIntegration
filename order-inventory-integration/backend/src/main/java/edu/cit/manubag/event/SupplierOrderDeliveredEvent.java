package edu.cit.manubag.event;


public record SupplierOrderDeliveredEvent(String productId, int units, Long supplierOrderId) {
}