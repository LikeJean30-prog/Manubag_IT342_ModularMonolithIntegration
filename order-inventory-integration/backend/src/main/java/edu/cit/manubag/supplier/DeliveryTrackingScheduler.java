package edu.cit.manubag.supplier;

import edu.cit.manubag.event.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Component
class DeliveryTrackingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTrackingScheduler.class);

    private static final List<SupplierOrderStatus> OPEN_STATUSES = List.of(
            SupplierOrderStatus.SUBMITTED, SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED);

    private final SupplierOrderRepository orderRepository;
    private final LegacySupplyClient client;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate tx;

    DeliveryTrackingScheduler(SupplierOrderRepository orderRepository, LegacySupplyClient client,
                              ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.client = client;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // Checked often, and each order is saved on its own: a delivery is restocked (and its stock sent to
    // Tiangge) as soon as it is seen, not after every other open order has been checked too.
    @Scheduled(fixedDelayString = "${app.supplier.tracking-interval-ms:6000}")
    void pollOpenOrders() {
        List<SupplierOrder> open = orderRepository.findByStatusIn(OPEN_STATUSES);
        if (open.isEmpty()) {
            return;
        }
        log.info("Polling {} open supplier order(s)", open.size());

        for (SupplierOrder snapshot : open) {
            if (snapshot.getPoNumber() == null) {
                continue;
            }
            try {
                LegacySupplyXml.PurchaseOrderStatus status = client.getStatus(snapshot.getPoNumber());
                SupplierOrderStatus mapped = SupplierGatewayImpl.mapStatus(status.statusCode);

                if (mapped == snapshot.getStatus()) {
                    continue;
                }

                tx.executeWithoutResult(s -> {
                    SupplierOrder order = orderRepository.findById(snapshot.getId()).orElseThrow();
                    if (order.getStatus() == mapped) {
                        return;
                    }
                    order.markStatus(mapped);
                    orderRepository.save(order);

                    if (mapped == SupplierOrderStatus.DELIVERED) {
                        log.info("Order {} delivered - restocking {} unit(s) of {}",
                                order.getBuyerRef(), order.getUnits(), order.getProductId());
                        eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                                order.getProductId(), order.getUnits(), order.getId()));
                    }
                });
            } catch (LegacySupplyExceptions.SupplierUnavailableException transient_) {
                log.warn("Could not check status of order {}: {}", snapshot.getBuyerRef(), transient_.getMessage());
            } catch (LegacySupplyExceptions.NonRetryableSupplierException permanent) {
                log.error("LegacySupply rejected status check for order {}: {} {}",
                        snapshot.getBuyerRef(), permanent.code, permanent.getMessage());
            } catch (RuntimeException unexpected) {
                log.error("Checking supplier order {} failed: {}", snapshot.getBuyerRef(), unexpected.toString());
            }
        }
    }
}