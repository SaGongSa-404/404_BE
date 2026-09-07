package com.sagongsa.backend.decision;

import java.util.UUID;
import org.springframework.stereotype.Service;

/** 기존 HTTP 계약을 애플리케이션 명령과 결과에 연결한다. */
@Service
public class DecisionService {
	private final DecisionCommands commands;
	private final DecisionQueries queries;

	public DecisionService(DecisionCommands commands, DecisionQueries queries) {
		this.commands = commands;
		this.queries = queries;
	}

	public DecisionResultResponse complete(UUID userId, DecisionCompleteRequest request) {
		try {
			return DecisionWebMapper.response(commands.complete(userId, DecisionWebMapper.command(request)));
		} catch (com.sagongsa.backend.domain.budget.BudgetCycleMissingException exception) {
			throw new DecisionConflictException(exception.getMessage());
		}
	}

	public DecisionResultResponse updateResult(UUID userId, UUID decisionId, DecisionResultUpdateRequest request) {
		return DecisionWebMapper.response(commands.updateResult(userId, decisionId, DecisionWebMapper.command(request)));
	}

	public DecisionResultResponse getResult(UUID userId, UUID decisionId) {
		return DecisionWebMapper.response(queries.getResult(userId, decisionId));
	}
}
