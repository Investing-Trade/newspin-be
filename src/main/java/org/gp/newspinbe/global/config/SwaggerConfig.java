package org.gp.newspinbe.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

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

		SecurityRequirement securityRequirement = new SecurityRequirement().addList("bearer");

		return new OpenAPI()
			.components(new Components().addSecuritySchemes("bearer", securityScheme))
			.info(apiInfo())
			.addServersItem(server)
			.addSecurityItem(securityRequirement);
	}

	private Info apiInfo(){
		return new Info()
			.title("Newspin Swagger")
			.description("API Documentation")
			.version("1.0");
	}
}
