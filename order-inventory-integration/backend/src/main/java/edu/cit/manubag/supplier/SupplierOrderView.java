package edu.cit.manubag.supplier;

import java.time.OffsetDateTime;

public record SupplierOrderView(
        Long id,
        String productId,
        int units,
        Integer cases,
        String buyerRef,
        String poNumber,
        SupplierOrderStatus status,
        int attempts,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static SupplierOrderView from(SupplierOrder order) {
        return new SupplierOrderView(order.getId(), order.getProductId(), order.getUnits(), order.getCases(),
                order.getBuyerRef(), order.getPoNumber(), order.getStatus(), order.getAttempts(),
                order.getLastError(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
