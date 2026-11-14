package com.nanacocoa.server.order.service;

import com.nanacocoa.server.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order.inventory.expiration-enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpirationScheduler {
	private final OrderRepository orderRepository;
	private final OrderExpirationService expirationService;

	@Scheduled(fixedDelayString = "${order.inventory.expiration-delay:60000}")
	public void expireUnpaidOrders() {
		for (String id : orderRepository.findExpiredOrderIds(LocalDateTime.now(), PageRequest.of(0, 100))) {
			try { expirationService.expire(id); }
			catch (RuntimeException exception) {
				LoggerFactory.getLogger(getClass()).warn("Inventory expiration deferred. orderId={}", id);
			}
		}
	}
}
