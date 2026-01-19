package org.gp.newspinbe.domain.user.presentation;

import org.gp.newspinbe.domain.user.application.UserService;
import org.gp.newspinbe.domain.user.dto.request.RefreshRequest;
import org.gp.newspinbe.domain.user.dto.request.SignInRequest;
import org.gp.newspinbe.domain.user.dto.request.SignUpRequest;
import org.gp.newspinbe.domain.user.dto.response.SignInResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@PostMapping("/sign-up")
	public ResponseEntity<Void> signUp(
		@RequestBody SignUpRequest signUpRequest
	) {
		userService.signUp(signUpRequest);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/sign-in")
	public ResponseEntity<SignInResponse> signIn(
		@RequestBody SignInRequest signInRequest
	) {
		SignInResponse response = userService.signIn(signInRequest);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
		@RequestHeader("Authorization") String accessToken
	) {
		userService.logout(accessToken);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/refresh")
	public ResponseEntity<SignInResponse> refresh(
		@RequestBody RefreshRequest refreshRequest
	) {
		SignInResponse response = userService.refresh(refreshRequest);
		return ResponseEntity.ok(response);
	}
}
