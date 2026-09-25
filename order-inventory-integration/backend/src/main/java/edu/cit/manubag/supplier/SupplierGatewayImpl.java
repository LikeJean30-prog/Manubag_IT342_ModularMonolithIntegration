package edu.cit.manubag.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static edu.cit.manubag.supplier.LegacySupplyExceptions.AuthRejectedException;
import static edu.cit.manubag.supplier.LegacySupplyExceptions.NonRetryableSupplierException;
import static edu.cit.manubag.supplier.LegacySupplyExceptions.RequestIdConflictException;
import static edu.cit.manubag.supplier.LegacySupplyExceptions.SupplierUnavailableException;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    private static final List<SupplierOrderStatus> OPEN_STATUSES = List.of(
            SupplierOrderStatus.PENDING, SupplierOrderStatus.SUBMITTED,
            SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED);

    private final LegacySupplyClient client;
    private final SupplierOrderRepository orderRepository;
    private final SupplierProperties properties;

    SupplierGatewayImpl(LegacySupplyClient client, SupplierOrderRepository orderRepository,
                        SupplierProperties properties) {
        this.client = client;
        this.orderRepository = orderRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public ReorderResult reorder(String productId, int unitsNeeded) {
        List<SupplierOrder> inFlight = orderRepository.findByProductIdAndStatusIn(productId, OPEN_STATUSES);
        if (!inFlight.isEmpty()) {

            return toResult(inFlight.get(0));
        }

        SupplierProperties.ProductMapping mapping = properties.getMapping().get(productId);
        if (mapping == null) {
            throw new IllegalStateException(
                    "No LegacySupply SKU mapping configured for product " + productId
                            + " - add it under app.supplier.mapping." + productId + ".sku / .pack-size");
        }

        int cases = ceilDiv(unitsNeeded, mapping.getPackSize());

        SupplierOrder order = new SupplierOrder(productId, unitsNeeded, cases);
        order = orderRepository.save(order);
        String buyerRef = "RO-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        String requestId = buyerRef;
        order.assignReferences(buyerRef, requestId);
        orderRepository.save(order);

        attemptSubmit(order, mapping.getSku(), cases, buyerRef, requestId);
        return toResult(order);
    }

    void attemptSubmit(SupplierOrder order, String sku, int cases, String buyerRef, String requestId) {
        long backoff = properties.getInitialBackoffMs();

        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            order.recordAttempt();
            try {
                LegacySupplyXml.PurchaseOrderAck ack = client.placeOrder(requestId, sku, cases, buyerRef);
                order.markSubmitted(ack.poNumber, mapStatus(ack.statusCode));
                orderRepository.save(order);
                log.info("Submitted supplier order {} ({} cases of {}) -> PO {}",
                        buyerRef, cases, sku, ack.poNumber);
                return;
            } catch (RequestIdConflictException conflict) {

                if (client.existsByBuyerRef(buyerRef)) {
                    log.warn("Order {} was already accepted by LegacySupply on a prior attempt", buyerRef);
                    order.markStatus(SupplierOrderStatus.SUBMITTED);
                    orderRepository.save(order);
                    return;
                }
                order.markFailed(conflict.getMessage());
                orderRepository.save(order);
                return;
            } catch (NonRetryableSupplierException permanent) {
                log.warn("LegacySupply rejected order {} permanently: {} {}",
                        buyerRef, permanent.code, permanent.getMessage());
                order.markFailed(permanent.code + ": " + permanent.getMessage());
                orderRepository.save(order);
                return;
            } catch (AuthRejectedException credsBad) {
                log.error("LegacySupply credentials rejected - check LS_API_KEY / client id: {}",
                        credsBad.getMessage());
                order.recordTransientError("auth rejected: " + credsBad.getMessage());
                orderRepository.save(order);
                return;
            } catch (SupplierUnavailableException transient_) {
                order.recordTransientError(transient_.getMessage());
                orderRepository.save(order);
                log.warn("LegacySupply unavailable on attempt {}/{} for {}: {}",
                        attempt, properties.getMaxAttempts(), buyerRef, transient_.getMessage());
                if (attempt < properties.getMaxAttempts()) {
                    sleep(backoff);
                    backoff *= 2;
                }
            }
        }
        log.warn("Exhausted {} attempts for order {}; leaving PENDING for the retry job",
                properties.getMaxAttempts(), buyerRef);
    }

    static SupplierOrderStatus mapStatus(int legacySupplyStatusCode) {
        return switch (legacySupplyStatusCode) {
            case 10 -> SupplierOrderStatus.SUBMITTED;
            case 20 -> SupplierOrderStatus.PICKING;
            case 30 -> SupplierOrderStatus.SHIPPED;
            case 40 -> SupplierOrderStatus.DELIVERED;
            default -> {
                log.warn("Unexpected LegacySupply StatusCode {} - leaving order status unchanged; "
                        + "document this in INTEGRATION.md Part E", legacySupplyStatusCode);
                yield SupplierOrderStatus.SUBMITTED;
            }
        };
    }

    private static int ceilDiv(int units, int packSize) {
        if (packSize <= 0) {
            throw new IllegalArgumentException("packSize must be positive");
        }
        return (units + packSize - 1) / packSize;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ReorderResult toResult(SupplierOrder order) {
        return new ReorderResult(order.getId(), order.getProductId(), order.getBuyerRef(),
                order.getStatus(), order.getPoNumber());
    }
}