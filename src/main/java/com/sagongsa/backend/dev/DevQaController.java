package com.sagongsa.backend.dev;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/qa")
@Profile("!prod")
public class DevQaController {

	private final QaScenarioService qaScenarioService;

	public DevQaController(QaScenarioService qaScenarioService) {
		this.qaScenarioService = qaScenarioService;
	}

	@PostMapping("/users")
	public ResponseEntity<QaUserScenarioResponse> createQaUser() {
		return ResponseEntity.ok(qaScenarioService.createQaUser());
	}

	@PostMapping("/scenarios/basic")
	public ResponseEntity<QaBasicScenarioResponse> createBasicScenario() {
		return ResponseEntity.ok(qaScenarioService.createBasicScenario());
	}

	@DeleteMapping("/users/{userId}")
	public ResponseEntity<Void> deleteQaUser(@PathVariable UUID userId) {
		qaScenarioService.deleteQaUser(userId);
		return ResponseEntity.noContent().build();
	}
}
