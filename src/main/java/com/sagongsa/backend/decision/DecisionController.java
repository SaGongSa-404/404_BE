package com.sagongsa.backend.decision;

import com.sagongsa.backend.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Decision", description = "Purchase decision result APIs")
public class DecisionController {

	private final DecisionService decisionService;

	public DecisionController(DecisionService decisionService) {
		this.decisionService = decisionService;
	}

	@PostMapping
	@Operation(
		summary = "Complete purchase decision",
		description = "Stores GO or STOP decision result, self-check answers, budget impact, mascot reaction, and reminder data.",
		responses = {
			@ApiResponse(responseCode = "201", description = "Decision completed"),
			@ApiResponse(responseCode = "400", description = "Invalid decision payload"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "Item belongs to another user"),
			@ApiResponse(responseCode = "404", description = "Item does not exist"),
			@ApiResponse(responseCode = "409", description = "Decision already exists")
		}
	)
	public ResponseEntity<DecisionResultResponse> complete(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@RequestBody(required = false) DecisionCompleteRequest request
	) {
		DecisionResultResponse response = decisionService.complete(userId, request);
		return ResponseEntity
			.created(URI.create("/api/v1/decisions/" + response.decisionId() + "/result"))
			.body(response);
	}

	@GetMapping("/{decisionId}/result")
	@Operation(
		summary = "Get purchase decision result",
		description = "Returns a previously completed purchase decision result screen payload.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Decision result returned"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "Decision belongs to another user"),
			@ApiResponse(responseCode = "404", description = "Decision does not exist")
		}
	)
	public DecisionResultResponse getResult(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@PathVariable UUID decisionId
	) {
		return decisionService.getResult(userId, decisionId);
	}

	@PatchMapping("/{decisionId}/result")
	@Operation(
		summary = "Update purchase decision result",
		description = "Updates GO or STOP result and self-check answers for an existing decision.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Decision result updated"),
			@ApiResponse(responseCode = "400", description = "Invalid decision update payload"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "Decision belongs to another user"),
			@ApiResponse(responseCode = "404", description = "Decision does not exist")
		}
	)
	public DecisionResultResponse updateResult(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@PathVariable UUID decisionId,
		@RequestBody(required = false) DecisionResultUpdateRequest request
	) {
		return decisionService.updateResult(userId, decisionId, request);
	}
}
