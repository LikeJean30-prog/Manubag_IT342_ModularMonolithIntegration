package edu.cit.manubag.supplier;

public record ReorderResult(
        Long supplierOrderId,
        String productId,
        String buyerRef,
        SupplierOrderStatus status,
        String poNumber
) {
}
