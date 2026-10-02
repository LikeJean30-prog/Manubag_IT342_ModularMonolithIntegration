package edu.cit.manubag.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
interface FeedCursorRepository extends JpaRepository<FeedCursor, String> {

    default Optional<String> findLastCursor() {
        return findById("TIANGGE_CURSOR").map(FeedCursor::getLastCursor);
    }

    default void saveLastCursor(String cursor) {
        FeedCursor fc = findById("TIANGGE_CURSOR").orElse(new FeedCursor());
        fc.setLastCursor(cursor);
        save(fc);
    }
}