package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.SocialAccount;
import com.sagongsa.backend.domain.enums.OnboardingStatus;
import com.sagongsa.backend.domain.enums.UserStatus;
import com.sagongsa.backend.domain.user.UserProfile;
import java.time.Instant;
import java.util.UUID;

record MyProfileResponse(
	UUID id,
	String nickname,
	String mascotName,
	String profileImageUrl,
	String provider,
	UserStatus status,
	OnboardingStatus onboardingStatus,
	long postCount,
	Instant createdAt
) {
	static MyProfileResponse of(UserAccount user, UserProfile profile, SocialAccount social, long postCount) {
		return new MyProfileResponse(
			user.getId(),
			profile != null ? profile.getNickname() : null,
			profile != null ? profile.getMascotName() : null,
			profile != null ? profile.getProfileImageUrl() : (social != null ? social.getProfileImageUrl() : null),
			social != null ? social.getProvider().name().toLowerCase() : null,
			user.getStatus(),
			user.getOnboardingStatus(),
			postCount,
			user.getCreatedAt()
		);
	}
}
