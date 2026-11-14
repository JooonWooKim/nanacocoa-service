package com.nanacocoa.server.order.service;

import com.nanacocoa.server.order.entity.OrderStatus;
import com.nanacocoa.server.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderExpirationService {
	private final OrderRepository orderRepository;
	private final OrderInventoryService inventoryService;

	@Transactional
	public void expire(String orderId) {
		orderRepository.findByOrderIdForUpdate(orderId).ifPresent(order -> {
			if (order.getStatus() == OrderStatus.CREATED && order.reservationExpired(LocalDateTime.now())) {
				order.cancelUnpaid();
				inventoryService.release(order);
			}
		});
	}
}
