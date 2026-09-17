
package edu.cit.manubag.shop;

public class OrderCancellationException extends RuntimeException {
    public OrderCancellationException(Long orderId, OrderStatus currentStatus) {
        super("Order " + orderId + " cannot be cancelled from status " + currentStatus);
    }
}