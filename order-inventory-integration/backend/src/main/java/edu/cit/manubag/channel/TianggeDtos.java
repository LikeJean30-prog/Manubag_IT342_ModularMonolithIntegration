package edu.cit.manubag.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

final class TianggeDtos {
    private TianggeDtos() {}

    record HeartbeatRequest(String appName, String startedAt, long uptimeSeconds) {}
    record ListingItem(String sellerSku, String title, String supplierSku) {}
    record StockItem(String sellerSku, int available) {}
    record DecisionRequest(String decision, String shopOrderId, String reason) {}
    record CancellationConfirmRequest(boolean restocked) {}
    record ResolutionRequest(String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedResponse(List<FeedEvent> events, Long nextCursor) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedEvent(long seq, String eventId, String type, String orderId,
                     String placedAt, String decisionDeadline,
                     String cancelledAt, String confirmDeadline,
                     List<FeedItem> lines) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedItem(String sellerSku, int qty) {}
}