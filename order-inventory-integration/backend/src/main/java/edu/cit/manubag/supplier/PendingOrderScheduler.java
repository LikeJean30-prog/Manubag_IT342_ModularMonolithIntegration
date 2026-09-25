package edu.cit.manubag.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class PendingOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderScheduler.class);

    private final SupplierOrderRepository orderRepository;
    private final SupplierGatewayImpl gateway;
    private final SupplierProperties properties;

    PendingOrderScheduler(SupplierOrderRepository orderRepository, SupplierGatewayImpl gateway,
                          SupplierProperties properties) {
        this.orderRepository = orderRepository;
        this.gateway = gateway;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.supplier.pending-retry-interval-ms:30000}")
    @Transactional
    void retryPendingOrders() {
        List<SupplierOrder> pending = orderRepository.findByStatus(SupplierOrderStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retrying {} pending supplier order(s)", pending.size());

        SupplierProperties.ProductMapping mapping;
        for (SupplierOrder order : pending) {
            mapping = properties.getMapping().get(order.getProductId());
            if (mapping == null) {
                log.error("No SKU mapping for product {} - cannot retry order {}",
                        order.getProductId(), order.getBuyerRef());
                continue;
            }
            gateway.attemptSubmit(order, mapping.getSku(), order.getCases(), order.getBuyerRef(),
                    order.getRequestId());
        }
    }
}
