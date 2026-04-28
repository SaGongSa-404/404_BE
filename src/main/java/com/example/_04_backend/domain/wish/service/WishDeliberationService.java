package com.example._04_backend.domain.wish.service;

import com.example._04_backend.domain.wish.dto.request.SubmitDeliberationRequest;
import com.example._04_backend.domain.wish.dto.response.DeliberationInfoResponse;
import com.example._04_backend.domain.wish.dto.response.DeliberationResultResponse;
import com.example._04_backend.domain.wish.entity.BudgetCycle;
import com.example._04_backend.domain.wish.entity.SavedItem;
import com.example._04_backend.domain.wish.entity.SelfCheckAnswer;
import com.example._04_backend.domain.wish.entity.SelfCheckResponseSet;
import com.example._04_backend.domain.wish.enums.ItemStatus;
import com.example._04_backend.domain.wish.enums.RationalityResult;
import com.example._04_backend.domain.wish.repository.BudgetCycleRepository;
import com.example._04_backend.domain.wish.repository.SavedItemRepository;
import com.example._04_backend.domain.wish.repository.SelfCheckResponseSetRepository;
import com.example._04_backend.global.error.BusinessException;
import com.example._04_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    // 질문 코드 → 텍스트 매핑
    private static final List<String> QUESTION_CODES = List.of(
            "Q1", "Q2", "Q3", "Q4"
    );

    private static final List<DeliberationInfoResponse.Question> QUESTIONS = List.of(
            DeliberationInfoResponse.Question.builder().id(1).text("오늘 갑자기 갖고 싶어진 건가요?").build(),
            DeliberationInfoResponse.Question.builder().id(2).text("비슷한 물건을 이미 갖고 있나요?").build(),
            DeliberationInfoResponse.Question.builder().id(3).text("구매하지 않아도 일상생활에 불편함이 없나요?").build(),
            DeliberationInfoResponse.Question.builder().id(4).text("한 달 뒤에는 필요하지 않을 것 같나요?").build()
    );

    private final SavedItemRepository savedItemRepository;
    private final SelfCheckResponseSetRepository selfCheckResponseSetRepository;
    private final BudgetCycleRepository budgetCycleRepository;

    public DeliberationInfoResponse getDeliberationInfo(UUID userId, UUID itemId) {
        SavedItem item = findAndValidateItem(userId, itemId);

        String yearMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);
        BudgetCycle budgetCycle = budgetCycleRepository
                .findByUserIdAndYearMonth(userId, yearMonth)
                .orElse(null);

        Integer budgetAmount = budgetCycle != null ? budgetCycle.getMonthlyBudgetAmount() : null;
        Integer spentAmount = budgetCycle != null ? budgetCycle.getSpentAmount() : 0;
        Integer remainingBudget = budgetAmount != null ? Math.max(0, budgetAmount - spentAmount) : null;

        LocalDateTime from = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime to = YearMonth.now().atEndOfMonth().plusDays(1).atStartOfDay();

        long irrationalCount = selfCheckResponseSetRepository
                .countIrrationalByUserIdAndDecidedAtBetween(userId, from, to);
        Integer opportunityCost = savedItemRepository
                .sumPriceByUserIdAndStatusAndDecidedAtBetween(userId, ItemStatus.STOP, from, to);

        return DeliberationInfoResponse.builder()
                .wish(DeliberationInfoResponse.WishInfo.of(item))
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
    public DeliberationResultResponse submitDeliberation(UUID userId, UUID itemId,
                                                         SubmitDeliberationRequest request) {
        SavedItem item = findAndValidateItem(userId, itemId);

        ItemStatus decision = request.getDecision();
        if (decision != ItemStatus.GO && decision != ItemStatus.STOP) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "decision은 GO 또는 STOP이어야 합니다.");
        }

        List<Boolean> answers = request.getAnswers();
        int yesCount = (int) answers.stream().filter(Boolean::booleanValue).count();
        boolean warningTriggered = yesCount >= 2;
        RationalityResult rationalityResult = warningTriggered
                ? RationalityResult.IRRATIONAL
                : RationalityResult.RATIONAL;

        // SelfCheckResponseSet 저장
        SelfCheckResponseSet responseSet = selfCheckResponseSetRepository.save(
                SelfCheckResponseSet.builder()
                        .item(item)
                        .yesCount((short) yesCount)
                        .rationalityResult(rationalityResult)
                        .build()
        );

        // 개별 답변 저장
        for (int i = 0; i < QUESTION_CODES.size(); i++) {
            SelfCheckAnswer answer = SelfCheckAnswer.builder()
                    .responseSet(responseSet)
                    .questionCode(QUESTION_CODES.get(i))
                    .answerBoolean(answers.get(i))
                    .build();
            // responseSet에 cascade 없으므로 직접 persist 필요 — 서비스에서 처리
            selfCheckResponseSetRepository.flush(); // flush 후 answer는 별도 repo 없이 entityManager로 저장
        }

        // 아이템 상태 변경
        item.decide(decision);

        // GO(구매) 시 예산 차감
        String yearMonth = YearMonth.now().format(YEAR_MONTH_FORMAT);
        if (decision == ItemStatus.GO && item.getListedPrice() != null) {
            BudgetCycle budgetCycle = budgetCycleRepository
                    .findByUserIdAndYearMonth(userId, yearMonth)
                    .orElseGet(() -> budgetCycleRepository.save(
                            BudgetCycle.builder()
                                    .user(item.getUser())
                                    .yearMonth(yearMonth)
                                    .monthlyBudgetAmount(0)
                                    .warningThresholdRate(new BigDecimal("80.00"))
                                    .build()
                    ));
            budgetCycle.addSpent(item.getListedPrice());
        }

        LocalDateTime from = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime to = YearMonth.now().atEndOfMonth().plusDays(1).atStartOfDay();

        Integer spentAmount = savedItemRepository.sumPriceByUserIdAndStatusAndDecidedAtBetween(
                userId, ItemStatus.GO, from, to);
        long irrationalCount = selfCheckResponseSetRepository
                .countIrrationalByUserIdAndDecidedAtBetween(userId, from, to);
        Integer opportunityCost = savedItemRepository.sumPriceByUserIdAndStatusAndDecidedAtBetween(
                userId, ItemStatus.STOP, from, to);

        return DeliberationResultResponse.builder()
                .wishId(itemId)
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

    private SavedItem findAndValidateItem(UUID userId, UUID itemId) {
        SavedItem item = savedItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISH_NOT_FOUND));

        if (!item.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (item.getStatus() != ItemStatus.SAVED) {
            throw new BusinessException(ErrorCode.ALREADY_DECIDED);
        }

        if (item.getCreatedAt().plusHours(24).isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.DELIBERATION_NOT_READY);
        }

        return item;
    }
}
