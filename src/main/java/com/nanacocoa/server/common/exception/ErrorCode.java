package com.nanacocoa.server.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
	RECOVERY_INVALID(HttpStatus.BAD_REQUEST, "RECOVERY_01", "복구 링크를 사용할 수 없습니다. 복구 안내를 다시 요청해주세요."),
	RECOVERY_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RECOVERY_02", "요청이 많습니다. 잠시 후 다시 시도해주세요."),
	RECOVERY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "RECOVERY_03", "비밀번호 복구를 이용할 수 없습니다. 잠시 후 다시 시도해주세요."),
	SESSION_CHECK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_05", "로그인 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요."),
	ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "ADDRESS_01", "배송지를 찾을 수 없습니다."),
	ADDRESS_CONFLICT(HttpStatus.CONFLICT, "ADDRESS_02", "배송지 정보가 변경되었습니다. 다시 조회해주세요."),
	CART_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_01", "장바구니 항목을 찾을 수 없습니다."),
	CART_CONFLICT(HttpStatus.CONFLICT, "CART_02", "장바구니 또는 상품 가격이 변경되었습니다. 다시 확인해주세요."),
	RESTOCK_NOT_SOLD_OUT(HttpStatus.CONFLICT, "RESTOCK_01", "현재 품절 상품만 재입고 알림을 신청할 수 있습니다."),
	RESTOCK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "RESTOCK_02", "현재 재입고 알림을 신청할 수 없습니다."),
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_01", "요청 값을 확인해주세요."),
	POLICY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "POLICY_01", "서비스 정책 승인 및 회원가입 준비 중입니다."),
	POLICY_VERSION_CHANGED(HttpStatus.CONFLICT, "POLICY_02", "서비스 정책이 변경되었습니다. 문서를 다시 확인하고 동의해주세요."),
	POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "POLICY_03", "게시된 정책 문서를 찾을 수 없습니다."),
	RETURN_POLICY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "RETURN_01", "취소·반품 정책 승인 및 운영 준비 중입니다."),
	RETURN_CONFLICT(HttpStatus.CONFLICT, "RETURN_02", "신청 상태 또는 요청 내용이 변경되었습니다. 다시 조회해주세요."),
	RETURN_REQUEST_REQUIRED(HttpStatus.CONFLICT, "RETURN_03", "주문 상세에서 취소·반품을 신청해주세요."),

	ALREADY_EXIST_EMAIL(HttpStatus.CONFLICT, "MEMBER_01", "이미 등록된 이메일입니다."),
	NOT_VALID_PASSWORD(HttpStatus.BAD_REQUEST, "MEMBER_02", "비밀번호를 다시 확인해주세요."),
	NOT_FOUND_EMAIL(HttpStatus.NOT_FOUND, "MEMBER_03", "이메일을 찾을 수 없습니다."),
	MEMBER_ID_IMMUTABLE(HttpStatus.BAD_REQUEST, "MEMBER_04", "아이디는 변경할 수 없습니다."),
	PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "MEMBER_05", "새 비밀번호와 확인 값이 일치하지 않습니다."),
	MEMBER_UPDATE_CONFLICT(HttpStatus.CONFLICT, "MEMBER_06", "회원정보 변경 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF_INVALID", "보안 토큰이 만료되었거나 유효하지 않습니다. 다시 시도해 주세요."),
	AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_01", "로그인이 필요합니다."),
	PROFILE_VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "AUTH_03", "회원정보를 확인하려면 현재 비밀번호로 본인 확인을 해주세요."),
	PROFILE_VERIFICATION_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_04", "본인 확인에 여러 번 실패했습니다. 5분 후 다시 시도해주세요."),
	ADMIN_ACCESS_REQUIRED(HttpStatus.FORBIDDEN, "AUTH_02", "운영자만 접근할 수 있습니다."),

	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFY_01", "알림을 찾을 수 없습니다."),
	FAQ_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SUPPORT_01", "FAQ를 불러오지 못했습니다."),
	SUPPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "SUPPORT_02", "문의를 찾을 수 없습니다."),
	SUPPORT_REQUEST_CONFLICT(HttpStatus.CONFLICT, "SUPPORT_03", "같은 요청 키로 다른 문의를 제출할 수 없습니다."),
	SUPPORT_REPLY_CONFLICT(HttpStatus.CONFLICT, "SUPPORT_04", "이미 답변이 등록됐습니다. 문의를 다시 확인해주세요."),
	SUPPORT_ACCOUNT_CHANGED(HttpStatus.CONFLICT, "SUPPORT_05", "로그인한 계정이 변경됐습니다."),
	CATEGORY_UNAVAILABLE(HttpStatus.NOT_FOUND, "PRODUCTS_08", "카테고리를 이용할 수 없습니다."),
	CATEGORY_NAME_CONFLICT(HttpStatus.CONFLICT, "PRODUCTS_09", "이미 등록된 카테고리 이름입니다."),
	ALREADY_EXIST_PRODUCTS(HttpStatus.CONFLICT, "PRODUCTS_01", "이미 등록된 상품입니다."),
	NOT_FOUND_PRODUCTS(HttpStatus.NOT_FOUND, "PRODUCTS_02", "상품을 찾을 수 없습니다."),
	INVALID_PRODUCT_IMAGE(HttpStatus.BAD_REQUEST, "PRODUCTS_03", "JPEG, PNG, WebP 이미지만 등록할 수 있습니다."),
	PRODUCT_IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "PRODUCTS_04", "상품 이미지는 5MB 이하만 등록할 수 있습니다."),
	PRODUCT_IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "PRODUCTS_05", "상품 이미지 업로드에 실패했습니다."),

	INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "PRODUCTS_06", "판매 가능 재고가 부족하거나 판매 중지된 상품입니다."),
	STOCK_CONFLICT(HttpStatus.CONFLICT, "PRODUCTS_07", "재고가 변경되었습니다. 새로고침 후 다시 시도해 주세요."),
	ORDER_RESERVATION_EXPIRED(HttpStatus.CONFLICT, "ORDER_04", "주문 유효 시간이 만료되었거나 재고 미확인 주문입니다. 새 주문을 생성해 주세요."),

	SHIPPING_CONFLICT(HttpStatus.CONFLICT, "SHIP_01", "현재 배송 상태에서는 처리할 수 없습니다. 주문 상세를 다시 확인해주세요."),
	SHIPPING_CANCEL_BLOCKED(HttpStatus.CONFLICT, "SHIP_02", "출고 여부 확인 또는 반품 안내는 고객 문의를 이용해주세요."),

	NOT_FOUND_ORDER(HttpStatus.NOT_FOUND, "ORDER_01", "주문을 찾을 수 없습니다."),
	INVALID_ORDER_ITEMS(HttpStatus.BAD_REQUEST, "ORDER_02", "주문 상품 정보를 확인해주세요."),
	ORDER_AMOUNT_OVERFLOW(HttpStatus.BAD_REQUEST, "ORDER_03", "주문 금액이 허용 범위를 초과했습니다."),
	ORDER_SUBMISSION_CONFLICT(HttpStatus.CONFLICT, "ORDER_05", "이미 접수한 주문의 내용과 다릅니다. 기존 주문을 확인해주세요."),
	ORDER_CHECKOUT_ACCOUNT_CHANGED(HttpStatus.CONFLICT, "ORDER_06", "로그인한 계정이 바뀌었습니다. 주문서를 다시 열어주세요."),

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
	PAYMENT_RECOVERY_DEFERRED(HttpStatus.CONFLICT, "PAYMENT_14", "복구 예정 시각 또는 거래 상태를 확인해주세요."),
	UNSUPPORTED_PAYMENT_STATUS(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_13", "지원하지 않는 결제 상태입니다.");


	private final HttpStatus httpStatus;
	private final String code;
	private final String error;
}
