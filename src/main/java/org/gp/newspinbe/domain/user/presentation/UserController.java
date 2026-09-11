package org.gp.newspinbe.domain.user.presentation;

import org.gp.newspinbe.domain.user.application.UserService;
import org.gp.newspinbe.domain.user.dto.request.EmailVerificationRequest;
import org.gp.newspinbe.domain.user.dto.request.PasswordResetRequest;
import org.gp.newspinbe.domain.user.dto.request.RefreshRequest;
import org.gp.newspinbe.domain.user.dto.request.SignInRequest;
import org.gp.newspinbe.domain.user.dto.request.SignUpRequest;
import org.gp.newspinbe.domain.user.dto.response.EmailVerificationResponse;
import org.gp.newspinbe.domain.user.dto.response.SignInResponse;
import org.gp.newspinbe.domain.user.dto.response.UserDetailResponse;
import org.gp.newspinbe.global.common.ApiResponse;
import org.gp.newspinbe.global.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@Tag(name = "사용자", description = "회원가입, 로그인/토큰 재발급, 이메일 인증, 비밀번호 재설정, 내 정보 조회")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@Operation(summary = "이메일 인증코드 발송", description = "가입/비밀번호 재설정 전 이메일 소유 확인용 인증코드를 발송한다.")
	@SecurityRequirements
	@PostMapping("/email/send-verification")
	public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(
			@RequestParam @Valid @NotBlank @Email String email) {
		userService.sendVerificationEmail(email);
		return ResponseEntity.ok(ApiResponse.success());
	}

	@Operation(summary = "이메일 인증코드 확인")
	@SecurityRequirements
	@PostMapping("/email/verify")
	public ResponseEntity<ApiResponse<EmailVerificationResponse>> verifyEmail(
			@RequestBody @Valid EmailVerificationRequest emailVerificationRequest) {
		EmailVerificationResponse emailVerificationResponse = userService.verifyEmail(
				emailVerificationRequest.getEmail(), emailVerificationRequest.getCode());
		return ResponseEntity.ok(ApiResponse.success(emailVerificationResponse));
	}

	@Operation(summary = "회원가입", description = "이메일 인증이 완료된 이메일만 가입 가능하다.")
	@SecurityRequirements
	@PostMapping("/sign-up")
	public ResponseEntity<ApiResponse<Void>> signUp(
			@RequestBody @Valid SignUpRequest signUpRequest) {
		userService.signUp(signUpRequest);
		return ResponseEntity.ok(ApiResponse.success());
	}

	@Operation(summary = "로그인", description = "access token(24h) / refresh token(3d) 발급. refresh token 은 Redis 에 디바이스별로 저장된다.")
	@SecurityRequirements
	@PostMapping("/sign-in")
	public ResponseEntity<ApiResponse<SignInResponse>> signIn(
			@RequestBody @Valid SignInRequest signInRequest) {
		SignInResponse response = userService.signIn(signInRequest);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(summary = "로그아웃", description = "access token 을 블랙리스트에 등록하고 연결된 refresh token 을 무효화한다.")
	@SecurityRequirement(name = "bearer")
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
			@RequestHeader("Authorization") @Valid @NotBlank String accessToken) {
		userService.logout(accessToken);
		return ResponseEntity.ok(ApiResponse.success());
	}

	@Operation(summary = "토큰 재발급", description = "refresh token 으로 access/refresh token 을 함께 재발급한다(회전). 사용된 refresh token 은 즉시 무효화된다.")
	@SecurityRequirements
	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<SignInResponse>> refresh(
			@RequestBody @Valid RefreshRequest refreshRequest) {
		SignInResponse response = userService.refresh(refreshRequest);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(summary = "내 정보 조회")
	@SecurityRequirement(name = "bearer")
	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserDetailResponse>> getUserDetail(
			@AuthenticationPrincipal CustomUserDetails userDetails) {
		UserDetailResponse response = userService.getUserDetail(userDetails.getUsername());
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(summary = "비밀번호 재설정 코드 발송")
	@SecurityRequirements
	@PostMapping("/password/send-reset-code")
	public ResponseEntity<ApiResponse<Void>> sendPasswordResetCode(
			@RequestParam @Valid @NotBlank @Email String email) {
		userService.sendPasswordResetCode(email);
		return ResponseEntity.ok(ApiResponse.success());
	}

	@Operation(summary = "비밀번호 재설정", description = "발송된 코드 확인 후 새 비밀번호로 변경한다.")
	@SecurityRequirements
	@PostMapping("/password/reset")
	public ResponseEntity<ApiResponse<Void>> resetPassword(
			@RequestBody @Valid PasswordResetRequest request) {
		userService.resetPassword(request);
		return ResponseEntity.ok(ApiResponse.success());
	}
}
