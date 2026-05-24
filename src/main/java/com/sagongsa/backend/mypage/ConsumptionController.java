package com.sagongsa.backend.mypage;

import com.sagongsa.backend.auth.CurrentUserId;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/my/consumption")
public class ConsumptionController {

	private final ConsumptionService consumptionService;

	public ConsumptionController(ConsumptionService consumptionService) {
		this.consumptionService = consumptionService;
	}

	@GetMapping
	public ResponseEntity<ConsumptionListResponse> getMonthlyConsumption(
		@CurrentUserId UUID userId,
		@RequestParam String month) {
		return ResponseEntity.ok(consumptionService.getMonthlyConsumption(userId, month));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<ConsumptionRecord> changeDecisionResult(
		@CurrentUserId UUID userId,
		@PathVariable UUID id,
		@RequestBody ChangeDecisionRequest request) {
		return ResponseEntity.ok(consumptionService.changeDecisionResult(userId, id, request.result()));
	}
}
