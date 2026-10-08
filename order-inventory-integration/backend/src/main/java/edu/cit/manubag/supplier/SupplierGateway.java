package edu.cit.manubag.supplier;

public interface SupplierGateway {

    ReorderResult reorder(String productId, int unitsNeeded);

    boolean hasOpenReorder(String productId);
}
