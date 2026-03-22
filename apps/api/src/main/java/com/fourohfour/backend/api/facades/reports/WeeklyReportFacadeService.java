package com.fourohfour.backend.api.facades.reports;

import com.fourohfour.backend.modules.practice.infrastructure.PracticeCardJdbcRepository;
import com.fourohfour.backend.modules.practice.infrastructure.PracticeCardJdbcRepository.CategoryCountRecord;
import com.fourohfour.backend.modules.practice.infrastructure.PracticeCardJdbcRepository.CategoryProgressRecord;
import com.fourohfour.backend.modules.practice.infrastructure.PracticeCardJdbcRepository.CompletionRecord;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WeeklyReportFacadeService {

    private final PracticeCardJdbcRepository practiceCardJdbcRepository;
    private final Clock clock;

    public WeeklyReportFacadeService(PracticeCardJdbcRepository practiceCardJdbcRepository, Clock clock) {
        this.practiceCardJdbcRepository = practiceCardJdbcRepository;
        this.clock = clock;
    }

    public WeeklyReportView getWeeklyReport(UUID userId, LocalDate anchorDate) {
        LocalDate weekStartDate = anchorDate.with(DayOfWeek.MONDAY);
        LocalDate weekEndDate = weekStartDate.plusDays(6);

        int savedCount = practiceCardJdbcRepository.countSavedContents(userId, weekStartDate, weekEndDate);
        int completedCount = practiceCardJdbcRepository.countCompletedCards(userId, weekStartDate, weekEndDate);

        List<CategoryCountView> savedCategories = practiceCardJdbcRepository.findSavedCategoryCounts(userId, weekStartDate, weekEndDate)
                .stream()
                .map(this::toCategoryCountView)
                .toList();
        List<CategoryCountView> completedCategories = practiceCardJdbcRepository.findCompletedCategoryCounts(userId, weekStartDate, weekEndDate)
                .stream()
                .map(this::toCategoryCountView)
                .toList();
        List<CompletionView> recentCompletions = practiceCardJdbcRepository.findRecentCompletions(userId, weekStartDate, weekEndDate)
                .stream()
                .map(this::toCompletionView)
                .toList();

        String insight = buildInsight(savedCount, completedCount,
                practiceCardJdbcRepository.findCategoryProgress(userId, weekStartDate, weekEndDate));

        return new WeeklyReportView(
                weekStartDate,
                weekEndDate,
                savedCount,
                completedCount,
                Math.round(savedCount == 0 ? 0F : (completedCount * 100F) / savedCount),
                Math.max(savedCount - completedCount, 0),
                savedCategories,
                completedCategories,
                recentCompletions,
                insight,
                LocalDate.now(clock)
        );
    }

    private CategoryCountView toCategoryCountView(CategoryCountRecord record) {
        return new CategoryCountView(record.category().name(), record.category().displayName(), record.count());
    }

    private CompletionView toCompletionView(CompletionRecord record) {
        return new CompletionView(
                record.actionTitle(),
                record.category().name(),
                record.category().displayName(),
                record.completedAt()
        );
    }

    private String buildInsight(int savedCount, int completedCount, List<CategoryProgressRecord> progressRecords) {
        if (savedCount == 0) {
            return "아직 저장된 콘텐츠가 없어요. 링크 하나만 저장해도 오늘의 실천 카드가 바로 생겨요.";
        }
        if (completedCount == 0) {
            return "이번 주엔 카드를 많이 모았어요. 가장 가벼운 카드 1개만 골라 시작하면 덱이 바로 살아나요.";
        }

        CategoryProgressRecord best = progressRecords.stream()
                .filter(record -> record.savedCount() > 0 && record.completedCount() > 0)
                .sorted((left, right) -> {
                    double leftRate = (double) left.completedCount() / left.savedCount();
                    double rightRate = (double) right.completedCount() / right.savedCount();
                    int rateCompare = Double.compare(rightRate, leftRate);
                    if (rateCompare != 0) {
                        return rateCompare;
                    }
                    return Integer.compare(right.completedCount(), left.completedCount());
                })
                .findFirst()
                .orElse(null);

        if (best == null) {
            return "이번 주엔 저장 대비 실천이 아직 적어요. 덱에서 5분짜리 카드부터 깨보면 좋아요.";
        }

        return "이번 주 " + savedCount + "개 저장, " + completedCount + "개 실천했어요. "
                + "주로 " + best.category().displayName() + " 쪽을 잘 실천하네요.";
    }

    public record WeeklyReportView(
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            int savedCount,
            int completedCount,
            int completionRate,
            int pendingCount,
            List<CategoryCountView> savedCategories,
            List<CategoryCountView> completedCategories,
            List<CompletionView> recentCompletions,
            String insightMessage,
            LocalDate generatedDate
    ) {
    }

    public record CategoryCountView(
            String category,
            String categoryLabel,
            int count
    ) {
    }

    public record CompletionView(
            String actionTitle,
            String category,
            String categoryLabel,
            java.time.OffsetDateTime completedAt
    ) {
    }
}

