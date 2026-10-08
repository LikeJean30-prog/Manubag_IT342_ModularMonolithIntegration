package edu.cit.manubag.channel;

import edu.cit.manubag.event.SupplierOrderDeliveredEvent;
import edu.cit.manubag.shop.OrderLineItem;
import edu.cit.manubag.shop.OrderService;
import edu.cit.manubag.shop.OrderStatus;
import edu.cit.manubag.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Task 6: when a supplier delivery has been restocked, backordered Tiangge orders are filled (oldest first)
 * or cancelled, and the result is queued for Tiangge. A backorder is only judged once none of its products
 * has a purchase order still on its way.
 */
@Component
class BackorderResolver {

    private static final Logger log = LoggerFactory.getLogger(BackorderResolver.class);

    private final TianggeOrderRepository tianggeOrders;
    private final OutboxRepository outbox;
    private final OrderService orderService;
    private final SupplierGateway supplierGateway;
    private final StockSync stockSync;
    private final TransactionTemplate tx;
    private final ReentrantLock lock = new ReentrantLock();

    BackorderResolver(TianggeOrderRepository tianggeOrders, OutboxRepository outbox, OrderService orderService,
                      SupplierGateway supplierGateway, StockSync stockSync,
                      PlatformTransactionManager transactionManager) {
        this.tianggeOrders = tianggeOrders;
        this.outbox = outbox;
        this.orderService = orderService;
        this.supplierGateway = supplierGateway;
        this.stockSync = stockSync;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Immediately after the delivery (and its restock) has been committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onDelivery(SupplierOrderDeliveredEvent event) {
        stockSync.publishNow(event.productId()); // Tiangge learns about the new units before we resolve backorders
        resolveReady();
    }

    /** Safety net: also catches failed purchase orders and anything a crash interrupted. */
    @Scheduled(initialDelay = 10000, fixedDelay = 5000)
    void sweep() {
        resolveReady();
    }

    void resolveReady() {
        if (!lock.tryLock()) {
            return; // another thread is already resolving
        }
        try {
            List<TianggeOrder> waiting = tianggeOrders.findByStatusOrderByCreatedAtAsc(TianggeOrderStatus.BACKORDERED);
            for (TianggeOrder backorder : waiting) {
                try {
                    resolveOne(backorder);
                } catch (Exception e) {
                    log.warn("Could not resolve backorder {}: {}", backorder.getTianggeOrderId(), e.getMessage());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void resolveOne(TianggeOrder backorder) {
        Long localId = backorder.getLocalOrderId();
        for (OrderLineItem line : orderService.getOrderLines(localId)) {
            if (supplierGateway.hasOpenReorder(line.productId())) {
                return; // still waiting for a delivery of this product
            }
        }

        tx.executeWithoutResult(s -> {
            OrderStatus result = orderService.resolveBackorder(localId); // reserves all lines, or cancels
            boolean accepted = result == OrderStatus.CONFIRMED;
            TianggeOrder fresh = tianggeOrders.findById(backorder.getTianggeOrderId()).orElseThrow();
            fresh.setStatus(accepted ? TianggeOrderStatus.BACKORDER_ACCEPTED : TianggeOrderStatus.BACKORDER_CANCELLED);
            tianggeOrders.save(fresh);
            outbox.save(OutboxMessage.resolution(fresh.getTianggeOrderId(), accepted ? "ACCEPTED" : "CANCELLED"));
            log.info("Backorder {} (local order {}) resolved after supplier delivery -> {}",
                    fresh.getTianggeOrderId(), localId, accepted ? "ACCEPTED" : "CANCELLED");
        });
    }
}