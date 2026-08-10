package com.nanacocoa.server.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public class NanacocoaException extends RuntimeException{
	private final ErrorCode errorCode;
}
