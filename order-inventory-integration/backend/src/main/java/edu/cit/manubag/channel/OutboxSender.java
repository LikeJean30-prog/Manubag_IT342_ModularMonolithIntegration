package edu.cit.manubag.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Component
class OutboxSender {

    private static final Logger log = LoggerFactory.getLogger(OutboxSender.class);

    private final OutboxRepository outbox;
    private final TianggeClient tianggeClient;
    private final StockSync stockSync;

    OutboxSender(OutboxRepository outbox, TianggeClient tianggeClient, StockSync stockSync) {
        this.outbox = outbox;
        this.tianggeClient = tianggeClient;
        this.stockSync = stockSync;
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 1000)

    void drain() {
        if (!stockSync.isInitialised()) {
            return; // Tiangge must know our current stock before it hears any decision
        }

        List<OutboxMessage> pending = outbox.findTop50BySentAtIsNullOrderByIdAsc();
        for (OutboxMessage message : pending) {
            boolean delivered;
            try {
                delivered = deliver(message);
            } catch (Exception e) {
                log.warn("Sending {} for {} failed: {}", message.getKind(), message.getTianggeOrderId(), e.getMessage());
                delivered = false;
            }
            if (delivered) {
                message.markSent();
                outbox.save(message);
                long waitedMs = Duration.between(message.getCreatedAt(), OffsetDateTime.now()).toMillis();
                log.info("Sent {} {} for Tiangge order {} ({} ms after it was decided)",
                        message.getKind(), message.getOutcome() == null ? "" : message.getOutcome(),
                        message.getTianggeOrderId(), waitedMs);
                if (changesStock(message)) {
                    stockSync.markAllDirty(); // a stock update must follow this decision
                }
            } else {
                message.recordFailedAttempt();
                outbox.save(message);
                return; // Tiangge is slow or down: keep the order of messages and try again in a second
            }
        }

        if (!outbox.existsBySentAtIsNull()) {
            stockSync.flush(); // only when every decision has been delivered
        }
    }

    private static boolean changesStock(OutboxMessage m) {
        return switch (m.getKind()) {
            case DECISION, RESOLUTION -> "ACCEPTED".equals(m.getOutcome());
            case CANCELLATION -> true;
        };
    }

    private boolean deliver(OutboxMessage m) {
        return switch (m.getKind()) {
            case DECISION -> tianggeClient.sendOrderDecision(
                    m.getTianggeOrderId(), m.getOutcome(), m.getShopOrderId(), m.getReason());
            case CANCELLATION -> tianggeClient.confirmCancellation(m.getTianggeOrderId());
            case RESOLUTION -> tianggeClient.resolveBackorder(m.getTianggeOrderId(), m.getOutcome());
        };
    }
}