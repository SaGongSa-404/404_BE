package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.auth.SocialAccount;
import com.sagongsa.backend.domain.auth.SocialAccountRepository;
import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.budget.BudgetCycle;
import com.sagongsa.backend.domain.budget.BudgetCycleRepository;
import com.sagongsa.backend.domain.user.UserProfile;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import com.sagongsa.backend.social.SocialActivityQueries;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ProfileCommands {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final SocialActivityQueries socialQueries;
	private final BudgetCycleRepository budgetCycleRepository;

	ProfileCommands(
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		SocialAccountRepository socialAccountRepository,
		SocialActivityQueries socialQueries,
		BudgetCycleRepository budgetCycleRepository
	) {
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.socialAccountRepository = socialAccountRepository;
		this.socialQueries = socialQueries;
		this.budgetCycleRepository = budgetCycleRepository;
	}

	@Transactional
	MyProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
		UserAccount user = findUserOrThrow(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId)
			.orElseGet(() -> userProfileRepository.save(
				UserProfile.create(user,
					request.nickname() != null ? request.nickname() : "사용자",
					request.raccoonName() != null ? request.raccoonName() : "너구리")));
		profile.updateProfile(request.nickname(), request.raccoonName());
		SocialAccount social = socialAccountRepository.findByUserId(userId).orElse(null);
		long postCount = socialQueries.countMyPosts(userId);
		return MyProfileResponse.of(user, profile, social, postCount);
	}

	@Transactional
	BudgetUpdateResponse updateBudget(UUID userId, UpdateBudgetRequest request) {
		UserAccount user = findUserOrThrow(userId);
		String yearMonth = YearMonth.now(KST).toString();
		BudgetCycle cycle = budgetCycleRepository.findByUserIdAndYearMonth(userId, yearMonth)
			.orElseGet(() -> budgetCycleRepository.save(
				BudgetCycle.create(user, yearMonth, request.monthlyBudget())));
		cycle.updateBudgetAmount(request.monthlyBudget());
		return new BudgetUpdateResponse(request.monthlyBudget());
	}

	@Transactional
	NotificationSettingsResponse updateNotificationSettings(UUID userId, NotificationSettingsRequest request) {
		UserAccount user = findUserOrThrow(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId)
			.orElseGet(() -> userProfileRepository.save(
				UserProfile.create(user, "사용자", "너구리")));
		profile.updateNotificationEnabled(request.notificationEnabled());
		return new NotificationSettingsResponse(request.notificationEnabled());
	}

	private UserAccount findUserOrThrow(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new MypageNotFoundException("사용자를 찾을 수 없습니다."));
	}
}
