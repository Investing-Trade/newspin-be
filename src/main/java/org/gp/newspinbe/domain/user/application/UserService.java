package org.gp.newspinbe.domain.user.application;

import lombok.RequiredArgsConstructor;

import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.dto.request.RefreshRequest;
import org.gp.newspinbe.domain.user.dto.request.SignInRequest;
import org.gp.newspinbe.domain.user.dto.request.SignUpRequest;
import org.gp.newspinbe.domain.user.dto.response.SignInResponse;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.gp.newspinbe.global.security.CustomUserDetails;
import org.gp.newspinbe.global.security.jwt.JwtToken;
import org.gp.newspinbe.global.security.jwt.JwtTokenProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final AuthenticationManagerBuilder authenticationManagerBuilder;

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
		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(signInRequest.getEmail(), signInRequest.getPassword());

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
			userDetails.getAuthorities()
		);
	}
}
