package edu.cit.manubag.event;

public record OrderRejectedEvent(Long orderId, String reason) {
}