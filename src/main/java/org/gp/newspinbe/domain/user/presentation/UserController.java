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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@PostMapping("/email/send-verification")
	public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(
			@RequestParam @Valid @NotBlank @Email String email) {
		userService.sendVerificationEmail(email);
		return ResponseEntity.ok(ApiResponse.success());
	}

	@PostMapping("/email/verify")
	public ResponseEntity<ApiResponse<EmailVerificationResponse>> verifyEmail(
			@RequestBody @Valid EmailVerificationRequest emailVerificationRequest) {
		EmailVerificationResponse emailVerificationResponse = userService.verifyEmail(
				emailVerificationRequest.getEmail(), emailVerificationRequest.getCode());
		return ResponseEntity.ok(ApiResponse.success(emailVerificationResponse));
	}

	@PostMapping("/sign-up")
	public ResponseEntity<ApiResponse<Void>> signUp(
			@RequestBody @Valid SignUpRequest signUpRequest) {
		userService.signUp(signUpRequest);
		return ResponseEntity.ok(ApiResponse.success());
	}

	@PostMapping("/sign-in")
	public ResponseEntity<ApiResponse<SignInResponse>> signIn(
			@RequestBody @Valid SignInRequest signInRequest) {
		SignInResponse response = userService.signIn(signInRequest);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
			@RequestHeader("Authorization") @Valid @NotBlank String accessToken) {
		userService.logout(accessToken);
		return ResponseEntity.ok(ApiResponse.success());
	}

	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<SignInResponse>> refresh(
			@RequestBody @Valid RefreshRequest refreshRequest) {
		SignInResponse response = userService.refresh(refreshRequest);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserDetailResponse>> getUserDetail(
			@AuthenticationPrincipal CustomUserDetails userDetails) {
		UserDetailResponse response = userService.getUserDetail(userDetails.getUsername());
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping("/password/send-reset-code")
	public ResponseEntity<ApiResponse<Void>> sendPasswordResetCode(
			@RequestParam @Valid @NotBlank @Email String email) {
		userService.sendPasswordResetCode(email);
		return ResponseEntity.ok(ApiResponse.success());
	}

	@PostMapping("/password/reset")
	public ResponseEntity<ApiResponse<Void>> resetPassword(
			@RequestBody @Valid PasswordResetRequest request) {
		userService.resetPassword(request);
		return ResponseEntity.ok(ApiResponse.success());
	}
}
