package edu.cit.manubag.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Something we owe Tiangge (a decision, a cancellation confirmation, a backorder resolution).
 * It is saved in the same transaction as the change that caused it, and sent until Tiangge has it.
 */
@Entity
@Table(name = "tiangge_outbox")
class OutboxMessage {

    enum Kind { DECISION, CANCELLATION, RESOLUTION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tiangge_order_id", nullable = false, length = 40)
    private String tianggeOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private Kind kind;

    /** ACCEPTED / REJECTED / BACKORDERED for a decision, ACCEPTED / CANCELLED for a resolution. */
    @Column(name = "outcome", length = 20)
    private String outcome;

    @Column(name = "shop_order_id", length = 40)
    private String shopOrderId;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    protected OutboxMessage() {
    }

    private OutboxMessage(String tianggeOrderId, Kind kind, String outcome, String shopOrderId, String reason) {
        this.tianggeOrderId = tianggeOrderId;
        this.kind = kind;
        this.outcome = outcome;
        this.shopOrderId = shopOrderId;
        this.reason = reason == null ? null : (reason.length() > 200 ? reason.substring(0, 200) : reason);
        this.createdAt = OffsetDateTime.now();
    }

    static OutboxMessage decision(String tianggeOrderId, String decision, String shopOrderId, String reason) {
        return new OutboxMessage(tianggeOrderId, Kind.DECISION, decision, shopOrderId, reason);
    }

    static OutboxMessage cancellation(String tianggeOrderId) {
        return new OutboxMessage(tianggeOrderId, Kind.CANCELLATION, null, null, null);
    }

    static OutboxMessage resolution(String tianggeOrderId, String status) {
        return new OutboxMessage(tianggeOrderId, Kind.RESOLUTION, status, null, null);
    }

    Long getId() { return id; }
    String getTianggeOrderId() { return tianggeOrderId; }
    Kind getKind() { return kind; }
    String getOutcome() { return outcome; }
    String getShopOrderId() { return shopOrderId; }
    String getReason() { return reason; }
    OffsetDateTime getCreatedAt() { return createdAt; }
    void markSent() { this.sentAt = OffsetDateTime.now(); }
    void recordFailedAttempt() { this.attempts++; }
}
