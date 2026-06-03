package com.sagongsa.backend.domain.common;

import com.sagongsa.backend.domain.auth.UserAccount;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class UserScopedEntity extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	protected UserScopedEntity() {
	}

	protected UserScopedEntity(UserAccount user) {
		this.user = user;
	}

	protected void assignUser(UserAccount user) {
		this.user = user;
	}

	public UserAccount getUser() {
		return user;
	}
}
