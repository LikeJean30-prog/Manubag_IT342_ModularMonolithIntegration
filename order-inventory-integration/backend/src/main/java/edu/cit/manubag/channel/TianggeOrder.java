package edu.cit.manubag.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** One row per Tiangge order: the Tiangge ID is the primary key, so an order can only exist once. */
@Entity
@Table(name = "tiangge_orders")
class TianggeOrder {

    @Id
    @Column(name = "tiangge_order_id", length = 40)
    private String tianggeOrderId;

    @Column(name = "local_order_id")
    private Long localOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TianggeOrderStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TianggeOrder() {
    }

    TianggeOrder(String tianggeOrderId, Long localOrderId, TianggeOrderStatus status) {
        this.tianggeOrderId = tianggeOrderId;
        this.localOrderId = localOrderId;
        this.status = status;
        this.createdAt = OffsetDateTime.now();
    }

    String getTianggeOrderId() { return tianggeOrderId; }
    Long getLocalOrderId() { return localOrderId; }
    TianggeOrderStatus getStatus() { return status; }
    void setStatus(TianggeOrderStatus status) { this.status = status; }
}
