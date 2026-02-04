package org.gp.newspinbe.domain.user.application;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.dto.request.PasswordResetRequest;
import org.gp.newspinbe.domain.user.dto.request.RefreshRequest;
import org.gp.newspinbe.domain.user.dto.request.SignInRequest;
import org.gp.newspinbe.domain.user.dto.request.SignUpRequest;
import org.gp.newspinbe.domain.user.dto.response.EmailVerificationResponse;
import org.gp.newspinbe.domain.user.dto.response.SignInResponse;
import org.gp.newspinbe.domain.user.dto.response.UserDetailResponse;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.gp.newspinbe.global.security.CustomUserDetails;
import org.gp.newspinbe.global.security.jwt.JwtToken;
import org.gp.newspinbe.global.security.jwt.JwtTokenProvider;
import org.gp.newspinbe.global.util.RedisUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final AuthenticationManagerBuilder authenticationManagerBuilder;
	private final RedisUtil redisUtil;
	private final EmailService emailService;

	private final Long EXPIRATION = 10 * 60L;
	private final Long REFRESH_TOKEN_EXPIRE_SECONDS = 7 * 24 * 60 * 60L;

	public void sendVerificationEmail(String email) {
		Optional<User> user = userRepository.findByEmail(email);
		if (user.isPresent()) {
			throw new CustomException(ErrorCode.EMAIL_DUPLICATION);
		}

		String title = "NEWPIN 서비스 회원가입 인증 메일";
		String code = generateRandomCode();
		String text = "인증번호: " + code;

		redisUtil.setDataExpire(email, code, EXPIRATION);

		try {
			emailService.sendEmail(email, title, text);
		} catch (Exception e) {
			log.error("Error: {}", e);
			throw new CustomException(ErrorCode.EXTERNAL_SERVICE_ERROR);
		}
	}

	public EmailVerificationResponse verifyEmail(String email, String code) {
		if (redisUtil.existData(email)) {
			String result = redisUtil.getData(email);
			if (result.equals(code)) {
				return EmailVerificationResponse.builder().verified(true).message("인증 성공하였습니다.").build();
			} else {
				return EmailVerificationResponse.builder()
						.verified(false)
						.message(result)
						.message("인증번호가 일치하지 않습니다")
						.build();
			}
		} else {
			return EmailVerificationResponse.builder().verified(false).message("인증번호가 만료되었습니다. 다시 시도해주세요.").build();
		}
	}

	@Transactional
	public void signUp(SignUpRequest signUpRequest) {
		if (userRepository.findByEmail(signUpRequest.getEmail()).isPresent()) {
			throw new CustomException(ErrorCode.USER_DUPLICATED);
		}

		String encodedPassword = passwordEncoder.encode(signUpRequest.getPassword());
		User user = User.create(signUpRequest.getEmail(), encodedPassword);
		userRepository.save(user);
	}

	@Transactional
	public SignInResponse signIn(SignInRequest signInRequest) {
		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
				signInRequest.getEmail(), signInRequest.getPassword());

		Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

		JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);

		return new SignInResponse(jwtToken);
	}

	@Transactional
	public void logout(String accessToken) {
		accessToken = accessToken.substring(7);
		String username = jwtTokenProvider.getUserNameFromToken(accessToken);
		jwtTokenProvider.deleteRefreshToken(username);
	}

	@Transactional
	public SignInResponse refresh(RefreshRequest refreshRequest) {
		if (!jwtTokenProvider.validateRefreshToken(refreshRequest.getRefreshToken())) {
			throw new CustomException(ErrorCode.INVALID_REFRESHTOKEN);
		}

		String username = jwtTokenProvider.getUserNameFromToken(refreshRequest.getRefreshToken());

		Authentication authentication = getAuthenticationForRefresh(username);

		JwtToken newTokens = jwtTokenProvider.generateToken(authentication);

		return new SignInResponse(newTokens);
	}

	private Authentication getAuthenticationForRefresh(String username) {
		User user = userRepository.findByEmail(username)
				.orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

		UserDetails userDetails = new CustomUserDetails(user);

		return new UsernamePasswordAuthenticationToken(
				userDetails,
				"",
				userDetails.getAuthorities());
	}

	@Transactional(readOnly = true)
	public UserDetailResponse getUserDetail(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		return UserDetailResponse.from(user);
	}

	public void sendPasswordResetCode(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		String title = "NEWPIN 비밀번호 재설정 인증 메일";
		String code = generateRandomCode();
		String text = "비밀번호 재설정 인증번호: " + code;

		String redisKey = "password_reset:" + email;
		redisUtil.setDataExpire(redisKey, code, EXPIRATION);

		try {
			emailService.sendEmail(email, title, text);
		} catch (Exception e) {
			log.error("Error: {}", e);
			throw new CustomException(ErrorCode.EXTERNAL_SERVICE_ERROR);
		}
	}

	@Transactional
	public void resetPassword(PasswordResetRequest request) {
		User user = userRepository.findByEmail(request.getEmail())
				.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		String redisKey = "password_reset:" + request.getEmail();
		if (!redisUtil.existData(redisKey)) {
			throw new CustomException(ErrorCode.VERIFICATION_CODE_EXPIRED);
		}

		String storedCode = redisUtil.getData(redisKey);
		if (!storedCode.equals(request.getCode())) {
			throw new CustomException(ErrorCode.VERIFICATION_CODE_MISMATCH);
		}

		String encodedPassword = passwordEncoder.encode(request.getNewPassword());
		user.updatePassword(encodedPassword);

		redisUtil.deleteData(redisKey);
	}

	private String generateRandomCode() {
		final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		final String NUMBERS = "0123456789";
		SecureRandom random = new java.security.SecureRandom();
		List<Character> chars = new java.util.ArrayList<>();

		for (int i = 0; i < 3; i++) {
			chars.add(LETTERS.charAt(random.nextInt(LETTERS.length())));
		}
		for (int i = 0; i < 3; i++) {
			chars.add(NUMBERS.charAt(random.nextInt(NUMBERS.length())));
		}

		Collections.shuffle(chars, random);

		StringBuilder sb = new StringBuilder();
		for (char c : chars) {
			sb.append(c);
		}

		return sb.toString();
	}
}
