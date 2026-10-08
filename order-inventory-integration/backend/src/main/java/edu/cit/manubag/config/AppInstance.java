package edu.cit.manubag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Identifies this running copy of the app. A new UUID is generated on every start and sent
 * as X-Client-Instance on every call to Tiangge and LegacySupply (Task 1).
 */
@Component
public class AppInstance {

    private static final Logger log = LoggerFactory.getLogger(AppInstance.class);

    private final String id = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();

    public AppInstance() {
        log.info("Application instance ID: {}", id);
    }

    public String id() {
        return id;
    }

    public Instant startedAt() {
        return startedAt;
    }
}
