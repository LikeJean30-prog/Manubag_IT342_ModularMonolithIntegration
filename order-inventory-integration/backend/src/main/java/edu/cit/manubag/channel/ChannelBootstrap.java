package edu.cit.manubag.channel;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ChannelBootstrap {

    private final TianggeClient tianggeClient;
    private final ShopPort shopPort;
    private volatile boolean listed = false;

    ChannelBootstrap(TianggeClient tianggeClient, ShopPort shopPort) {
        this.tianggeClient = tianggeClient;
        this.shopPort = shopPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        tianggeClient.sendHeartbeat();   // Task 1: first call to Tiangge is the heartbeat
        listed = shopPort.publishListings(); // Task 2: listings, then stock
    }

    @Scheduled(initialDelay = 30000, fixedRate = 30000) // Task 1: heartbeat every 30 seconds
    public void heartbeatTimer() {
        tianggeClient.sendHeartbeat();
    }

    /** If Tiangge was down at startup, keep trying until the shop is published. */
    @Scheduled(initialDelay = 10000, fixedDelay = 10000)
    public void retryListings() {
        if (!listed) {
            listed = shopPort.publishListings();
        }
    }
}
