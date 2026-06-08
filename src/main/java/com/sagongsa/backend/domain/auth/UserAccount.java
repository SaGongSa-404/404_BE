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

	@Column
	private Instant suspendedUntil;

	@Column
	private Instant bannedAt;

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

	public Instant getSuspendedUntil() {
		return suspendedUntil;
	}

	public Instant getBannedAt() {
		return bannedAt;
	}

	public boolean canAccessAt(Instant now) {
		if (status == UserStatus.ACTIVE) {
			return true;
		}
		if (status == UserStatus.SUSPENDED) {
			return suspendedUntil != null && !suspendedUntil.isAfter(now);
		}
		return false;
	}

	public boolean activateIfSuspensionExpiredAt(Instant now) {
		if (status == UserStatus.SUSPENDED && suspendedUntil != null && !suspendedUntil.isAfter(now)) {
			this.status = UserStatus.ACTIVE;
			this.suspendedUntil = null;
			return true;
		}
		return false;
	}

	public void suspendUntil(Instant suspendedUntil) {
		this.status = UserStatus.SUSPENDED;
		this.suspendedUntil = suspendedUntil;
		this.bannedAt = null;
		this.withdrawnAt = null;
	}

	public void banPermanently() {
		this.status = UserStatus.BANNED;
		this.bannedAt = Instant.now();
		this.suspendedUntil = null;
		this.withdrawnAt = null;
	}

	public void withdraw() {
		this.status = UserStatus.WITHDRAWN;
		this.withdrawnAt = Instant.now();
		this.suspendedUntil = null;
		this.bannedAt = null;
	}
}
