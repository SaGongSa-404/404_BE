package com.sagongsa.backend.home;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class HomeSummaryService {
	private final HomeSummaryQueries queries;
	private final HomeBubbleCommands commands;
	public HomeSummaryService(HomeSummaryQueries queries, HomeBubbleCommands commands) {
		this.queries = queries;
		this.commands = commands;
	}
	public HomeSummaryResponse getSummary(UUID userId) { return queries.getSummary(userId); }
	public void markBubbleSeen(UUID userId) { commands.markBubbleSeen(userId); }
	public void markBubbleSeen(UUID userId, String type) { commands.markBubbleSeen(userId, type); }
}
