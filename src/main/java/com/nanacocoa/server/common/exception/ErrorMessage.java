package com.nanacocoa.server.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 이 클래스는 예외 발생 시에 클라이언트에게 반환되는 응답 형식을 정의합니다.
 * 필드에는 예외 처리 결과에 대한 메시지와 코드가 있습니다.
 * 이 메시지와 코드는 클라이언트에게 전송돼 예외 처리 결과를 알려주는 데 사용됩니다.
 * 아래는 형식의 예시 입니다.
 *
 * {
 * "message" :"접근 권한이 없습니다.",
 * "code" : "NOT_VALID_ACCESS"
 * }
 */
@Getter
@AllArgsConstructor
public class ErrorMessage {

    private final String code;
    private final String error;
}
