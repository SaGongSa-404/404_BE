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

	@PostMapping("/scenarios/budget-zero")
	public ResponseEntity<QaUserScenarioResponse> createBudgetZeroScenario() {
		return ResponseEntity.ok(qaScenarioService.createBudgetZeroScenario());
	}

	@PostMapping("/scenarios/result-combinations")
	public ResponseEntity<QaDecisionScenarioResponse> createResultCombinationsScenario() {
		return ResponseEntity.ok(qaScenarioService.createResultCombinationsScenario());
	}

	@PostMapping("/scenarios/regret-notification-ready")
	public ResponseEntity<QaRegretReminderScenarioResponse> createRegretNotificationReadyScenario() {
		return ResponseEntity.ok(qaScenarioService.createRegretNotificationReadyScenario());
	}

	@PostMapping("/scenarios/feed-ready")
	public ResponseEntity<QaFeedScenarioResponse> createFeedReadyScenario() {
		return ResponseEntity.ok(qaScenarioService.createFeedReadyScenario());
	}

	@PostMapping("/scenarios/mypage-consumption")
	public ResponseEntity<QaDecisionScenarioResponse> createMypageConsumptionScenario() {
		return ResponseEntity.ok(qaScenarioService.createMypageConsumptionScenario());
	}

	@PostMapping("/reminders/{reminderId}/process")
	public ResponseEntity<QaReminderProcessResponse> processDueReminder(@PathVariable UUID reminderId) {
		return ResponseEntity.ok(qaScenarioService.processDueReminder(reminderId));
	}

	@DeleteMapping("/users/{userId}")
	public ResponseEntity<Void> deleteQaUser(@PathVariable UUID userId) {
		qaScenarioService.deleteQaUser(userId);
		return ResponseEntity.noContent().build();
	}
}
