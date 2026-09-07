package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.budget.BudgetCycleRepository;
import com.sagongsa.backend.domain.item.SavedItemRepository;
import com.sagongsa.backend.domain.notification.DevicePushTokenRepository;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import com.sagongsa.backend.domain.social.PostVoteRepository;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@Transactional(readOnly = true)
class AccountWithdrawalService {
	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final FeedPostRepository feedPostRepository;
	private final PostVoteRepository postVoteRepository;
	private final PostCommentRepository postCommentRepository;
	private final SavedItemRepository savedItemRepository;
	private final BudgetCycleRepository budgetCycleRepository;
	private final DevicePushTokenRepository devicePushTokenRepository;
	private final JdbcTemplate jdbcTemplate;

	AccountWithdrawalService(UserAccountRepository userAccountRepository, UserProfileRepository userProfileRepository, FeedPostRepository feedPostRepository, PostVoteRepository postVoteRepository, PostCommentRepository postCommentRepository, SavedItemRepository savedItemRepository, BudgetCycleRepository budgetCycleRepository, DevicePushTokenRepository devicePushTokenRepository, JdbcTemplate jdbcTemplate) {
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.feedPostRepository = feedPostRepository;
		this.postVoteRepository = postVoteRepository;
		this.postCommentRepository = postCommentRepository;
		this.savedItemRepository = savedItemRepository;
		this.budgetCycleRepository = budgetCycleRepository;
		this.devicePushTokenRepository = devicePushTokenRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	void deleteAccount(UUID userId) {
		UserAccount user = findUserOrThrow(userId);

		// Social: votes/comments by user and on user's posts
		postVoteRepository.deleteByUserId(userId);
		postCommentRepository.deleteByUserId(userId);
		postVoteRepository.deleteByPostUserId(userId);
		postCommentRepository.deleteByPostUserId(userId);
		feedPostRepository.softDeleteByUserId(userId);
		// Unlink feed posts from saved items (item_id is nullable)
		jdbcTemplate.update("UPDATE feed_posts SET item_id = NULL, decision_id = NULL WHERE user_id = ?", userId);

		// Decision history — delete in FK dependency order before saved_items
		jdbcTemplate.update("""
			DELETE FROM self_check_answers WHERE response_set_id IN (
				SELECT id FROM self_check_response_sets
				WHERE decision_id IN (SELECT id FROM purchase_decisions WHERE user_id = ?))""", userId);
		jdbcTemplate.update("DELETE FROM self_check_response_sets WHERE decision_id IN (SELECT id FROM purchase_decisions WHERE user_id = ?)", userId);
		jdbcTemplate.update("DELETE FROM purchase_decision_change_logs WHERE decision_id IN (SELECT id FROM purchase_decisions WHERE user_id = ?)", userId);
		jdbcTemplate.update("DELETE FROM purchase_reflections WHERE decision_id IN (SELECT id FROM purchase_decisions WHERE user_id = ?)", userId);
		jdbcTemplate.update("DELETE FROM mascot_state_events WHERE user_id = ?", userId);
		jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
		jdbcTemplate.update("DELETE FROM reminder_schedules WHERE user_id = ?", userId);
		jdbcTemplate.update("DELETE FROM purchase_decisions WHERE user_id = ?", userId);
		jdbcTemplate.update("DELETE FROM item_source_metadata WHERE item_id IN (SELECT id FROM saved_items WHERE user_id = ?)", userId);

		devicePushTokenRepository.deleteByUserId(userId);
		savedItemRepository.deleteByUserId(userId);
		budgetCycleRepository.deleteByUserId(userId);
		userProfileRepository.deleteByUserId(userId);
		user.withdraw();
	}

	private UserAccount findUserOrThrow(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new MypageNotFoundException("사용자를 찾을 수 없습니다."));
	}
}
