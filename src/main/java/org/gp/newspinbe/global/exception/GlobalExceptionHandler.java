package org.gp.newspinbe.global.exception;


import org.gp.newspinbe.global.common.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
		log.error("Validation ERROR : {}", e.getBindingResult());
		return ResponseEntity.status(e.getStatusCode())
			.body(ApiResponse.error("400", "유효성 검사가 실패했습니다."));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error("400", "요청 형식이 올바르지 않습니다."));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
		HttpRequestMethodNotSupportedException e) {
		ErrorCode errorCode = ErrorCode.BAD_REQUEST;
		log.warn("HTTP method not supported: {}", e.getMethod());
		return ResponseEntity.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode.getCode(), "지원하지 않는 HTTP 메소드입니다: " + e.getMethod()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
		log.warn("Data integrity violation (동시 요청 등): {}", e.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(ApiResponse.error("C409", "이미 처리 중이거나 처리된 요청입니다. 잠시 후 다시 시도해주세요."));
	}

	@ExceptionHandler(CustomException.class)
	public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
		ErrorCode errorCode = e.getErrorCode();
		log.error("Custom ERROR: {}", errorCode.getMessage());
		return ResponseEntity.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("Exception : {}", e);
		ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
	}

}
