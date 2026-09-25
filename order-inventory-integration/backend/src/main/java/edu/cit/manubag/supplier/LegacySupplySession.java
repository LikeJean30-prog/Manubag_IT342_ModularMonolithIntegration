package edu.cit.manubag.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static edu.cit.manubag.supplier.LegacySupplyExceptions.AuthRejectedException;

@Component
class LegacySupplySession {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplySession.class);

    private final RestClient restClient;
    private final SupplierProperties properties;

    private volatile String token;
    private volatile Instant issuedAt;

    LegacySupplySession(RestClient supplierRestClient, SupplierProperties properties) {
        this.restClient = supplierRestClient;
        this.properties = properties;
    }

    synchronized String currentToken() {
        if (token == null) {
            login();
        }
        return token;
    }

    synchronized void invalidate() {
        token = null;
    }

    synchronized String renew() {
        login();
        return token;
    }

    private void login() {
        try {
            LegacySupplyXml.AuthRequest body =
                    new LegacySupplyXml.AuthRequest(properties.getClientId(), properties.getApiKey());

            LegacySupplyXml.AuthResponse response = restClient.post()
                    .uri("/auth/token")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(body)
                    .retrieve()
                    .body(LegacySupplyXml.AuthResponse.class);

            if (response == null || response.sessionToken == null) {
                throw new AuthRejectedException("LegacySupply returned an empty AuthResponse");
            }

            this.token = response.sessionToken;
            this.issuedAt = Instant.now();
            log.info("Obtained new LegacySupply session token");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            throw new AuthRejectedException("LegacySupply rejected our credentials: " + ex.getStatusCode());
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            throw new LegacySupplyExceptions.SupplierUnavailableException(
                    "LegacySupply auth timed out/unreachable", ex);
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            throw new LegacySupplyExceptions.SupplierUnavailableException(
                    "LegacySupply auth failed: " + ex.getStatusCode(), ex);
        }
    }

    Instant issuedAt() {
        return issuedAt;
    }
}