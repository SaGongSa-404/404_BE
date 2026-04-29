package com.example._04_backend.domain.user.service;

import com.example._04_backend.domain.social.dto.response.PostListResponse;
import com.example._04_backend.domain.social.dto.response.PostResponse;
import com.example._04_backend.domain.social.entity.FeedPost;
import com.example._04_backend.domain.social.entity.PostVote;
import com.example._04_backend.domain.social.enums.PostVoteType;
import com.example._04_backend.domain.social.repository.FeedPostRepository;
import com.example._04_backend.domain.social.repository.PostCommentRepository;
import com.example._04_backend.domain.social.repository.PostVoteRepository;
import com.example._04_backend.domain.user.dto.request.NotificationSettingsRequest;
import com.example._04_backend.domain.user.dto.request.UpdateBudgetRequest;
import com.example._04_backend.domain.user.dto.request.UpdateProfileRequest;
import com.example._04_backend.domain.user.dto.response.*;
import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.user.entity.UserProfile;
import com.example._04_backend.domain.user.repository.UserProfileRepository;
import com.example._04_backend.domain.user.repository.UserRepository;
import com.example._04_backend.domain.wish.entity.BudgetCycle;
import com.example._04_backend.domain.wish.entity.SavedItem;
import com.example._04_backend.domain.wish.enums.ItemStatus;
import com.example._04_backend.domain.wish.repository.BudgetCycleRepository;
import com.example._04_backend.domain.wish.repository.SavedItemRepository;
import com.example._04_backend.global.error.BusinessException;
import com.example._04_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final FeedPostRepository feedPostRepository;
    private final PostVoteRepository postVoteRepository;
    private final PostCommentRepository postCommentRepository;
    private final SavedItemRepository savedItemRepository;
    private final BudgetCycleRepository budgetCycleRepository;

    public MyProfileResponse getMyProfile(UUID userId) {
        User user = findUserOrThrow(userId);
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        long postCount = feedPostRepository.countByUserIdAndDeletedAtIsNull(userId);
        return MyProfileResponse.of(user, profile, postCount);
    }

    @Transactional
    public MyProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> userProfileRepository.save(
                        UserProfile.builder()
                                .user(user)
                                .nickname(request.getNickname() != null ? request.getNickname() : "사용자")
                                .mascotName(request.getRaccoonName() != null ? request.getRaccoonName() : "너구리")
                                .build()
                ));
        profile.updateProfile(request.getNickname(), request.getRaccoonName());
        long postCount = feedPostRepository.countByUserIdAndDeletedAtIsNull(userId);
        return MyProfileResponse.of(user, profile, postCount);
    }

    @Transactional
    public MyProfileResponse updateNickname(UUID userId,
            com.example._04_backend.domain.user.dto.request.UpdateNicknameRequest request) {
        User user = findUserOrThrow(userId);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> userProfileRepository.save(
                        UserProfile.builder()
                                .user(user)
                                .nickname(request.getNickname())
                                .mascotName("너구리")
                                .build()
                ));
        profile.updateProfile(request.getNickname(), null);
        long postCount = feedPostRepository.countByUserIdAndDeletedAtIsNull(userId);
        return MyProfileResponse.of(user, profile, postCount);
    }

    @Transactional
    public BudgetUpdateResponse updateBudget(UUID userId, UpdateBudgetRequest request) {
        User user = findUserOrThrow(userId);
        String currentYearMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);

        BudgetCycle budgetCycle = budgetCycleRepository
                .findByUserIdAndYearMonth(userId, currentYearMonth)
                .orElseGet(() -> budgetCycleRepository.save(
                        BudgetCycle.builder()
                                .user(user)
                                .yearMonth(currentYearMonth)
                                .monthlyBudgetAmount(request.getMonthlyBudget())
                                .build()
                ));
        budgetCycle.updateBudgetAmount(request.getMonthlyBudget());

        return new BudgetUpdateResponse(request.getMonthlyBudget(), null);
    }

    public NotificationSettingsResponse getNotificationSettings(UUID userId) {
        findUserOrThrow(userId);
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        boolean enabled = profile == null || profile.isNotificationEnabled();
        return new NotificationSettingsResponse(enabled);
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(UUID userId, NotificationSettingsRequest request) {
        User user = findUserOrThrow(userId);
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> userProfileRepository.save(
                        UserProfile.builder()
                                .user(user)
                                .nickname("사용자")
                                .mascotName("너구리")
                                .build()
                ));
        profile.updateNotificationEnabled(request.getNotificationEnabled());
        return new NotificationSettingsResponse(request.getNotificationEnabled());
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = findUserOrThrow(userId);

        postVoteRepository.deleteByUserId(userId);
        postCommentRepository.deleteByUserId(userId);
        postVoteRepository.deleteByPostUserId(userId);
        postCommentRepository.deleteByPostUserId(userId);
        feedPostRepository.deleteByUserId(userId);
        savedItemRepository.deleteByUserId(userId);
        budgetCycleRepository.deleteByUserId(userId);
        userProfileRepository.deleteByUserId(userId);
        user.withdraw();
    }

    public StatsResponse getStats(UUID userId, String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            yearMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);
        }

        findUserOrThrow(userId);

        YearMonth ym = YearMonth.parse(yearMonth, YEAR_MONTH_FORMAT);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().plusDays(1).atStartOfDay();

        BudgetCycle budget = budgetCycleRepository
                .findByUserIdAndYearMonth(userId, yearMonth)
                .orElse(null);

        Integer budgetAmount = budget != null ? budget.getMonthlyBudgetAmount() : null;
        Integer spentAmount = savedItemRepository.sumPriceByUserIdAndStatusAndDecidedAtBetween(
                userId, ItemStatus.GO, from, to);
        Integer restrainedAmount = savedItemRepository.sumPriceByUserIdAndStatusAndDecidedAtBetween(
                userId, ItemStatus.STOP, from, to);

        long boughtCount = savedItemRepository.countByUserIdAndStatusAndDecidedAtBetween(
                userId, ItemStatus.GO, from, to);
        long restrainedCount = savedItemRepository.countByUserIdAndStatusAndDecidedAtBetween(
                userId, ItemStatus.STOP, from, to);

        Double usageRate = null;
        if (budgetAmount != null && budgetAmount > 0 && spentAmount != null) {
            usageRate = Math.round((double) spentAmount / budgetAmount * 1000.0) / 10.0;
        }

        return StatsResponse.builder()
                .yearMonth(yearMonth)
                .budgetAmount(budgetAmount)
                .spentAmount(spentAmount != null ? spentAmount : 0)
                .restrainedAmount(restrainedAmount != null ? restrainedAmount : 0)
                .usageRate(usageRate)
                .boughtCount(boughtCount)
                .restrainedCount(restrainedCount)
                .byCategory(List.of())
                .build();
    }

    public WishHistoryResponse getWishHistory(UUID userId, ItemStatus status, String yearMonth, int page, int size) {
        if (yearMonth == null || yearMonth.isBlank()) {
            yearMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);
        }

        YearMonth ym = YearMonth.parse(yearMonth, YEAR_MONTH_FORMAT);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().plusDays(1).atStartOfDay();

        PageRequest pageable = PageRequest.of(page, size);
        List<SavedItem> items = savedItemRepository.findByUserIdAndStatusAndDecidedAtBetween(
                userId, status, from, to, pageable);

        long total;
        if (status != null) {
            total = savedItemRepository.countByUserIdAndStatusAndDecidedAtBetween(userId, status, from, to);
        } else {
            total = savedItemRepository.countByUserIdAndStatusAndDecidedAtBetween(userId, ItemStatus.GO, from, to)
                    + savedItemRepository.countByUserIdAndStatusAndDecidedAtBetween(userId, ItemStatus.STOP, from, to);
        }

        List<WishSummaryResponse> wishResponses = items.stream()
                .map(WishSummaryResponse::of)
                .toList();

        return WishHistoryResponse.builder()
                .wishes(wishResponses)
                .total(total)
                .page(page)
                .size(size)
                .build();
    }

    public PostListResponse getMyPosts(UUID userId, UUID cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<FeedPost> posts;

        if (cursor != null) {
            posts = feedPostRepository.findByUserIdVisibleWithCursorOrderByCreatedAtDesc(userId, cursor, pageable);
        } else {
            posts = feedPostRepository.findByUserIdVisibleOrderByCreatedAtDesc(userId, pageable);
        }

        boolean hasMore = posts.size() > size;
        if (hasMore) posts = posts.subList(0, size);

        List<PostResponse> postResponses = posts.stream()
                .map(post -> {
                    long commentCount = postCommentRepository.countByPostIdAndDeletedAtIsNull(post.getId());
                    PostVoteType myVote = postVoteRepository.findByPostIdAndUserId(post.getId(), userId)
                            .filter(PostVote::isActive)
                            .map(PostVote::getVoteType).orElse(null);
                    return PostResponse.of(post, commentCount, myVote);
                })
                .toList();

        UUID nextCursor = hasMore && !posts.isEmpty() ? posts.get(posts.size() - 1).getId() : null;

        return PostListResponse.builder()
                .posts(postResponses)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    public PostListResponse getMyVotedPosts(UUID userId, UUID cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<PostVote> votes;

        if (cursor != null) {
            votes = postVoteRepository.findActiveByUserIdWithCursorOrderByCreatedAtDesc(userId, cursor, pageable);
        } else {
            votes = postVoteRepository.findActiveByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        boolean hasMore = votes.size() > size;
        if (hasMore) votes = votes.subList(0, size);

        List<PostResponse> postResponses = votes.stream()
                .map(vote -> {
                    FeedPost post = vote.getPost();
                    long commentCount = postCommentRepository.countByPostIdAndDeletedAtIsNull(post.getId());
                    return PostResponse.of(post, commentCount, vote.getVoteType());
                })
                .toList();

        UUID nextCursor = hasMore && !votes.isEmpty() ? votes.get(votes.size() - 1).getPost().getId() : null;

        return PostListResponse.builder()
                .posts(postResponses)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    public AvailableMonthsResponse getAvailableMonths(UUID userId) {
        findUserOrThrow(userId);

        Set<String> monthSet = new LinkedHashSet<>();

        List<String> budgetMonths = budgetCycleRepository.findYearMonthsByUserId(userId);
        monthSet.addAll(budgetMonths);

        List<Object[]> decidedMonths = savedItemRepository.findDistinctDecidedYearMonthsByUserId(
                userId, List.of(ItemStatus.GO, ItemStatus.STOP));
        for (Object[] row : decidedMonths) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            monthSet.add(String.format("%04d-%02d", year, month));
        }

        String currentMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);
        monthSet.add(currentMonth);

        List<String> sorted = new ArrayList<>(monthSet);
        sorted.sort((a, b) -> b.compareTo(a));

        return new AvailableMonthsResponse(sorted, currentMonth);
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
