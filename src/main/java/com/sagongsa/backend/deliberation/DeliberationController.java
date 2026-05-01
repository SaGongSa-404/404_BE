package com.sagongsa.backend.deliberation;

import com.sagongsa.backend.auth.CurrentUserId;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deliberations")
public class DeliberationController {

	private final DeliberationService deliberationService;

	public DeliberationController(DeliberationService deliberationService) {
		this.deliberationService = deliberationService;
	}

	@GetMapping("/items/{itemId}")
	public DeliberationSummaryResponse getSummary(
		@CurrentUserId UUID userId,
		@PathVariable UUID itemId
	) {
		return deliberationService.getSummary(userId, itemId);
	}
}
