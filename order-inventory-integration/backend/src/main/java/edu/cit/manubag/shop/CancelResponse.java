
package edu.cit.manubag.shop;

public record CancelResponse(Long orderId, String status, String message) {}