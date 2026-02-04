package org.gp.newspinbe.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {

	// Bad Request
	BAD_REQUEST("C001", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
	INVALID_INPUT_VALUE("C002", "유효하지 않은 입력 값입니다.", HttpStatus.BAD_REQUEST),
	INVALID_TYPE_VALUE("C003", "요청 데이터 타입이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
	MISSING_REQUIRED_FIELD("C004", "필수 입력 항목이 누락되었습니다.", HttpStatus.BAD_REQUEST),
	INVALID_REFRESHTOKEN("C005", "유효하지 않은 토큰입니다.", HttpStatus.BAD_REQUEST),

	// HTTP 401 Unauthorized
	UNAUTHORIZED_ACCESS("C101", "인증 정보가 유효하지 않습니다.", HttpStatus.UNAUTHORIZED),
	TOKEN_EXPIRED("C102", "인증 토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED),
	INVALID_TOKEN("C103", "유효하지 않은 인증 토큰입니다.", HttpStatus.UNAUTHORIZED),

	// HTTP 403 Forbidden
	FORBIDDEN_ACCESS("C201", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),

	// Internal Server Error
	INTERNAL_SERVER_ERROR("C999", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
	EXTERNAL_SERVICE_ERROR("C901", "외부 서비스 연동 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

	// User
	USER_DUPLICATED("U001", "이미 존재하는 유저입니다.", HttpStatus.CONFLICT),
	EMAIL_DUPLICATION("U002", "이미 존재하는 이메일입니다.", HttpStatus.CONFLICT),
	USER_NOT_FOUND("U003", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	VERIFICATION_CODE_EXPIRED("U004", "인증번호가 만료되었습니다. 다시 시도해주세요.", HttpStatus.BAD_REQUEST),
	VERIFICATION_CODE_MISMATCH("U005", "인증번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),

	// News
	NEWS_NOT_FOUND("N001", "뉴스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

	private final HttpStatus status;
	private final String code;
	private String message;

	ErrorCode(final String code, final String message, final HttpStatus status) {
		this.status = status;
		this.code = code;
		this.message = message;
	}
}