package org.gp.newspinbe.global.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.gp.newspinbe.global.security.jwt.JwtAuthenticationFilter;
import org.gp.newspinbe.global.security.jwt.JwtExceptionFilter;
import org.gp.newspinbe.global.security.jwt.JwtTokenProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtTokenProvider jwtTokenProvider;

	private static final String[] PERMIT_ALL_PATTERNS = {
		"/user/sign-up",
		"/user/sign-in",
		"/user/refresh",
		"/swagger-ui/**",
		"/v3/api-docs/**"
	};

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity httpSecurity ) throws Exception {
		httpSecurity
			.httpBasic(AbstractHttpConfigurer::disable)
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(authorize ->
				authorize
					.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
					.requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
					.requestMatchers(HttpMethod.DELETE, "/user").hasRole("ADMIN")
					.anyRequest().authenticated()
			)
			.cors(configurer -> configurer.configurationSource(corsConfigurationSource()));


		httpSecurity
			.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
			.addFilterBefore(new JwtExceptionFilter(), UsernamePasswordAuthenticationFilter.class);

		return httpSecurity.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration corsConfiguration = new CorsConfiguration();
		corsConfiguration.addAllowedHeader("*");
		corsConfiguration.setAllowedMethods(Collections.singletonList("*"));
		corsConfiguration.setAllowCredentials(true);
		corsConfiguration.setAllowedOrigins(List.of(
			"http://localhost:5173",
			"http://127.0.0.1:5173"
		));

		corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", corsConfiguration);
		return source;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}
}