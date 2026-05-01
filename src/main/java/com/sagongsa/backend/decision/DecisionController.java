package com.sagongsa.backend.decision;

import com.sagongsa.backend.auth.CurrentUserId;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {

	private final DecisionService decisionService;

	public DecisionController(DecisionService decisionService) {
		this.decisionService = decisionService;
	}

	@PostMapping
	public ResponseEntity<DecisionResultResponse> complete(
		@CurrentUserId UUID userId,
		@RequestBody(required = false) DecisionCompleteRequest request
	) {
		DecisionResultResponse response = decisionService.complete(userId, request);
		return ResponseEntity
			.created(URI.create("/api/v1/decisions/" + response.decisionId() + "/result"))
			.body(response);
	}

	@GetMapping("/{decisionId}/result")
	public DecisionResultResponse getResult(
		@CurrentUserId UUID userId,
		@PathVariable UUID decisionId
	) {
		return decisionService.getResult(userId, decisionId);
	}

	@PatchMapping("/{decisionId}/result")
	public DecisionResultResponse updateResult(
		@CurrentUserId UUID userId,
		@PathVariable UUID decisionId,
		@RequestBody(required = false) DecisionResultUpdateRequest request
	) {
		return decisionService.updateResult(userId, decisionId, request);
	}
}
