package org.gp.newspinbe.global.security.jwt;

import java.security.Key;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.Collection;

import org.gp.newspinbe.global.util.RedisUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtTokenProvider {

	private final RedisUtil redisUtil;
	private final Key key;
	private final UserDetailsService userDetailsService;

	private static final String GRANT_TYPE = "Bearer";

	@Value("${spring.jwt.access-token.expire-time}")
	private long ACCESS_TOKEN_EXPIRE_TIME;

	@Value("${spring.jwt.refresh-token.expire-time}")
	private long REFRESH_TOKEN_EXPIRE_TIME;

	public JwtTokenProvider(
			@Value("${spring.jwt.secret}") String secretKey,
			RedisUtil redisUtil,
			UserDetailsService userDetailsService) {
		this.redisUtil = redisUtil;
		this.userDetailsService = userDetailsService;
		byte[] keyBytes = Decoders.BASE64.decode(secretKey);
		this.key = Keys.hmacShaKeyFor(keyBytes);
	}

	public JwtToken generateToken(Authentication authentication) {
		String authorities = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.joining(","));

		long now = (new Date()).getTime();
		String username = authentication.getName();

		// AccessToken 생성
		Date accessTokenExpire = new Date(now + ACCESS_TOKEN_EXPIRE_TIME);
		String accessToken = generateAccessToken(username, authorities, accessTokenExpire);

		// RefreshToken 생성
		Date refreshTokenExpire = new Date(now + REFRESH_TOKEN_EXPIRE_TIME);
		String refreshToken = generateRefreshToken(username, refreshTokenExpire);

		// Redis에 RefreshToken 넣기
		redisUtil.setDataExpire(username, refreshToken, REFRESH_TOKEN_EXPIRE_TIME);

		return JwtToken.builder()
				.grantType(GRANT_TYPE)
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.build();
	}

	private String generateAccessToken(String username, String authorities, Date expireDate) {
		return Jwts.builder()
				.setSubject(username)
				.claim("auth", authorities)
				.setExpiration(expireDate)
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();
	}

	private String generateRefreshToken(String username, Date expireDate) {
		return Jwts.builder()
				.setSubject(username)
				.setExpiration(expireDate)
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();
	}

	public Authentication getAuthentication(String accessToken) {
		Claims claims = parseClaims(accessToken);
		if (claims.get("auth") == null) {
			throw new RuntimeException("권한 정보가 없는 토큰입니다.");
		}

		Collection<? extends GrantedAuthority> authorities = Arrays.stream(claims.get("auth").toString().split(","))
				.map(SimpleGrantedAuthority::new)
				.toList();

		String username = claims.getSubject();
		UserDetails userDetails = userDetailsService.loadUserByUsername(username);

		return new UsernamePasswordAuthenticationToken(userDetails, "", authorities);
	}

	private Claims parseClaims(String accessToken) {
		try {
			return Jwts.parserBuilder()
					.setSigningKey(key)
					.build()
					.parseClaimsJws(accessToken)
					.getBody();
		} catch (ExpiredJwtException e) {
			return e.getClaims();
		}
	}

	public boolean validateToken(String token) {
		try {
			Jwts.parserBuilder()
					.setSigningKey(key)
					.build()
					.parseClaimsJws(token);

			return true;
		} catch (SecurityException | MalformedJwtException e) {
			log.info("Invalid JWT Token", e);
		} catch (ExpiredJwtException e) {
			log.info("Expired JWT Token", e);
		} catch (UnsupportedJwtException e) {
			log.info("Unsupported JWT Token", e);
		} catch (IllegalArgumentException e) {
			log.info("JWT claims string is empty", e);
		}
		return false;
	}

	public boolean validateRefreshToken(String token) {
		if (!validateToken(token))
			return false;

		try {
			String username = getUserNameFromToken(token);
			String redisToken = redisUtil.getData(username);
			return token.equals(redisToken);
		} catch (Exception e) {
			log.info("RefreshToken Validation Failed", e);
			return false;
		}
	}

	public String getUserNameFromToken(String token) {
		try {
			Claims claims = Jwts.parserBuilder()
					.setSigningKey(key)
					.build()
					.parseClaimsJws(token)
					.getBody();

			return claims.getSubject();
		} catch (ExpiredJwtException e) {
			return e.getClaims().getSubject();
		}
	}

	public void deleteRefreshToken(String username) {
		if (username == null || username.trim().isEmpty()) {
			throw new IllegalArgumentException("Username cannot be null or empty");
		}

		redisUtil.deleteData(username);
	}

}
