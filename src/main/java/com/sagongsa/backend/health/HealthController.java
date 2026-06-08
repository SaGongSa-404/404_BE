package com.sagongsa.backend.health;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	private final String serviceName;
	private final Environment environment;

	public HealthController(
		@Value("${spring.application.name:wigul-backend}") String serviceName,
		Environment environment
	) {
		this.serviceName = serviceName;
		this.environment = environment;
	}

	@GetMapping({"/health", "/api/health"})
	public ResponseEntity<HealthResponse> health() {
		return ResponseEntity.ok(new HealthResponse(
			"UP",
			serviceName,
			activeProfiles(),
			OffsetDateTime.now(ZoneOffset.UTC)
		));
	}

	private List<String> activeProfiles() {
		return Arrays.stream(environment.getActiveProfiles())
			.sorted()
			.toList();
	}

	public record HealthResponse(
		String status,
		String service,
		List<String> profiles,
		OffsetDateTime time
	) {
	}
}
