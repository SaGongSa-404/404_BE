package com.sagongsa.backend.notification;

final class NotificationMessages {

	private static final int PRODUCT_NAME_LIMIT = 15;
	private static final String DEFAULT_PRODUCT_NAME = "위시템";

	private NotificationMessages() {
	}

	static String regretCheckReadyBody(String productName) {
		return "'%s' 구매한 지 일주일이 지났어요. 만족스러우신가요?".formatted(shortProductName(productName));
	}

	static String regretCheckFollowUpBody(String productName) {
		return "'%s' 아직 확인 안 하셨어요!".formatted(shortProductName(productName));
	}

	private static String shortProductName(String productName) {
		String normalized = productName == null || productName.isBlank()
			? DEFAULT_PRODUCT_NAME
			: productName.trim();
		if (normalized.length() <= PRODUCT_NAME_LIMIT) {
			return normalized;
		}
		return normalized.substring(0, PRODUCT_NAME_LIMIT) + "...";
	}
}
