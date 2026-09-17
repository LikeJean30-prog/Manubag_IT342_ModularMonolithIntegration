
package edu.cit.manubag.notification;

import edu.cit.manubag.event.LowStockEvent;
import edu.cit.manubag.event.OrderPlacedEvent;
import edu.cit.manubag.event.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    @Transactional
    public void onOrderPlaced(OrderPlacedEvent event) {
        notificationRepository.save(new Notification("Order O%d confirmed".formatted(event.orderId())));
    }

    @EventListener
    @Transactional
    public void onOrderRejected(OrderRejectedEvent event) {
        notificationRepository.save(new Notification(
                "Order O%d rejected: %s".formatted(event.orderId(), event.reason())));
    }

    @EventListener
    @Transactional
    public void onLowStock(LowStockEvent event) {
        notificationRepository.save(new Notification(
                "Reorder needed: %s (%s) has %d unit(s) left".formatted(
                        event.productId(), event.productName(), event.remainingStock())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationItem> getRecentNotifications() {
        return notificationRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(n -> new NotificationItem(n.getNotificationId(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}