package edu.cit.manubag.channel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;

@Component
class TianggeClient {

    private static final Logger log = LoggerFactory.getLogger(TianggeClient.class);
    private static final int ATTEMPTS = 3;

    private final RestClient restClient;
    private final Instant startTime = Instant.now();

    TianggeClient(
            @Value("${app.supplier.client-id}") String clientId,
            @Value("${tiangge.api.key}") String apiKey,
            @Value("${tiangge.base-url:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
            String clientInstanceId) {

        // Own JSON mapper: the app's XmlMapper bean stops Spring Boot from creating one.
        ObjectMapper json = new ObjectMapper()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .messageConverters(c -> {
                    c.clear();
                    c.add(new MappingJackson2HttpMessageConverter(json));
                })
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader("X-Client-Instance", clientInstanceId)
                .build();
    }

    boolean sendHeartbeat() {
        long uptime = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        var body = new TianggeDtos.HeartbeatRequest("my-shop", startTime.toString(), uptime);
        return send("heartbeat", () -> restClient.post().uri("/instances/heartbeat")
                .body(body).retrieve().toBodilessEntity());
    }

    boolean publishListings(List<TianggeDtos.ListingItem> listings) {
        return send("listings", () -> restClient.put().uri("/listings")
                .body(listings).retrieve().toBodilessEntity());
    }

    boolean updateStock(List<TianggeDtos.StockItem> stockItems) {
        return send("stock", () -> restClient.put().uri("/stock")
                .body(stockItems).retrieve().toBodilessEntity());
    }

    boolean sendOrderDecision(String orderId, String decision, String shopOrderId) {
        return sendOrderDecision(orderId, decision, shopOrderId, null);
    }

    boolean sendOrderDecision(String orderId, String decision, String shopOrderId, String reason) {
        var body = new TianggeDtos.DecisionRequest(decision, shopOrderId, reason);
        return send("decision " + orderId + " " + decision, () -> restClient.post()
                .uri("/orders/{orderId}/decision", orderId)
                .body(body).retrieve().toBodilessEntity());
    }

    boolean confirmCancellation(String orderId) {
        var body = new TianggeDtos.CancellationConfirmRequest(true);
        return send("cancellation " + orderId, () -> restClient.post()
                .uri("/orders/{orderId}/cancellation", orderId)
                .body(body).retrieve().toBodilessEntity());
    }

    /** Backorder outcome: ACCEPTED or CANCELLED. Check the path and body against the manual's backorders section. */
    boolean resolveBackorder(String orderId, String status) {
        var body = new TianggeDtos.ResolutionRequest(status);
        return send("resolution " + orderId + " " + status, () -> restClient.post()
                .uri("/orders/{orderId}/resolution", orderId)
                .body(body).retrieve().toBodilessEntity());
    }

    /** Returns null if Tiangge could not be reached; the caller should simply try again on the next poll. */
    TianggeDtos.FeedResponse fetchFeed(String afterCursor) {
        try {
            return restClient.get()
                    .uri(u -> u.path("/feed")
                            .queryParam("after", afterCursor != null ? afterCursor : "0")
                            .queryParam("limit", 50)
                            .build())
                    .retrieve()
                    .body(TianggeDtos.FeedResponse.class);
        } catch (RestClientException e) {
            log.warn("Feed fetch failed: {}", describe(e));
            return null;
        }
    }

    /** true = nothing more to send (delivered, or Tiangge definitively refused it); false = try again later. */
    private boolean send(String what, Runnable call) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                call.run();
                return true;
            } catch (HttpClientErrorException refused) {
                log.error("Tiangge refused {}: {}", what, describe(refused));
                return true;
            } catch (RestClientException e) {
                log.warn("{} failed (attempt {}/{}): {}", what, attempt, ATTEMPTS, describe(e));
                sleep(200L << attempt);
            }
        }
        return false;
    }

    private static String describe(RestClientException e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return e.getMessage() + (root != e ? " [cause: " + root + "]" : "");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}