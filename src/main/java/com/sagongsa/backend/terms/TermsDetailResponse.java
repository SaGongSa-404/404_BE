package com.sagongsa.backend.terms;

record TermsDetailResponse(
	TermsType type,
	String title,
	boolean required,
	String content
) {}
