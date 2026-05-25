package com.sagongsa.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
@OpenAPIDefinition(
	info = @Info(
		title = "404 Backend API",
		version = "1.0.0",
		description = "Frontend integration API contract for auth, onboarding, item import, wishlist, deliberation, decision, home, notification, and reflection flows.",
		license = @License(name = "Internal")
	),
	servers = {
		@Server(url = "/", description = "Current host")
	}
)
@SecurityScheme(
	name = "bearerAuth",
	type = SecuritySchemeType.HTTP,
	scheme = "bearer",
	bearerFormat = "JWT"
)
public class OpenApiConfig {

	@Bean
	GroupedOpenApi frontendApi() {
		return GroupedOpenApi.builder()
			.group("frontend-api")
			.pathsToMatch("/api/**")
			.build();
	}

	@Bean
	OpenApiCustomizer bearerAuthCustomizer() {
		return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
			if ("/api/auth/token/refresh".equals(path)) {
				return;
			}

			pathItem.readOperations().forEach(operation -> operation.addSecurityItem(
				new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearerAuth")
			));
		});
	}
}
