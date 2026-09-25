package edu.cit.manubag.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "units", nullable = false)
    private int units;

    @Column(name = "cases")
    private Integer cases;

    @Column(name = "buyer_ref", nullable = false, unique = true, length = 40)
    private String buyerRef;

    @Column(name = "request_id", nullable = false, unique = true, length = 80)
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupplierOrderStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    protected SupplierOrder() {

    }

    SupplierOrder(String productId, int units, int cases) {
        this.productId = productId;
        this.units = units;
        this.cases = cases;
        this.status = SupplierOrderStatus.PENDING;
        this.attempts = 0;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    Long getId() {
        return id;
    }

    String getProductId() {
        return productId;
    }

    int getUnits() {
        return units;
    }

    Integer getCases() {
        return cases;
    }

    String getBuyerRef() {
        return buyerRef;
    }

    void assignReferences(String buyerRef, String requestId) {
        this.buyerRef = buyerRef;
        this.requestId = requestId;
        touch();
    }

    String getRequestId() {
        return requestId;
    }

    String getPoNumber() {
        return poNumber;
    }

    SupplierOrderStatus getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }

    String getLastError() {
        return lastError;
    }

    OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    void recordAttempt() {
        this.attempts++;
        touch();
    }

    void markSubmitted(String poNumber, SupplierOrderStatus mappedStatus) {
        this.poNumber = poNumber;
        this.status = mappedStatus;
        this.lastError = null;
        touch();
    }

    void markStatus(SupplierOrderStatus status) {
        this.status = status;
        touch();
    }

    void markFailed(String reason) {
        this.status = SupplierOrderStatus.FAILED;
        this.lastError = reason;
        touch();
    }

    void recordTransientError(String reason) {
        this.lastError = reason;
        touch();
    }

    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
