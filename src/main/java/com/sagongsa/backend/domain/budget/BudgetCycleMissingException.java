package com.sagongsa.backend.domain.budget;

public class BudgetCycleMissingException extends RuntimeException {
	public BudgetCycleMissingException() {
		super("Current budget cycle was not found.");
	}
}
