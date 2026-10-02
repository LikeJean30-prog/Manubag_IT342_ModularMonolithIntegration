package edu.cit.manubag.channel;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ChannelBootstrap {

    private final TianggeClient tianggeClient;
    private final ShopPort shopPort;

    ChannelBootstrap(TianggeClient tianggeClient, ShopPort shopPort) {
        this.tianggeClient = tianggeClient;
        this.shopPort = shopPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        // Task 1: Startup identification
        tianggeClient.sendHeartbeat();
        // Task 2: Publish listings on startup
        shopPort.publishListings();
    }

    @Scheduled(fixedRate = 30000) // Task 1: Heartbeat every 30 seconds
    public void heartbeatTimer() {
        tianggeClient.sendHeartbeat();
    }
}