package com.nanacocoa.server.notification.service;

import com.nanacocoa.server.notification.entity.OrderNotification.Status;
import com.nanacocoa.server.notification.entity.OrderNotification.Type;
import com.nanacocoa.server.notification.repository.OrderNotificationRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.enabled", havingValue = "true")
public class NotificationWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
    private final OrderNotificationRepository notifications;
    private final NotificationPublisher publisher;
    private final LocalDateTime cutoff;

    public NotificationWorker(OrderNotificationRepository notifications, NotificationPublisher publisher,
            @Value("${notifications.starts-at:}") String startsAt) {
        this.notifications = notifications;
        this.publisher = publisher;
        this.cutoff = LocalDateTime.ofInstant(Instant.parse(startsAt), ZoneOffset.UTC);
    }

    @Scheduled(fixedDelayString = "${notifications.fixed-delay:30000}", initialDelayString = "${notifications.initial-delay:30000}")
    public void run() {
        try {
            for (var event : notifications.undiscovered(cutoff)) {
                try {
                    publisher.discover(event);
                } catch (org.springframework.dao.DataIntegrityViolationException failure) {
                    if (!notifications.existsByOrderIdAndEventType(event.getOrderId(), Type.valueOf(event.getEventType()))) {
                        log.warn("Order notification discovery deferred");
                    }
                } catch (RuntimeException failure) {
                    log.warn("Order notification discovery deferred");
                }
            }
            for (var row : notifications.findByStatusAndConfirmedAtGreaterThanEqualOrderByIdAsc(Status.PENDING, cutoff, PageRequest.of(0, 100))) {
                Long id = row.getId();
                try {
                    publisher.publish(id);
                } catch (RuntimeException failure) {
                    try {
                        publisher.recordFailure(id);
                    } catch (RuntimeException unavailable) {
                        log.warn("Order notification failure record deferred; notification id={}", id);
                    }
                }
            }
        } catch (RuntimeException unavailable) {
            log.warn("Order notification scan deferred");
        }
    }
}
