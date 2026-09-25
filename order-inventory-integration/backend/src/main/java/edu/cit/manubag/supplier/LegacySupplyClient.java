package edu.cit.manubag.supplier;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static edu.cit.manubag.supplier.LegacySupplyExceptions.AuthRejectedException;
import static edu.cit.manubag.supplier.LegacySupplyExceptions.NonRetryableSupplierException;
import static edu.cit.manubag.supplier.LegacySupplyExceptions.RequestIdConflictException;
import static edu.cit.manubag.supplier.LegacySupplyExceptions.SessionInvalidException;
import static edu.cit.manubag.supplier.LegacySupplyExceptions.SupplierUnavailableException;

@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);

    private final RestClient restClient;
    private final LegacySupplySession session;
    private final XmlMapper xmlMapper;

    LegacySupplyClient(RestClient supplierRestClient, LegacySupplySession session, XmlMapper legacySupplyXmlMapper) {
        this.restClient = supplierRestClient;
        this.session = session;
        this.xmlMapper = legacySupplyXmlMapper;
    }

    LegacySupplyXml.PurchaseOrderAck placeOrder(String requestId, String supplierSku, int qty, String buyerRef) {
        LegacySupplyXml.PurchaseOrderRequest body =
                new LegacySupplyXml.PurchaseOrderRequest(supplierSku, qty, buyerRef);
        return withSession(token -> restClient.post()
                .uri("/purchase-orders")
                .header("X-LS-Session", token)
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_XML)
                .body(body)
                .retrieve()
                .body(LegacySupplyXml.PurchaseOrderAck.class));
    }

    LegacySupplyXml.PurchaseOrderStatus getStatus(String poNumber) {
        return withSession(token -> restClient.get()
                .uri("/purchase-orders/{po}", poNumber)
                .header("X-LS-Session", token)
                .retrieve()
                .body(LegacySupplyXml.PurchaseOrderStatus.class));
    }

    boolean existsByBuyerRef(String buyerRef) {
        LegacySupplyXml.PurchaseOrderList list = withSession(token -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/purchase-orders").queryParam("buyerRef", buyerRef).build())
                .header("X-LS-Session", token)
                .retrieve()
                .body(LegacySupplyXml.PurchaseOrderList.class));
        return list != null && list.count > 0;
    }

    private <T> T withSession(java.util.function.Function<String, T> call) {
        String token = session.currentToken();
        try {
            return callGuarded(call, token);
        } catch (SessionInvalidException retryWithFreshSession) {
            log.info("Session rejected, renewing and retrying once");
            String fresh = session.renew();
            return callGuarded(call, fresh);
        }
    }

    private <T> T callGuarded(java.util.function.Function<String, T> call, String token) {
        try {
            return call.apply(token);
        } catch (RestClientResponseException httpError) {
            throw translate(httpError);
        } catch (ResourceAccessException timeoutOrConnect) {
            throw new SupplierUnavailableException("LegacySupply unreachable/timed out", timeoutOrConnect);
        }
    }



    private RuntimeException translate(RestClientResponseException ex) {
        String code = null;
        String message = ex.getMessage();
        try {
            LegacySupplyXml.LSError error =
                    xmlMapper.readValue(ex.getResponseBodyAsString(), LegacySupplyXml.LSError.class);
            code = error.code;
            message = error.message;
        } catch (Exception parseFailure) {

        }

        HttpStatusCode status = ex.getStatusCode();
        if (code == null) {
            code = "HTTP-" + status.value();
        }

        return switch (code) {
            case "E-AUTH-02", "E-AUTH-03", "E-AUTH-07" -> {
                session.invalidate();
                yield new SessionInvalidException(message);
            }
            case "E-AUTH-01" -> new AuthRejectedException(message);
            case "E-IDEM-04" -> new RequestIdConflictException(message);
            case "E-RATE-03", "E-SYS-50", "E-SYS-99" -> new SupplierUnavailableException(code + ": " + message);
            default -> {
                if (status.is5xxServerError()) {
                    yield new SupplierUnavailableException(code + ": " + message);
                }
                yield new NonRetryableSupplierException(code, message);
            }
        };
    }

}
