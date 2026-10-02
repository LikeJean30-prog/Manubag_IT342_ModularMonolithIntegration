package edu.cit.manubag.channel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
class FeedCursor {
    @Id
    private String id = "TIANGGE_CURSOR";
    private String lastCursor;

    public String getLastCursor() {
        return lastCursor;
    }

    public void setLastCursor(String lastCursor) {
        this.lastCursor = lastCursor;
    }
}