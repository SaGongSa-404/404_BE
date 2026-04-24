package com.example._04_backend.domain.wish.service;

import com.example._04_backend.domain.wish.dto.request.SubmitDeliberationRequest;
import com.example._04_backend.domain.wish.dto.response.DeliberationInfoResponse;
import com.example._04_backend.domain.wish.dto.response.DeliberationResultResponse;
import com.example._04_backend.domain.wish.entity.MonthlyBudget;
import com.example._04_backend.domain.wish.entity.Wish;
import com.example._04_backend.domain.wish.entity.WishDeliberation;
import com.example._04_backend.domain.wish.enums.WishStatus;
import com.example._04_backend.domain.wish.repository.MonthlyBudgetRepository;
import com.example._04_backend.domain.wish.repository.WishDeliberationRepository;
import com.example._04_backend.domain.wish.repository.WishRepository;
import com.example._04_backend.global.error.BusinessException;
import com.example._04_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
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
public class WishDeliberationService {

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final List<DeliberationInfoResponse.Question> QUESTIONS = List.of(
            DeliberationInfoResponse.Question.builder().id(1).text("오늘 갑자기 갖고 싶어진 건가요?").build(),
            DeliberationInfoResponse.Question.builder().id(2).text("비슷한 물건을 이미 갖고 있나요?").build(),
            DeliberationInfoResponse.Question.builder().id(3).text("구매하지 않아도 일상생활에 불편함이 없나요?").build(),
            DeliberationInfoResponse.Question.builder().id(4).text("한 달 뒤에는 필요하지 않을 것 같나요?").build()
    );

    private final WishRepository wishRepository;
    private final WishDeliberationRepository wishDeliberationRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public DeliberationInfoResponse getDeliberationInfo(UUID userId, UUID wishId) {
        Wish wish = findAndValidateWish(userId, wishId);

        String yearMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);
        MonthlyBudget monthlyBudget = monthlyBudgetRepository
                .findByUserIdAndYearMonth(userId, yearMonth)
                .orElse(null);

        Integer budgetAmount = monthlyBudget != null
                ? monthlyBudget.getBudgetAmount()
                : wish.getUser().getMonthlyBudget();
        Integer spentAmount = monthlyBudget != null ? monthlyBudget.getSpentAmount() : 0;
        Integer remainingBudget = budgetAmount != null ? Math.max(0, budgetAmount - spentAmount) : null;

        LocalDateTime from = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime to = YearMonth.now().atEndOfMonth().plusDays(1).atStartOfDay();

        long irrationalCount = wishDeliberationRepository
                .countIrrationalByUserIdAndDecisionAtBetween(userId, from, to);
        Integer opportunityCost = wishRepository
                .sumPriceByUserIdAndStatusAndDecisionAtBetween(userId, WishStatus.RESTRAINED, from, to);

        return DeliberationInfoResponse.builder()
                .wish(DeliberationInfoResponse.WishInfo.of(wish))
                .budget(DeliberationInfoResponse.BudgetInfo.builder()
                        .monthlyBudget(budgetAmount)
                        .spentAmount(spentAmount)
                        .remainingBudget(remainingBudget)
                        .build())
                .monthStats(DeliberationInfoResponse.MonthStats.builder()
                        .spentAmount(spentAmount)
                        .irrationalCount(irrationalCount)
                        .opportunityCost(opportunityCost != null ? opportunityCost : 0)
                        .build())
                .questions(QUESTIONS)
                .build();
    }

    @Transactional
    public DeliberationResultResponse submitDeliberation(UUID userId, UUID wishId,
                                                         SubmitDeliberationRequest request) {
        Wish wish = findAndValidateWish(userId, wishId);

        WishStatus decision = request.getDecision();
        if (decision != WishStatus.BOUGHT && decision != WishStatus.RESTRAINED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "decision은 BOUGHT 또는 RESTRAINED여야 합니다.");
        }

        List<Boolean> answers = request.getAnswers();
        int yesCount = (int) answers.stream().filter(Boolean::booleanValue).count();
        boolean warningTriggered = yesCount >= 2;

        WishDeliberation deliberation = WishDeliberation.builder()
                .wish(wish)
                .answer1(answers.get(0))
                .answer2(answers.get(1))
                .answer3(answers.get(2))
                .answer4(answers.get(3))
                .yesCount(yesCount)
                .warningTriggered(warningTriggered)
                .build();
        wishDeliberationRepository.save(deliberation);

        wish.decide(decision);

        String yearMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);
        if (decision == WishStatus.BOUGHT) {
            MonthlyBudget monthlyBudget = monthlyBudgetRepository
                    .findByUserIdAndYearMonth(userId, yearMonth)
                    .orElseGet(() -> monthlyBudgetRepository.save(
                            MonthlyBudget.builder()
                                    .user(wish.getUser())
                                    .yearMonth(yearMonth)
                                    .budgetAmount(wish.getUser().getMonthlyBudget() != null
                                            ? wish.getUser().getMonthlyBudget() : 0)
                                    .build()
                    ));
            monthlyBudget.addSpent(wish.getPrice());
            wish.getUser().recalculateRaccoonStatus(
                    monthlyBudget.getSpentAmount(), monthlyBudget.getBudgetAmount());
        }

        LocalDateTime from = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime to = YearMonth.now().atEndOfMonth().plusDays(1).atStartOfDay();

        Integer spentAmount = wishRepository.sumPriceByUserIdAndStatusAndDecisionAtBetween(
                userId, WishStatus.BOUGHT, from, to);
        long irrationalCount = wishDeliberationRepository
                .countIrrationalByUserIdAndDecisionAtBetween(userId, from, to);
        Integer opportunityCost = wishRepository.sumPriceByUserIdAndStatusAndDecisionAtBetween(
                userId, WishStatus.RESTRAINED, from, to);

        return DeliberationResultResponse.builder()
                .wishId(wishId)
                .decision(decision)
                .yesCount(yesCount)
                .warningTriggered(warningTriggered)
                .monthStats(DeliberationResultResponse.MonthStats.builder()
                        .spentAmount(spentAmount != null ? spentAmount : 0)
                        .irrationalCount(irrationalCount)
                        .opportunityCost(opportunityCost != null ? opportunityCost : 0)
                        .build())
                .build();
    }

    private Wish findAndValidateWish(UUID userId, UUID wishId) {
        Wish wish = wishRepository.findById(wishId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISH_NOT_FOUND));

        if (!wish.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (wish.getStatus() != WishStatus.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_DECIDED);
        }

        if (wish.getCreatedAt().plusHours(24).isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.DELIBERATION_NOT_READY);
        }

        return wish;
    }
}
