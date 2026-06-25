package com.sagongsa.backend.auth;

import java.util.UUID;

final class AppReviewerAccount {

	static final UUID USER_ID = UUID.fromString("40400000-0000-0000-0000-000000000055");
	static final String PROVIDER = "kakao";
	static final String PROVIDER_DB_VALUE = "KAKAO";
	static final String PROVIDER_USER_ID = "app-reviewer-fixed";
	static final String NAME = "앱 심사 계정";
	static final String EMAIL = "app-reviewer@sagongsa.dev";

	private AppReviewerAccount() {
	}
}
