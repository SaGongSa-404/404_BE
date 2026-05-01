package com.sagongsa.backend.domain.auth;

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
	name = "refresh_tokens",
	indexes = {
		@Index(name = "idx_refresh_tokens_user_created_at", columnList = "user_id,created_at")
	},
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = "token_hash")
	}
)
public class RefreshToken extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "token_hash", nullable = false, length = 255)
	private String tokenHash;

	@Column(length = 120)
	private String deviceId;

	@Column(length = 120)
	private String deviceName;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column
	private Instant revokedAt;

	@Column
	private Instant lastUsedAt;

	protected RefreshToken() {
	}
}
