package com.nanacocoa.server.order.service;

import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.order.dto.reqeust.CreateOrderRequest;
import com.nanacocoa.server.order.dto.reqeust.OrderItemRequest;
import com.nanacocoa.server.order.dto.response.OrderResponse;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.repository.OrderRepository;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.repository.ProductsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nanacocoa.server.common.exception.ErrorCode.AUTHENTICATION_REQUIRED;
import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_REQUEST;
import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_ORDER_ITEMS;

@Service
@RequiredArgsConstructor
public class OrderService {
	private final OrderRepository orderRepository;
	private final ProductsRepository productsRepository;
	private final MemberRepository memberRepository;

	@Transactional
	public OrderResponse createOrder(CreateOrderRequest request, UserDetailsImpl userDetails) {
		Member member = authenticatedMember(userDetails);
		validateShippingInformation(request);
		validateItems(request);
		validateNoDuplicateProducts(request.getItems());

		List<Long> productIds = request.getItems().stream().map(OrderItemRequest::getProductId).toList();
		Map<Long, Products> productsById = new HashMap<>();
		productsRepository.findAllById(productIds).forEach(product -> productsById.put(product.getId(), product));
		if (productsById.size() != productIds.size()) {
			throw new NanacocoaException(INVALID_ORDER_ITEMS);
		}

		Order order = Order.create(
				"NC_" + UUID.randomUUID(),
				member,
				request.getRecipientName().strip(),
				request.getPhoneNumber().strip(),
				request.getShippingAddress().strip(),
				normalizeCustomerRequest(request.getCustomerRequest())
		);
		for (OrderItemRequest item : request.getItems()) {
			order.addItem(productsById.get(item.getProductId()), item.getQuantity());
		}

		return OrderResponse.from(orderRepository.save(order));
	}

	private Member authenticatedMember(UserDetailsImpl userDetails) {
		if (userDetails == null) {
			throw new NanacocoaException(AUTHENTICATION_REQUIRED);
		}
		return memberRepository.findByEmail(userDetails.getUsername())
				.orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED));
	}

	private void validateNoDuplicateProducts(List<OrderItemRequest> items) {
		HashSet<Long> productIds = new HashSet<>();
		if (items.stream().anyMatch(item -> !productIds.add(item.getProductId()))) {
			throw new NanacocoaException(INVALID_ORDER_ITEMS);
		}
	}

	private void validateShippingInformation(CreateOrderRequest request) {
		if (request == null
				|| isBlank(request.getRecipientName())
				|| isBlank(request.getPhoneNumber())
				|| isBlank(request.getShippingAddress())) {
			throw new NanacocoaException(INVALID_REQUEST);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private String normalizeCustomerRequest(String customerRequest) {
		return isBlank(customerRequest) ? null : customerRequest.strip();
	}

	private void validateItems(CreateOrderRequest request) {
		if (request == null || request.getItems() == null || request.getItems().isEmpty()
				|| request.getItems().stream().anyMatch(item -> item == null
				|| item.getProductId() == null
				|| item.getQuantity() == null
				|| item.getQuantity() <= 0)) {
			throw new NanacocoaException(INVALID_ORDER_ITEMS);
		}
	}
}
