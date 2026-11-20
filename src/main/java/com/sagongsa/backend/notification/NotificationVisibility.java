package com.sagongsa.backend.notification;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class NotificationVisibility {

	private NotificationVisibility() {
	}

	public static OffsetDateTime cutoff(Instant now) {
		return now.atOffset(ZoneOffset.UTC).minusMonths(1);
	}
}
