package edu.cit.manubag.supplier;

import edu.cit.manubag.event.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Component
class DeliveryTrackingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTrackingScheduler.class);

    private static final List<SupplierOrderStatus> OPEN_STATUSES = List.of(
            SupplierOrderStatus.SUBMITTED, SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED);

    private final SupplierOrderRepository orderRepository;
    private final LegacySupplyClient client;
    private final ApplicationEventPublisher eventPublisher;

    DeliveryTrackingScheduler(SupplierOrderRepository orderRepository, LegacySupplyClient client,
                              ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.client = client;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.supplier.tracking-interval-ms:20000}")
    @Transactional
    void pollOpenOrders() {
        List<SupplierOrder> open = orderRepository.findByStatusIn(OPEN_STATUSES);
        if (open.isEmpty()) {
            return;
        }
        log.info("Polling {} open supplier order(s)", open.size());

        for (SupplierOrder order : open) {
            try {
                LegacySupplyXml.PurchaseOrderStatus status = client.getStatus(order.getPoNumber());
                SupplierOrderStatus mapped = SupplierGatewayImpl.mapStatus(status.statusCode);

                if (mapped == order.getStatus()) {
                    continue;
                }

                order.markStatus(mapped);
                orderRepository.save(order);

                if (mapped == SupplierOrderStatus.DELIVERED) {
                    log.info("Order {} delivered - restocking {} unit(s) of {}",
                            order.getBuyerRef(), order.getUnits(), order.getProductId());
                    eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                            order.getProductId(), order.getUnits(), order.getId()));
                }
            } catch (LegacySupplyExceptions.SupplierUnavailableException transient_) {
                log.warn("Could not check status of order {}: {}", order.getBuyerRef(), transient_.getMessage());

            } catch (LegacySupplyExceptions.NonRetryableSupplierException permanent) {
                log.error("LegacySupply rejected status check for order {}: {} {}",
                        order.getBuyerRef(), permanent.code, permanent.getMessage());
            }
        }
    }
}
