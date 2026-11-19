package com.nanacocoa.server.order.service;

import com.nanacocoa.server.common.exception.ErrorCode;
import com.nanacocoa.server.order.entity.OrderStatus;
import java.time.LocalDateTime;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.order.dto.reqeust.CreateOrderRequest;
import com.nanacocoa.server.order.dto.reqeust.OrderItemRequest;
import com.nanacocoa.server.order.dto.response.OrderHistoryResponse;
import com.nanacocoa.server.order.dto.response.OrderHistoryResponse.PaymentResult;
import com.nanacocoa.server.order.dto.response.OrderResponse;
import com.nanacocoa.server.payment.entity.CancellationStatus;
import com.nanacocoa.server.payment.entity.PaymentStatus;
import com.nanacocoa.server.payment.repository.PaymentCancellationRepository;
import com.nanacocoa.server.payment.repository.PaymentRepository;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.order.repository.OrderRepository;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.repository.ProductsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.nanacocoa.server.common.exception.ErrorCode.AUTHENTICATION_REQUIRED;
import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_REQUEST;
import static com.nanacocoa.server.common.exception.ErrorCode.NOT_FOUND_ORDER;
import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_ORDER_ITEMS;
import static com.nanacocoa.server.common.exception.ErrorCode.ORDER_CHECKOUT_ACCOUNT_CHANGED;
import static com.nanacocoa.server.common.exception.ErrorCode.ORDER_SUBMISSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class OrderService {
	private static final int HISTORY_PAGE_SIZE = 20;
	private static final Sort HISTORY_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");
	private static final Pattern SUBMISSION_KEY = Pattern.compile(
			"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	private final OrderRepository orderRepository;
	private final ProductsRepository productsRepository;
	private final MemberRepository memberRepository;
	private final OrderInventoryService inventoryService;
	private final PaymentRepository paymentRepository;
	private final PaymentCancellationRepository paymentCancellationRepository;
	private final ShippingService shippingService;
    private final ReturnRequestService returnRequests;

	@Transactional(readOnly = true)
	public OrderHistoryResponse.OrderPage listOrders(String pageValue, UserDetailsImpl userDetails) {
		Member member = authenticatedMember(userDetails);
		int page = parsePage(pageValue);
		PageRequest pageable = PageRequest.of(page, HISTORY_PAGE_SIZE, HISTORY_SORT);
		if (pageable.getOffset() > Integer.MAX_VALUE) {
			return new OrderHistoryResponse.OrderPage(List.of(), page, false);
		}

		var orders = orderRepository.findByMemberId(member.getId(), pageable);
		if (orders.isEmpty()) {
			return new OrderHistoryResponse.OrderPage(List.of(), page, false);
		}
		List<Long> ids = orders.getContent().stream().map(Order::getId).toList();
		Map<Long, String> names = new HashMap<>();
		for (var item : orderRepository.findRepresentativeItems(ids)) {
			names.put(item.getOrderId(), item.getProductName());
		}
		var summaries = orders.map(order -> OrderHistoryResponse.Summary.from(order, names.get(order.getId())));
		return new OrderHistoryResponse.OrderPage(summaries.getContent(), page, summaries.hasNext());
	}

	@Transactional(readOnly = true)
	public OrderHistoryResponse getOrder(String orderId, UserDetailsImpl userDetails) {
		Member member = authenticatedMember(userDetails);
		Order order = orderRepository.findByOrderIdAndMemberId(orderId, member.getId())
				.orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
		return OrderHistoryResponse.from(order, paymentResult(order), shippingService.tracking(order), returnRequests.orderView(order));
	}

	private int parsePage(String value) {
		if (value == null) {
			return 0;
		}
		try {
			if (value.matches("[0-9]+")) {
				return Integer.parseInt(value);
			}
		} catch (NumberFormatException ignored) {
			// int 범위를 넘는 페이지 번호도 동일한 요청 오류로 응답합니다.
		}
		throw new NanacocoaException(INVALID_REQUEST);
	}

	private PaymentResult paymentResult(Order order) {
		Long orderId = order.getId();
		return switch (order.getStatus()) {
			case PAYMENT_PENDING -> PaymentResult.PENDING;
			case PAID -> paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.CANCEL_PENDING)
					? PaymentResult.CANCELLATION_PENDING : PaymentResult.PAID;
			case CANCELED -> paymentCancellationRepository.existsByPaymentOrderIdAndStatus(
					orderId, CancellationStatus.SUCCESS)
					? PaymentResult.PAYMENT_CANCELED : PaymentResult.ORDER_CANCELED;
			case CREATED -> paymentRepository.existsByOrderIdAndStatusIn(
					orderId, List.of(PaymentStatus.FAILED, PaymentStatus.CANCELED))
					? PaymentResult.FAILED : PaymentResult.UNPAID;
		};
	}

	@Transactional(readOnly = true)
	public OrderResponse getSubmission(String rawKey, String checkoutAccount, UserDetailsImpl userDetails) {
		Member member = authenticatedMember(userDetails);
		checkCheckoutAccount(member, checkoutAccount);
		return orderRepository.findByMemberIdAndSubmissionKey(member.getId(), normalizeKey(rawKey))
				.map(OrderResponse::from)
				.orElseThrow(() -> new NanacocoaException(NOT_FOUND_ORDER));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Submission createOrder(CreateOrderRequest request, String rawKey, String checkoutAccount,
			UserDetailsImpl userDetails) {
		if (userDetails == null) throw new NanacocoaException(AUTHENTICATION_REQUIRED);
		String key = normalizeKey(rawKey);
		// 같은 회원의 제출은 재고 검사 전에 직렬화합니다.
		Member member = memberRepository.findByEmailForUpdate(userDetails.getUsername())
				.orElseThrow(() -> new NanacocoaException(AUTHENTICATION_REQUIRED));
		checkCheckoutAccount(member, checkoutAccount);
		validateShippingInformation(request);
		validateItems(request);
		validateNoDuplicateProducts(request.getItems());

		var existing = orderRepository.findByMemberIdAndSubmissionKey(member.getId(), key);
		if (existing.isPresent()) {
			Order order = existing.get();
			if (!matchesSubmission(order, request)) {
				throw new NanacocoaException(ORDER_SUBMISSION_CONFLICT);
			}
			return new Submission(OrderResponse.from(order), false);
		}

		List<Long> productIds = request.getItems().stream().map(OrderItemRequest::getProductId).toList();
		Map<Long, Products> productsById = new HashMap<>();
		// 하나의 트랜잭션에서 상품 ID 순서로 잠가 다중 상품 교착과 초과 판매를 방지합니다.
		productIds.stream().sorted().forEach(id -> productsRepository.findByIdForUpdate(id)
			.ifPresent(product -> productsById.put(id, product)));
		if (productsById.size() != productIds.size()) {
			throw new NanacocoaException(INVALID_ORDER_ITEMS);
		}

		Order order = Order.createForSubmission(
				"NC_" + UUID.randomUUID(),
				member,
				key,
				request.getRecipientName().strip(),
				request.getPhoneNumber().strip(),
				request.getShippingAddress().strip(),
				normalizeCustomerRequest(request.getCustomerRequest())
		);
		for (OrderItemRequest item : request.getItems()) {
			Products product = productsById.get(item.getProductId());
			product.reserve(item.getQuantity());
			order.addItem(product, item.getQuantity());
		}

		order.reserveStock(LocalDateTime.now().plusMinutes(15));
		return new Submission(OrderResponse.from(orderRepository.save(order)), true);
	}

	public record Submission(OrderResponse order, boolean created) {}

	private String normalizeKey(String key) {
		if (key == null || !SUBMISSION_KEY.matcher(key).matches()) throw new NanacocoaException(INVALID_REQUEST);
		return UUID.fromString(key).toString();
	}

	private void checkCheckoutAccount(Member member, String checkoutAccount) {
		if (checkoutAccount != null && !member.checkoutAccount().equals(checkoutAccount)) {
			throw new NanacocoaException(ORDER_CHECKOUT_ACCOUNT_CHANGED);
		}
	}

	private boolean matchesSubmission(Order order, CreateOrderRequest request) {
		if (!order.getRecipientName().equals(request.getRecipientName().strip())
				|| !order.getPhoneNumber().equals(request.getPhoneNumber().strip())
				|| !order.getShippingAddress().equals(request.getShippingAddress().strip())
				|| !Objects.equals(order.getCustomerRequest(), normalizeCustomerRequest(request.getCustomerRequest()))
				|| order.getItems().size() != request.getItems().size()) {
			return false;
		}
		Map<Long, Integer> quantities = new HashMap<>();
		order.getItems().forEach(item -> quantities.put(item.getProduct().getId(), item.getQuantity()));
		return request.getItems().stream()
				.allMatch(item -> item.getQuantity().equals(quantities.get(item.getProductId())));
	}

	@Transactional
	public OrderResponse reserveForRetry(String orderId, UserDetailsImpl userDetails) {
		Member member = authenticatedMember(userDetails);
		Order order = orderRepository.findByOrderIdForUpdate(orderId)
			.orElseThrow(() -> new NanacocoaException(ErrorCode.NOT_FOUND_ORDER));
		if (!order.isOwnedBy(member.getEmail())) throw new NanacocoaException(ErrorCode.NOT_FOUND_ORDER);
		if (order.getStatus() != OrderStatus.CREATED) throw new NanacocoaException(ErrorCode.INVALID_PAYMENT_STATUS);
		inventoryService.prepareApproval(order);
		return OrderResponse.from(order);
	}

	@Transactional
	public OrderResponse cancelUnpaid(String orderId, UserDetailsImpl userDetails) {
		Member member = authenticatedMember(userDetails);
		Order order = orderRepository.findByOrderIdForUpdate(orderId)
			.orElseThrow(() -> new NanacocoaException(ErrorCode.NOT_FOUND_ORDER));
		if (!order.isOwnedBy(member.getEmail())) throw new NanacocoaException(ErrorCode.NOT_FOUND_ORDER);
		if (order.getStatus() == OrderStatus.CANCELED) return OrderResponse.from(order);
		order.cancelUnpaid();
		inventoryService.release(order);
		return OrderResponse.from(order);
	}

	@Transactional
	public OrderResponse restock(String orderId, String reason, UserDetailsImpl userDetails) {
		authenticatedMember(userDetails).checkAdmin();
		Order order = orderRepository.findByOrderIdForUpdate(orderId)
			.orElseThrow(() -> new NanacocoaException(ErrorCode.NOT_FOUND_ORDER));
		inventoryService.restock(order, reason);
		return OrderResponse.from(order);
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
