package com.sagongsa.backend.domain.auth;

import com.sagongsa.backend.domain.common.BaseEntity;
import com.sagongsa.backend.domain.enums.SocialProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "social_accounts",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_social_accounts_provider_user", columnNames = {"provider", "provider_user_id"}),
		@UniqueConstraint(name = "uk_social_accounts_user_id", columnNames = "user_id")
	}
)
public class SocialAccount extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SocialProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 120)
	private String providerUserId;

	@Column(length = 255)
	private String email;

	@Column(columnDefinition = "text")
	private String profileImageUrl;

	protected SocialAccount() {
	}

	public UserAccount getUser() {
		return user;
	}

	public SocialProvider getProvider() {
		return provider;
	}

	public String getProviderUserId() {
		return providerUserId;
	}

	public String getEmail() {
		return email;
	}

	public String getProfileImageUrl() {
		return profileImageUrl;
	}
}
