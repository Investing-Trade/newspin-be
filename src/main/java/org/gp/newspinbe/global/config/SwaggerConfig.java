package org.gp.newspinbe.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI(Swagger) 메타데이터. bearer 스킴만 등록하고, 전역 SecurityRequirement 는 걸지 않는다
 * — sign-up/sign-in 등 인증 없이 호출하는 엔드포인트까지 "Authorize" 가 필요한 것처럼 보였던 문제(개선)를
 * 막기 위해, 인증이 실제로 필요한 컨트롤러/메서드에만 {@code @SecurityRequirement(name = "bearer")} 를 붙인다.
 */
@Configuration
public class SwaggerConfig {

	@Value("${server.url}")
	private String serverUrl;

	@Bean
	public OpenAPI openAPI(){
		Server server = new Server();
		server.setUrl(serverUrl);

		SecurityScheme securityScheme = new SecurityScheme()
			.type(SecurityScheme.Type.HTTP)
			.scheme("bearer")
			.bearerFormat("JWT");

		return new OpenAPI()
			.components(new Components().addSecuritySchemes("bearer", securityScheme))
			.info(apiInfo())
			.addServersItem(server);
	}

	private Info apiInfo(){
		return new Info()
			.title("NewsPin API")
			.description("뉴스 감정 판단 학습 + 모의투자 시뮬레이션 백엔드 API. "
					+ "인증이 필요한 엔드포인트는 우측 상단 Authorize 에 access token 을 입력한다 (Bearer, `Authorization` 헤더 아님 — 스킴이 값 앞에 자동으로 붙는다).")
			.version("1.0");
	}
}
