package com.sagongsa.backend.domain.user;

import com.sagongsa.backend.domain.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "user_profiles")
public class UserProfile {

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(nullable = false, length = 40)
	private String nickname;

	@Column(nullable = false, length = 40)
	private String mascotName;

	@Column(nullable = false, length = 40)
	private String timezone;

	@Column(columnDefinition = "text")
	private String profileImageUrl;

	@Column(nullable = false)
	private boolean notificationEnabled = true;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected UserProfile() {
	}

	public static UserProfile create(UserAccount user, String nickname, String mascotName) {
		UserProfile p = new UserProfile();
		p.user = user;
		p.nickname = nickname;
		p.mascotName = mascotName != null ? mascotName : "너구리";
		p.timezone = "Asia/Seoul";
		p.notificationEnabled = true;
		return p;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
		if (timezone == null || timezone.isBlank()) {
			timezone = "Asia/Seoul";
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public UUID getId() { return id; }
	public UserAccount getUser() { return user; }
	public String getNickname() { return nickname; }
	public String getMascotName() { return mascotName; }
	public String getTimezone() { return timezone; }
	public String getProfileImageUrl() { return profileImageUrl; }
	public boolean isNotificationEnabled() { return notificationEnabled; }
	public Instant getCreatedAt() { return createdAt; }

	public void updateProfile(String nickname, String mascotName) {
		if (nickname != null) this.nickname = nickname;
		if (mascotName != null) this.mascotName = mascotName;
	}

	public void updateNotificationEnabled(boolean enabled) {
		this.notificationEnabled = enabled;
	}
}
