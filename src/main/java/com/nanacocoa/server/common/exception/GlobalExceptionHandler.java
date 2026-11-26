package com.nanacocoa.server.common.exception;

import com.nanacocoa.server.common.observability.IncidentTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.nanacocoa.server.common.exception.ErrorCode.ALREADY_EXIST_EMAIL;
import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_REQUEST;
import static com.nanacocoa.server.common.exception.ErrorCode.PRODUCT_IMAGE_TOO_LARGE;

/**
 *  예외가 발생할 시 해당 예외에 대한 HTTP 상태 코드와 메시지를 생성하여 ResponseEntity 객체로 반환합니다.
 *  반환된 객체는 클라이언트에게 전송되어 예외 처리 결과를 알려줍니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(value = NanacocoaException.class)
    public ResponseEntity<?> handleNanacocoaServiceException(NanacocoaException e){
        HttpStatus status = e.getErrorCode().getHttpStatus();
        String code = e.getErrorCode().getCode();
        String error = e.getErrorCode().getError();
        ErrorMessage errorMessage = new ErrorMessage(code, error);

        switch (e.getErrorCode()) {
            case INSUFFICIENT_STOCK, STOCK_CONFLICT, ORDER_RESERVATION_EXPIRED ->
                    IncidentTrace.mark(IncidentTrace.Result.STOCK_CONFLICT);
            case PAYMENT_APPROVAL_FAILED, PAYMENT_CANCEL_FAILED -> IncidentTrace.mark(IncidentTrace.Result.FAILED);
            case PAYMENT_RECOVERY_DEFERRED, PAYMENT_IN_PROGRESS -> IncidentTrace.mark(IncidentTrace.Result.DEFERRED);
            default -> { }
        }
        logger.error(e.getErrorCode().getCode() + " : " + e.getErrorCode().getError());

        return ResponseEntity.status(status).cacheControl(org.springframework.http.CacheControl.noStore()).body(errorMessage);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleConflict(DataIntegrityViolationException e) {
        HttpStatus status = HttpStatus.CONFLICT;
        String code = "DUPLICATE_DATA";
        String error = "중복된 데이터가 존재합니다.";
        String message = e.getMostSpecificCause().getMessage();

        if (message != null && (message.contains("uk_members_email") || message.contains("uk_email"))) {
            code = ALREADY_EXIST_EMAIL.getCode();
            error = ALREADY_EXIST_EMAIL.getError();
        }
        ErrorMessage errorMessage = new ErrorMessage(code, error);

        logger.error(error + " : " + error);

        return ResponseEntity.status(status).cacheControl(org.springframework.http.CacheControl.noStore()).body(errorMessage);
    }

	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
	public ResponseEntity<ErrorMessage> handleInvalidRequest(Exception exception) {
		ErrorMessage errorMessage = new ErrorMessage(INVALID_REQUEST.getCode(), INVALID_REQUEST.getError());
		return ResponseEntity.status(INVALID_REQUEST.getHttpStatus()).body(errorMessage);
	}

	@ExceptionHandler({org.springframework.dao.PessimisticLockingFailureException.class,
			org.springframework.orm.ObjectOptimisticLockingFailureException.class})
	public ResponseEntity<ErrorMessage> handleConcurrentUpdate(Exception exception) {
		ErrorCode error = ErrorCode.STOCK_CONFLICT;
		IncidentTrace.mark(IncidentTrace.Result.STOCK_CONFLICT);
		return ResponseEntity.status(error.getHttpStatus()).body(new ErrorMessage(error.getCode(), error.getError()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorMessage> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
		ErrorMessage errorMessage = new ErrorMessage(
				PRODUCT_IMAGE_TOO_LARGE.getCode(),
				PRODUCT_IMAGE_TOO_LARGE.getError()
		);
		return ResponseEntity.status(PRODUCT_IMAGE_TOO_LARGE.getHttpStatus()).body(errorMessage);
	}
}
