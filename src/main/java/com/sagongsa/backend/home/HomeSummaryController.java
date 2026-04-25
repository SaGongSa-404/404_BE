package com.sagongsa.backend.home;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
	public HomeSummaryResponse getSummary(@RequestHeader("X-User-Id") String userIdHeader) {
		return homeSummaryService.getSummary(parseUserId(userIdHeader));
	}

	private UUID parseUserId(String userIdHeader) {
		try {
			return UUID.fromString(userIdHeader);
		} catch (IllegalArgumentException exception) {
			throw new InvalidHomeUserIdHeaderException(userIdHeader);
		}
	}
}
