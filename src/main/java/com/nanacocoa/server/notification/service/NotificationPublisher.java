package com.nanacocoa.server.notification.service;

import com.nanacocoa.server.notification.entity.OrderNotification;
import com.nanacocoa.server.notification.entity.OrderNotification.Status;
import com.nanacocoa.server.notification.entity.OrderNotification.Type;
import com.nanacocoa.server.notification.repository.OrderNotificationRepository;
import com.nanacocoa.server.notification.repository.OrderNotificationRepository.Confirmation;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPublisher {
    private final OrderNotificationRepository notifications;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discover(Confirmation event) {
        Type type = Type.valueOf(event.getEventType());
        if (!notifications.existsByOrderIdAndEventType(event.getOrderId(), type)) {
            notifications.saveAndFlush(new OrderNotification(event.getOrderId(), event.getMemberId(),
                    event.getOrderNumber(), type, event.getConfirmedAt()));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Long id) {
        notifications.lockById(id).filter(row -> row.getStatus() == Status.PENDING)
                .ifPresent(row -> {
                    row.publish();
                    notifications.flush();
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long id) {
        notifications.lockById(id).filter(row -> row.getStatus() == Status.PENDING)
                .ifPresent(row -> row.fail(LocalDateTime.now(ZoneOffset.UTC)));
    }
}
