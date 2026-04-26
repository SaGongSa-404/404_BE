package com.sagongsa.backend.domain.consent;

import com.sagongsa.backend.domain.common.UserScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
	name = "marketing_consent_histories",
	indexes = {
		@Index(name = "idx_marketing_consent_histories_user_changed", columnList = "user_id,changed_at")
	}
)
public class MarketingConsentHistory extends UserScopedEntity {

	@Column(nullable = false)
	private boolean consented;

	@Column(nullable = false)
	private Instant changedAt;

	@Column(length = 40)
	private String source;

	@Column(columnDefinition = "text")
	private String note;

	protected MarketingConsentHistory() {
	}
}
