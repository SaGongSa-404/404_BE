package com.sagongsa.backend.terms;

record TermsSummaryResponse(
	TermsType type,
	String title,
	boolean required
) {}
