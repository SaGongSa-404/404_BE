package com.sagongsa.backend.home;

import com.sagongsa.backend.auth.CurrentUserId;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
public class HomeSummaryController {

	private final HomeSummaryService homeSummaryService;

	public HomeSummaryController(HomeSummaryService homeSummaryService) {
		this.homeSummaryService = homeSummaryService;
	}

	@GetMapping("/summary")
	public HomeSummaryResponse getSummary(@CurrentUserId UUID userId) {
		return homeSummaryService.getSummary(userId);
	}
}
