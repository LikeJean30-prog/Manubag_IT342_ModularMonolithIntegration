package edu.cit.manubag.supplier;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "app.supplier")
class SupplierProperties {

    private String baseUrl;

    private String clientId;

    private String apiKey;

    private int timeoutMs = 3000;
    private int maxAttempts = 3;
    private long initialBackoffMs = 400;

    private long pendingRetryIntervalMs = 30_000;

    private long trackingIntervalMs = 20_000;

    private int reorderTargetLevel = 20;

    private Map<String, ProductMapping> mapping = Map.of();

    String getBaseUrl() {
        return baseUrl;
    }

    void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    String getClientId() {
        return clientId;
    }

    void setClientId(String clientId) {
        this.clientId = clientId;
    }

    String getApiKey() {
        return apiKey;
    }

    void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    int getTimeoutMs() {
        return timeoutMs;
    }

    void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    int getMaxAttempts() {
        return maxAttempts;
    }

    void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    void setInitialBackoffMs(long initialBackoffMs) {
        this.initialBackoffMs = initialBackoffMs;
    }

    long getPendingRetryIntervalMs() {
        return pendingRetryIntervalMs;
    }

    void setPendingRetryIntervalMs(long pendingRetryIntervalMs) {
        this.pendingRetryIntervalMs = pendingRetryIntervalMs;
    }

    long getTrackingIntervalMs() {
        return trackingIntervalMs;
    }

    void setTrackingIntervalMs(long trackingIntervalMs) {
        this.trackingIntervalMs = trackingIntervalMs;
    }

    int getReorderTargetLevel() {
        return reorderTargetLevel;
    }

    void setReorderTargetLevel(int reorderTargetLevel) {
        this.reorderTargetLevel = reorderTargetLevel;
    }

    Map<String, ProductMapping> getMapping() {
        return mapping;
    }

    void setMapping(Map<String, ProductMapping> mapping) {
        this.mapping = mapping;
    }

    static class ProductMapping {
        private String sku;
        private int packSize;

        String getSku() {
            return sku;
        }

        void setSku(String sku) {
            this.sku = sku;
        }

        int getPackSize() {
            return packSize;
        }

        void setPackSize(int packSize) {
            this.packSize = packSize;
        }
    }
}
