package com.sagongsa.backend.domain.mascot;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.enums.MascotState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mascot_profiles")
public class MascotProfile {

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MascotState mascotState = MascotState.DEFAULT;

	@Column(length = 140)
	private String lastReactionMessage;

	@Column(nullable = false)
	private Instant lastStateChangedAt;

	@Column
	private Instant reactionExpiresAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected MascotProfile() {
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (lastStateChangedAt == null) {
			lastStateChangedAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
