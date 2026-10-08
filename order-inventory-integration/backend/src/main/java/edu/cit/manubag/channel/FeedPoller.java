package edu.cit.manubag.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class FeedPoller {

    private static final Logger log = LoggerFactory.getLogger(FeedPoller.class);
    private static final int PAGE_SIZE = 50;   // must match the limit TianggeClient asks for
    private static final int MAX_PAGES_PER_POLL = 10;

    private final TianggeClient tianggeClient;
    private final FeedProcessor feedProcessor;
    private final FeedCursorRepository cursorRepository;

    FeedPoller(TianggeClient tianggeClient, FeedProcessor feedProcessor, FeedCursorRepository cursorRepository) {
        this.tianggeClient = tianggeClient;
        this.feedProcessor = feedProcessor;
        this.cursorRepository = cursorRepository;
    }

    // Task 4: poll every few seconds. The initial delay lets ChannelBootstrap send the first heartbeat first.
    @Scheduled(initialDelay = 5000, fixedDelay = 3000)
    public void pollFeed() {
        for (int page = 0; page < MAX_PAGES_PER_POLL; page++) {
            if (!pollOnePage()) {
                return;
            }
        }
    }

    /** @return true if a full page was read and processed, so there may be more waiting (flash sale). */
    private boolean pollOnePage() {
        long after = readCursor();
        TianggeDtos.FeedResponse response = tianggeClient.fetchFeed(after);
        if (response == null || response.events() == null) {
            return false; // Tiangge unreachable: try again on the next poll
        }

        long done = after;
        boolean allProcessed = true;
        for (TianggeDtos.FeedEvent event : response.events()) {
            try {
                feedProcessor.processEvent(event);
                done = Math.max(done, event.seq());
            } catch (Exception e) {
                log.error("Failed to process feed event {} (seq {}); will retry on the next poll",
                        event.eventId(), event.seq(), e);
                allProcessed = false;
                break; // keep the cursor before this event so it is read again
            }
        }

        if (allProcessed && response.nextCursor() != null) {
            done = Math.max(done, response.nextCursor());
        }
        if (done > after) {
            cursorRepository.saveLastCursor(String.valueOf(done)); // survives restarts (Stage 4)
        }
        return allProcessed && response.events().size() >= PAGE_SIZE;
    }

    /** The cursor is the numeric seq / nextCursor from the feed. Anything else counts as "start from 0". */
    private long readCursor() {
        String stored = cursorRepository.findLastCursor().orElse(null);
        if (stored == null) {
            return 0L;
        }
        try {
            return Long.parseLong(stored.trim());
        } catch (NumberFormatException e) {
            log.warn("Stored feed cursor '{}' is not a number; reading from 0 (processed events are skipped)", stored);
            return 0L;
        }
    }
}