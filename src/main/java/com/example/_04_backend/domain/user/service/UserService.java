package com.example._04_backend.domain.user.service;

import com.example._04_backend.domain.social.dto.response.PostListResponse;
import com.example._04_backend.domain.social.dto.response.PostResponse;
import com.example._04_backend.domain.social.entity.SocialPost;
import com.example._04_backend.domain.social.entity.Vote;
import com.example._04_backend.domain.social.enums.VoteType;
import com.example._04_backend.domain.social.repository.CommentRepository;
import com.example._04_backend.domain.social.repository.SocialPostRepository;
import com.example._04_backend.domain.social.repository.VoteRepository;
import com.example._04_backend.domain.user.dto.request.NotificationSettingsRequest;
import com.example._04_backend.domain.user.dto.request.UpdateBudgetRequest;
import com.example._04_backend.domain.user.dto.request.UpdateNicknameRequest;
import com.example._04_backend.domain.user.dto.request.UpdateProfileRequest;
import com.example._04_backend.domain.user.dto.response.*;
import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.user.repository.UserRepository;
import com.example._04_backend.domain.wish.entity.MonthlyBudget;
import com.example._04_backend.domain.wish.entity.Wish;
import com.example._04_backend.domain.wish.enums.WishStatus;
import com.example._04_backend.domain.wish.repository.MonthlyBudgetRepository;
import com.example._04_backend.domain.wish.repository.WishRepository;
import com.example._04_backend.global.common.enums.Category;
import com.example._04_backend.global.error.BusinessException;
import com.example._04_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserRepository userRepository;
    private final SocialPostRepository socialPostRepository;
    private final VoteRepository voteRepository;
    private final CommentRepository commentRepository;
    private final WishRepository wishRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public MyProfileResponse getMyProfile(UUID userId) {
        User user = findUserOrThrow(userId);
        long postCount = socialPostRepository.countByUserId(userId);
        return MyProfileResponse.of(user, postCount);
    }

    @Transactional
    public MyProfileResponse updateNickname(UUID userId, UpdateNicknameRequest request) {
        User user = findUserOrThrow(userId);
        user.updateNickname(request.getNickname());
        long postCount = socialPostRepository.countByUserId(userId);
        return MyProfileResponse.of(user, postCount);
    }

    @Transactional
    public MyProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);
        user.updateProfileWithRaccoonName(request.getNickname(), request.getRaccoonName());
        long postCount = socialPostRepository.countByUserId(userId);
        return MyProfileResponse.of(user, postCount);
    }

    @Transactional
    public BudgetUpdateResponse updateBudget(UUID userId, UpdateBudgetRequest request) {
        User user = findUserOrThrow(userId);
        user.updateMonthlyBudget(request.getMonthlyBudget());

        String currentYearMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);
        MonthlyBudget monthlyBudget = monthlyBudgetRepository
                .findByUserIdAndYearMonth(userId, currentYearMonth)
                .orElseGet(() -> monthlyBudgetRepository.save(
                        MonthlyBudget.builder()
                                .user(user)
                                .yearMonth(currentYearMonth)
                                .budgetAmount(request.getMonthlyBudget())
                                .build()
                ));
        monthlyBudget.updateBudgetAmount(request.getMonthlyBudget());

        user.recalculateRaccoonStatus(monthlyBudget.getSpentAmount(), request.getMonthlyBudget());

        return new BudgetUpdateResponse(user.getMonthlyBudget(), user.getRaccoonStatus());
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(UUID userId, NotificationSettingsRequest request) {
        User user = findUserOrThrow(userId);
        user.updateNotificationEnabled(request.getNotificationEnabled());
        return new NotificationSettingsResponse(user.isNotificationEnabled());
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = findUserOrThrow(userId);

        // 내가 다른 게시글에 남긴 투표/댓글 삭제
        voteRepository.deleteByUserId(userId);
        commentRepository.deleteByUserId(userId);

        // 내 게시글에 달린 다른 사람의 투표/댓글 삭제 (cascade JPQL 미트리거 방지)
        voteRepository.deleteByPostUserId(userId);
        commentRepository.deleteByPostUserId(userId);

        socialPostRepository.deleteByUserId(userId);
        wishRepository.deleteByUserId(userId);
        monthlyBudgetRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    public StatsResponse getStats(UUID userId, String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            yearMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);
        }

        User user = findUserOrThrow(userId);

        YearMonth ym = YearMonth.parse(yearMonth, YEAR_MONTH_FORMAT);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().plusDays(1).atStartOfDay();

        MonthlyBudget budget = monthlyBudgetRepository
                .findByUserIdAndYearMonth(userId, yearMonth)
                .orElse(null);

        Integer budgetAmount = budget != null ? budget.getBudgetAmount() : user.getMonthlyBudget();
        Integer spentAmount = wishRepository.sumPriceByUserIdAndStatusAndDecisionAtBetween(
                userId, WishStatus.BOUGHT, from, to);
        Integer restrainedAmount = wishRepository.sumPriceByUserIdAndStatusAndDecisionAtBetween(
                userId, WishStatus.RESTRAINED, from, to);

        long boughtCount = wishRepository.countByUserIdAndStatusAndDecisionAtBetween(
                userId, WishStatus.BOUGHT, from, to);
        long restrainedCount = wishRepository.countByUserIdAndStatusAndDecisionAtBetween(
                userId, WishStatus.RESTRAINED, from, to);

        Double usageRate = null;
        if (budgetAmount != null && budgetAmount > 0) {
            usageRate = Math.round((double) spentAmount / budgetAmount * 1000.0) / 10.0;
        }

        List<Object[]> rawCategoryStats = wishRepository
                .findCategoryStatsByUserIdAndDecisionAtBetween(userId, from, to);

        List<CategoryStatResponse> byCategory = rawCategoryStats.stream()
                .map(row -> new CategoryStatResponse(
                        (Category) row[0],
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).longValue()
                ))
                .toList();

        return StatsResponse.builder()
                .yearMonth(yearMonth)
                .budgetAmount(budgetAmount)
                .spentAmount(spentAmount)
                .restrainedAmount(restrainedAmount)
                .usageRate(usageRate)
                .boughtCount(boughtCount)
                .restrainedCount(restrainedCount)
                .byCategory(byCategory)
                .build();
    }

    public WishHistoryResponse getWishHistory(UUID userId, WishStatus status, String yearMonth, int page, int size) {
        if (yearMonth == null || yearMonth.isBlank()) {
            yearMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);
        }

        YearMonth ym = YearMonth.parse(yearMonth, YEAR_MONTH_FORMAT);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().plusDays(1).atStartOfDay();

        PageRequest pageable = PageRequest.of(page, size);
        List<Wish> wishes = wishRepository.findByUserIdAndStatusAndDecisionAtBetween(
                userId, status, from, to, pageable);

        long total;
        if (status != null) {
            total = wishRepository.countByUserIdAndStatusAndDecisionAtBetween(userId, status, from, to);
        } else {
            total = wishRepository.countByUserIdAndStatusAndDecisionAtBetween(userId, WishStatus.BOUGHT, from, to)
                    + wishRepository.countByUserIdAndStatusAndDecisionAtBetween(userId, WishStatus.RESTRAINED, from, to);
        }

        List<WishSummaryResponse> wishResponses = wishes.stream()
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
        List<SocialPost> posts;

        if (cursor != null) {
            posts = socialPostRepository.findByUserIdWithCursorOrderByCreatedAtDesc(userId, cursor, pageable);
        } else {
            posts = socialPostRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        boolean hasMore = posts.size() > size;
        if (hasMore) {
            posts = posts.subList(0, size);
        }

        List<PostResponse> postResponses = posts.stream()
                .map(post -> {
                    long commentCount = commentRepository.countByPostId(post.getId());
                    VoteType myVote = voteRepository.findByPostIdAndUserId(post.getId(), userId)
                            .map(Vote::getVoteType).orElse(null);
                    return PostResponse.of(post, commentCount, myVote);
                })
                .toList();

        UUID nextCursor = hasMore && !posts.isEmpty()
                ? posts.get(posts.size() - 1).getId()
                : null;

        return PostListResponse.builder()
                .posts(postResponses)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    public PostListResponse getMyVotedPosts(UUID userId, UUID cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<Vote> votes;

        if (cursor != null) {
            votes = voteRepository.findByUserIdWithCursorOrderByCreatedAtDesc(userId, cursor, pageable);
        } else {
            votes = voteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        boolean hasMore = votes.size() > size;
        if (hasMore) {
            votes = votes.subList(0, size);
        }

        List<PostResponse> postResponses = votes.stream()
                .map(vote -> {
                    SocialPost post = vote.getPost();
                    long commentCount = commentRepository.countByPostId(post.getId());
                    return PostResponse.of(post, commentCount, vote.getVoteType());
                })
                .toList();

        UUID nextCursor = hasMore && !votes.isEmpty()
                ? votes.get(votes.size() - 1).getPost().getId()
                : null;

        return PostListResponse.builder()
                .posts(postResponses)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
