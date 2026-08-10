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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.nanacocoa.server.common.exception.ErrorCode.AUTHENTICATION_REQUIRED;
import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_ORDER_ITEMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
	@Mock
	private OrderRepository orderRepository;
	@Mock
	private ProductsRepository productsRepository;
	@Mock
	private MemberRepository memberRepository;

	private OrderService orderService;
	private Member member;
	private UserDetailsImpl userDetails;

	@BeforeEach
	void setUp() {
		orderService = new OrderService(orderRepository, productsRepository, memberRepository);
		member = Member.builder()
				.email("buyer@example.com")
				.name("구매자")
				.password("encoded")
				.build();
		userDetails = new UserDetailsImpl(member, member.getEmail());
	}

	@Test
	void createsMultiItemOrderUsingServerPrices() {
		Products cocoa = product("코코아", 12000L);
		Products cake = product("케이크", 18000L);
		setId(cocoa, 1L);
		setId(cake, 2L);
		when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
		when(productsRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(cocoa, cake));
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

		OrderResponse response = orderService.createOrder(
				request(
						"문 앞에 놓아주세요.",
						List.of(new OrderItemRequest(1L, 2), new OrderItemRequest(2L, 1))
				),
				userDetails
		);

		assertThat(response.getOrderId()).startsWith("NC_");
		assertThat(response.getRecipientName()).isEqualTo("구매자");
		assertThat(response.getPhoneNumber()).isEqualTo("010-1234-5678");
		assertThat(response.getShippingAddress()).isEqualTo("서울시 성동구 성수동");
		assertThat(response.getCustomerRequest()).isEqualTo("문 앞에 놓아주세요.");
		assertThat(response.getTotalAmount()).isEqualTo(42000L);
		assertThat(response.getItems()).hasSize(2);
		assertThat(response.getItems().get(0).getUnitPrice()).isEqualTo(12000L);
	}

	@Test
	void rejectsDuplicateProductIdsBeforeRepositoryLookup() {
		when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
		CreateOrderRequest request = request(
				null,
				List.of(new OrderItemRequest(1L, 1), new OrderItemRequest(1L, 2))
		);

		assertThatThrownBy(() -> orderService.createOrder(request, userDetails))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(INVALID_ORDER_ITEMS));

		verify(productsRepository, never()).findAllById(any());
	}

	@Test
	void rejectsOrderWhenRequestedProductDoesNotExist() {
		when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
		when(productsRepository.findAllById(List.of(999L))).thenReturn(List.of());

		assertThatThrownBy(() -> orderService.createOrder(
				request(null, List.of(new OrderItemRequest(999L, 1))),
				userDetails
		))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(INVALID_ORDER_ITEMS));

		verify(orderRepository, never()).save(any());
	}

	@Test
	void requiresAuthenticatedMember() {
		CreateOrderRequest request = request(null, List.of(new OrderItemRequest(1L, 1)));

		assertThatThrownBy(() -> orderService.createOrder(request, null))
				.isInstanceOfSatisfying(NanacocoaException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(AUTHENTICATION_REQUIRED));
	}

	@Test
	void storesNullWhenOptionalCustomerRequestIsBlank() {
		Products product = product("코코아", 12000L);
		setId(product, 1L);
		when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
		when(productsRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

		OrderResponse response = orderService.createOrder(
				request("   ", List.of(new OrderItemRequest(1L, 1))),
				userDetails
		);

		assertThat(response.getCustomerRequest()).isNull();
	}

	private CreateOrderRequest request(String customerRequest, List<OrderItemRequest> items) {
		return new CreateOrderRequest(
				" 구매자 ",
				" 010-1234-5678 ",
				" 서울시 성동구 성수동 ",
				customerRequest,
				items
		);
	}

	private Products product(String name, long price) {
		return Products.builder()
				.name(name)
				.price(price)
				.summary("요약")
				.detailTitle("상세")
				.description("설명")
				.build();
	}

	private void setId(Products product, Long id) {
		product.setId(id);
	}
}
