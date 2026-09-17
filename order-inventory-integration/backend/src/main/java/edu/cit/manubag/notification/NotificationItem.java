
package edu.cit.manubag.notification;

import java.time.OffsetDateTime;

public record NotificationItem(Long notificationId, String message, OffsetDateTime createdAt) {}