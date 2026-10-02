package edu.cit.manubag.channel;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class FeedPoller {

    private final TianggeClient tianggeClient;
    private final FeedProcessor feedProcessor;
    private final FeedCursorRepository cursorRepository;

    FeedPoller(TianggeClient tianggeClient, FeedProcessor feedProcessor, FeedCursorRepository cursorRepository) {
        this.tianggeClient = tianggeClient;
        this.feedProcessor = feedProcessor;
        this.cursorRepository = cursorRepository;
    }

    @Scheduled(fixedDelay = 3000) // Task 4: Polls feed every 3 seconds
    public void pollFeed() {
        try {
            String lastCursor = cursorRepository.findLastCursor().orElse(null);
            TianggeDtos.FeedResponse response = tianggeClient.fetchFeed(lastCursor);

            if (response != null && response.events() != null) {
                for (TianggeDtos.FeedEvent event : response.events()) {
                    feedProcessor.processEvent(event);
                    cursorRepository.saveLastCursor(event.eventId()); // Persist cursor for Stage 4 restart resilience
                }
            }
        } catch (Exception e) {
            // Tolerates transient network issues
        }
    }
}