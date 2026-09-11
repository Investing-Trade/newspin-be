package org.gp.newspinbe.global.common;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
	private String status;
	private String code;
	private String message;
	private T data;

	public ApiResponse(String status, String code, String message, T data) {
		this.status = status;
		this.code = code;
		this.message = message;
		this.data = data;
	}

	public static <T> ApiResponse<T> success(T data, String message) {
		return new ApiResponse<>("success", "200", message, data);
	}

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>("success", "200", "OK", data);
	}

	public static <T> ApiResponse<T> success() {
		return new ApiResponse<>("success", "200", "OK", null);
	}


	public static <T> ApiResponse<T> error(String code, String message) {
		return new ApiResponse<>("error", code, message, null);
	}

	/** 필드별 검증 오류처럼, 에러 응답에도 상세 데이터를 함께 실어야 할 때(I-16). */
	public static <T> ApiResponse<T> error(String code, String message, T data) {
		return new ApiResponse<>("error", code, message, data);
	}
}