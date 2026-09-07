package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.auth.SocialAccount;
import com.sagongsa.backend.domain.auth.SocialAccountRepository;
import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.ItemStatus;
import com.sagongsa.backend.domain.enums.ModerationStatus;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.user.UserProfile;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import com.sagongsa.backend.social.PostListResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class MypageService {
	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final FeedPostRepository feedPostRepository;
	private final ProfileCommands profileCommands;
	private final AccountWithdrawalService withdrawal;
	private final MypageStatsQueries statsQueries;
	private final MypageSocialQueries socialQueries;

	MypageService(UserAccountRepository userAccountRepository, UserProfileRepository userProfileRepository, SocialAccountRepository socialAccountRepository, FeedPostRepository feedPostRepository, ProfileCommands profileCommands, AccountWithdrawalService withdrawal, MypageStatsQueries statsQueries, MypageSocialQueries socialQueries) {
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.socialAccountRepository = socialAccountRepository;
		this.feedPostRepository = feedPostRepository;
		this.profileCommands = profileCommands;
		this.withdrawal = withdrawal;
		this.statsQueries = statsQueries;
		this.socialQueries = socialQueries;
	}

	MyProfileResponse getMyProfile(UUID userId) {
		UserAccount user = findUserOrThrow(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
		SocialAccount social = socialAccountRepository.findByUserId(userId).orElse(null);
		long postCount = feedPostRepository.countByUserIdAndDeletedAtIsNullAndModerationStatus(userId, ModerationStatus.ACTIVE);
		return MyProfileResponse.of(user, profile, social, postCount);
	}

	@Transactional
	MyProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
		return profileCommands.updateProfile(userId, request);
	}

	record BudgetUpdateResponse(Integer monthlyBudget) {}

	@Transactional
	BudgetUpdateResponse updateBudget(UUID userId, UpdateBudgetRequest request) {
		return profileCommands.updateBudget(userId, request);
	}

	record NotificationSettingsResponse(boolean notificationEnabled) {}

	NotificationSettingsResponse getNotificationSettings(UUID userId) {
		findUserOrThrow(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
		return new NotificationSettingsResponse(profile == null || profile.isNotificationEnabled());
	}

	@Transactional
	NotificationSettingsResponse updateNotificationSettings(UUID userId, NotificationSettingsRequest request) {
		return profileCommands.updateNotificationSettings(userId, request);
	}

	@Transactional
	void deleteAccount(UUID userId) {
		withdrawal.deleteAccount(userId);
	}

	@Transactional
	AvailableMonthsResponse getAvailableMonths(UUID userId) {
		return statsQueries.getAvailableMonths(userId);
	}

	@Transactional
	StatsResponse getStats(UUID userId, String yearMonth) {
		return statsQueries.getStats(userId, yearMonth);
	}

	WishHistoryResponse getWishHistory(UUID userId, ItemStatus status, String yearMonth, int page, int size) {
		return statsQueries.getWishHistory(userId, status, yearMonth, page, size);
	}

	PostListResponse getMyPosts(UUID userId, Instant cursor, int size) {
		return socialQueries.getMyPosts(userId, cursor, size);
	}

	PostListResponse getMyVotedPosts(UUID userId, Instant cursor, int size) {
		return socialQueries.getMyVotedPosts(userId, cursor, size);
	}

	private UserAccount findUserOrThrow(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new MypageNotFoundException("사용자를 찾을 수 없습니다."));
	}

}
