package com.nanacocoa.server.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_01", "요청 값을 확인해주세요."),

	ALREADY_EXIST_EMAIL(HttpStatus.CONFLICT, "MEMBER_01", "이미 등록된 이메일입니다."),
	NOT_VALID_PASSWORD(HttpStatus.BAD_REQUEST, "MEMBER_02", "비밀번호를 다시 확인해주세요."),
	NOT_FOUND_EMAIL(HttpStatus.NOT_FOUND, "MEMBER_03", "이메일을 찾을 수 없습니다."),
	AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_01", "로그인이 필요합니다."),
	ADMIN_ACCESS_REQUIRED(HttpStatus.FORBIDDEN, "AUTH_02", "운영자만 접근할 수 있습니다."),

	ALREADY_EXIST_PRODUCTS(HttpStatus.CONFLICT, "PRODUCTS_01", "이미 등록된 상품입니다."),
	NOT_FOUND_PRODUCTS(HttpStatus.NOT_FOUND, "PRODUCTS_02", "상품을 찾을 수 없습니다."),
	INVALID_PRODUCT_IMAGE(HttpStatus.BAD_REQUEST, "PRODUCTS_03", "JPEG, PNG, WebP 이미지만 등록할 수 있습니다."),
	PRODUCT_IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "PRODUCTS_04", "상품 이미지는 5MB 이하만 등록할 수 있습니다."),
	PRODUCT_IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "PRODUCTS_05", "상품 이미지 업로드에 실패했습니다."),

	NOT_FOUND_ORDER(HttpStatus.NOT_FOUND, "ORDER_01", "주문을 찾을 수 없습니다."),
	INVALID_ORDER_ITEMS(HttpStatus.BAD_REQUEST, "ORDER_02", "주문 상품 정보를 확인해주세요."),
	ORDER_AMOUNT_OVERFLOW(HttpStatus.BAD_REQUEST, "ORDER_03", "주문 금액이 허용 범위를 초과했습니다."),

	NOT_FOUND_PAYMENT(HttpStatus.NOT_FOUND, "PAYMENT_01", "결제 정보를 찾을 수 없습니다."),
	PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_02", "주문 금액과 결제 금액이 일치하지 않습니다."),
	IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "PAYMENT_03", "멱등성 키가 필요합니다."),
	IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "PAYMENT_04", "멱등성 키가 다른 요청에 이미 사용되었습니다."),
	PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "PAYMENT_05", "이미 결제가 처리된 주문입니다."),
	PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "PAYMENT_06", "동일 주문의 결제가 처리 중입니다."),
	PAYMENT_LOCK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_07", "결제 잠금을 획득할 수 없습니다."),
	PAYMENT_LOCK_INTERRUPTED(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_08", "결제 잠금 대기가 중단되었습니다."),
	PAYMENT_APPROVAL_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_09", "결제 승인에 실패했습니다."),
	PAYMENT_CANCEL_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_10", "결제 취소에 실패했습니다."),
	PAYMENT_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_11", "결제사와 통신할 수 없습니다."),
	INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "PAYMENT_12", "현재 결제 상태에서는 요청을 처리할 수 없습니다."),
	UNSUPPORTED_PAYMENT_STATUS(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_13", "지원하지 않는 결제 상태입니다.");


	private final HttpStatus httpStatus;
	private final String code;
	private final String error;
}
