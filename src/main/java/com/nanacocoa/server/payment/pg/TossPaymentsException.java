package com.nanacocoa.server.payment.pg;

import lombok.Getter;

@Getter
public class TossPaymentsException extends RuntimeException {
	public enum Kind {
		DEFINITIVE,
		UNCERTAIN,
		NOT_FOUND,
		UNAVAILABLE
	}

	private final Kind kind;
	private final String code;

	public TossPaymentsException(Kind kind, String code, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
		this.code = code;
	}

	public TossPaymentsException(Kind kind, String code, String message) {
		this(kind, code, message, null);
	}

	public boolean isUncertain() {
		return kind == Kind.UNCERTAIN;
	}

	public boolean isDefinitive() {
		return kind == Kind.DEFINITIVE;
	}

	public boolean isNotFound() {
		return kind == Kind.NOT_FOUND;
	}
}
