package org.gp.newspinbe.global.exception;


import java.util.LinkedHashMap;
import java.util.Map;

import org.gp.newspinbe.global.common.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * 필드별 검증 실패 메시지를 {@code data}에 {필드명: 메시지} 로 담아 반환한다(I-16).
	 * 이전에는 전부 "유효성 검사가 실패했습니다."로 뭉뚱그려져 클라이언트가 어떤 필드가
	 * 왜 실패했는지 알 수 없었다. 클라이언트 입력 문제이므로 서버 오류 로그(error)가 아닌 warn.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(
			MethodArgumentNotValidException e) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
			fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		log.warn("Validation 실패: {}", fieldErrors);
		ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
		return ResponseEntity.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), fieldErrors));
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

	/**
	 * UserService.signIn 이 잘못된 이메일/비밀번호로 AuthenticationManager.authenticate(...) 를
	 * 호출하면 Spring Security 가 BadCredentialsException(AuthenticationException 의 하위) 을
	 * 던진다. 이 핸들러가 없으면 catch-all(Exception.class) 로 떨어져 500 "서버 내부 오류가
	 * 발생했습니다."로 응답했다 — 클라이언트 입력 문제인데 서버 오류처럼 보였음.
	 */
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
		log.warn("로그인 실패: {}", e.getMessage());
		ErrorCode errorCode = ErrorCode.LOGIN_FAILED;
		return ResponseEntity.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
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
