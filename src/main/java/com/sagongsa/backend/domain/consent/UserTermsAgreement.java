package com.sagongsa.backend.domain.consent;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "user_terms_agreements",
	indexes = {
		@Index(name = "idx_user_terms_agreements_user_agreed", columnList = "user_id,agreed_at")
	},
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_user_terms_agreements_user_version", columnNames = {"user_id", "terms_version_id"})
	}
)
public class UserTermsAgreement extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "terms_version_id", nullable = false)
	private TermsVersion termsVersion;

	@Column(nullable = false)
	private Instant agreedAt;

	@Column
	private Instant revokedAt;

	protected UserTermsAgreement() {
	}
}
