package com.sagongsa.backend.deliberation;

import com.sagongsa.backend.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deliberations")
@Tag(name = "Deliberation", description = "Purchase deliberation summary API")
public class DeliberationController {

	private final DeliberationService deliberationService;

	public DeliberationController(DeliberationService deliberationService) {
		this.deliberationService = deliberationService;
	}

	@GetMapping("/items/{itemId}")
	@Operation(
		summary = "Get deliberation summary",
		description = "Returns item, budget projection, similar category spend, and opportunity cost data for purchase deliberation.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Deliberation summary returned"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "Item belongs to another user"),
			@ApiResponse(responseCode = "404", description = "Item does not exist")
		}
	)
	public DeliberationSummaryResponse getSummary(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@PathVariable UUID itemId
	) {
		return deliberationService.getSummary(userId, itemId);
	}
}
