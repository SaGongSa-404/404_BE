package com.sagongsa.backend.domain.auth;

import com.sagongsa.backend.domain.common.BaseEntity;
import com.sagongsa.backend.domain.enums.OnboardingStatus;
import com.sagongsa.backend.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status = UserStatus.ACTIVE;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OnboardingStatus onboardingStatus = OnboardingStatus.NOT_STARTED;

	@Column
	private Instant withdrawnAt;

	protected UserAccount() {
	}

	public static UserAccount create() {
		return new UserAccount();
	}

	public UserStatus getStatus() {
		return status;
	}

	public OnboardingStatus getOnboardingStatus() {
		return onboardingStatus;
	}

	public Instant getWithdrawnAt() {
		return withdrawnAt;
	}

	public void withdraw() {
		this.status = UserStatus.WITHDRAWN;
		this.withdrawnAt = Instant.now();
	}
}
