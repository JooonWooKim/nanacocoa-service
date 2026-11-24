package com.nanacocoa.server.order.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.entity.OrderItem;
import com.nanacocoa.server.order.entity.StockState;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.repository.ProductsRepository;
import com.nanacocoa.server.products.service.RestockEventRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

/** 호출자는 기존 주문에 대해 주문 행 잠금을 먼저 획득해야 합니다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderInventoryService {
	private final ProductsRepository productsRepository;
    private final com.nanacocoa.server.order.repository.ReturnRequestRepository returnRequests;
    private final com.nanacocoa.server.order.config.ReturnPolicy returnPolicy;
    private final RestockEventRecorder restockEvents;

	public void prepareApproval(Order order) {
		if (order.getStockState() == StockState.LEGACY || order.reservationExpired(LocalDateTime.now()))
			throw new NanacocoaException(ORDER_RESERVATION_EXPIRED);
		if (order.getStockState() == StockState.RELEASED) {
			for (OrderItem item : sortedItems(order)) locked(item).reserve(item.getQuantity());
			order.reserveStock(order.getReservationExpiresAt());
		}
		if (order.getStockState() != StockState.RESERVED) throw new NanacocoaException(INVALID_PAYMENT_STATUS);
	}

	public void release(Order order) {
		if (order.getStockState() != StockState.RESERVED) return;
		for (OrderItem item : sortedItems(order)) locked(item).release(item.getQuantity());
		order.releaseStock();
	}

	public void complete(Order order) {
		if (order.getStockState() == StockState.LEGACY || order.getStockState() == StockState.SOLD) return;
		if (order.getStockState() != StockState.RESERVED) throw new NanacocoaException(STOCK_CONFLICT);
		for (OrderItem item : sortedItems(order)) locked(item).sellReserved(item.getQuantity());
		order.sellStock();
	}

	public void restock(Order order, String reason) {
        var request = returnRequests.findByActiveOrderId(order.getId()).orElse(null);
        if (request != null) {
            returnPolicy.requireAvailable();
            if (!returnPolicy.getVersion().equals(request.getPolicyVersion())
                    || request.getStatus() != com.nanacocoa.server.order.entity.ReturnRequest.Status.INSPECTED
                    || !Boolean.TRUE.equals(request.getResellable())
                    || request.getCancellation() == null
                    || request.getCancellation().getStatus() != com.nanacocoa.server.payment.entity.CancellationStatus.SUCCESS)
                throw new NanacocoaException(RETURN_CONFLICT);
        }
		if (order.getStockState() == StockState.RETURNED) return;
		order.restockAfterInspection(reason);
		for (OrderItem item : sortedItems(order)) {
			Products product = locked(item);
			long previousStock = product.getStockQuantity();
			product.restock(item.getQuantity());
			if (request != null) restockEvents.recordIfAvailable(product, previousStock);
		}
	}

	private List<OrderItem> sortedItems(Order order) {
		return order.getItems().stream().sorted(Comparator.comparing(i -> i.getProduct().getId())).toList();
	}

	private Products locked(OrderItem item) {
		return productsRepository.findByIdForUpdate(item.getProduct().getId())
			.orElseThrow(() -> new NanacocoaException(NOT_FOUND_PRODUCTS));
	}
}
