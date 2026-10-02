package edu.cit.manubag.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    default void markProcessed(String eventId) {
        save(new ProcessedEvent(eventId));
    }
}