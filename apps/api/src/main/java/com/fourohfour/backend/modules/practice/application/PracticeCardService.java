package com.fourohfour.backend.modules.practice.application;

import com.fourohfour.backend.modules.content.application.GeneratedPracticeCard;
import com.fourohfour.backend.modules.content.application.PracticeCardSection;
import com.fourohfour.backend.modules.practice.infrastructure.PracticeCardJdbcRepository;
import com.fourohfour.backend.modules.practice.infrastructure.PracticeCardJdbcRepository.PracticeCardRecord;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticeCardService {

    private final PracticeCardJdbcRepository practiceCardJdbcRepository;
    private final Clock clock;

    public PracticeCardService(PracticeCardJdbcRepository practiceCardJdbcRepository, Clock clock) {
        this.practiceCardJdbcRepository = practiceCardJdbcRepository;
        this.clock = clock;
    }

    @Transactional
    public PracticeCardView createFromGeneratedCard(
            UUID userId,
            UUID savedContentId,
            GeneratedPracticeCard generatedPracticeCard,
            OffsetDateTime now,
            String enhancementStatus,
            String enhancementNote
    ) {
        return toView(practiceCardJdbcRepository.saveCard(
                userId,
                savedContentId,
                generatedPracticeCard,
                now,
                enhancementStatus,
                enhancementNote
        ));
    }

    @Transactional
    public boolean replaceCardContentIfOpen(
            UUID userId,
            UUID cardId,
            GeneratedPracticeCard generatedPracticeCard
    ) {
        return practiceCardJdbcRepository.replaceCardContentIfOpen(
                userId,
                cardId,
                generatedPracticeCard,
                OffsetDateTime.now(clock)
        );
    }

    public List<PracticeCardView> getDeck(UUID userId, LocalDate targetDate) {
        return practiceCardJdbcRepository.findDeck(userId, targetDate).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public PracticeCardView completeCard(CompletePracticeCardCommand command) {
        PracticeCardRecord record = practiceCardJdbcRepository.completeCard(
                command.userId(),
                command.cardId(),
                command.completionNote(),
                OffsetDateTime.now(clock)
        );
        return toView(record);
    }

    public PracticeCardView getCard(UUID userId, UUID cardId) {
        return toView(practiceCardJdbcRepository.getCard(userId, cardId));
    }

    public PracticeCardJdbcRepository.PracticeCardRecord getCardRecord(UUID userId, UUID cardId) {
        return practiceCardJdbcRepository.getCard(userId, cardId);
    }

    @Transactional
    public PracticeCardView markEnhancementPending(UUID userId, UUID cardId, String note) {
        practiceCardJdbcRepository.updateEnhancementStatus(
                userId,
                cardId,
                "PENDING",
                note,
                "AI_PENDING",
                OffsetDateTime.now(clock)
        );
        return getCard(userId, cardId);
    }

    @Transactional
    public void markEnhancementSkipped(UUID userId, UUID cardId, String note) {
        practiceCardJdbcRepository.updateEnhancementStatus(
                userId,
                cardId,
                "SKIPPED",
                note,
                "AI_SKIPPED",
                OffsetDateTime.now(clock)
        );
    }

    @Transactional
    public void markEnhancementFailed(UUID userId, UUID cardId, String note) {
        practiceCardJdbcRepository.updateEnhancementStatus(
                userId,
                cardId,
                "FAILED",
                note,
                "AI_FAILED",
                OffsetDateTime.now(clock)
        );
    }

    @Transactional
    public PracticeCardView reportIssue(UUID userId, UUID cardId, String reason) {
        practiceCardJdbcRepository.reportIssue(userId, cardId, reason, OffsetDateTime.now(clock));
        return getCard(userId, cardId);
    }

    public AiEnhancementMonitoringView getEnhancementMonitoringView(UUID userId) {
        PracticeCardJdbcRepository.EnhancementMonitoringSnapshot snapshot =
                practiceCardJdbcRepository.getEnhancementMonitoringSnapshot(userId);
        return new AiEnhancementMonitoringView(
                snapshot.pendingCount(),
                snapshot.enhancedCount(),
                snapshot.skippedCount(),
                snapshot.failedCount(),
                snapshot.reportedCount(),
                snapshot.recentEvents().stream()
                        .map(event -> new AiEnhancementEventView(
                                event.practiceCardId(),
                                event.eventType(),
                                mapEnhancementStatusLabel(event.eventType()),
                                event.note(),
                                event.createdAt()
                        ))
                        .toList()
        );
    }

    private PracticeCardView toView(PracticeCardRecord record) {
        return new PracticeCardView(
                record.id(),
                record.savedContentId(),
                record.category().name(),
                record.category().displayName(),
                record.status().name(),
                record.actionTitle(),
                record.actionDetail(),
                record.detailTitle(),
                record.detailBody(),
                record.detailSections(),
                record.ideaOptions(),
                record.encouragementMessage(),
                record.rationale(),
                record.estimatedMinutes(),
                record.energyLevel().name(),
                record.scheduledFor(),
                record.completedAt(),
                record.completionNote(),
                record.sourceTitle(),
                record.url(),
                record.sourceDomain(),
                record.enhancementStatus(),
                mapEnhancementStatusLabel(record.enhancementStatus()),
                record.enhancementNote(),
                record.enhancementUpdatedAt(),
                record.createdAt()
        );
    }

    private String mapEnhancementStatusLabel(String status) {
        return switch (status) {
            case "PENDING", "AI_PENDING" -> "AI 업그레이드 대기중";
            case "ENHANCED", "AI_ENHANCED" -> "AI 업그레이드 완료";
            case "SKIPPED", "AI_SKIPPED" -> "AI 업그레이드 스킵";
            case "FAILED", "AI_FAILED" -> "AI 업그레이드 실패";
            case "REPORTED" -> "카드 이슈 신고됨";
            case "DISABLED" -> "AI 업그레이드 비활성화";
            default -> "상태 확인 필요";
        };
    }

    public record CompletePracticeCardCommand(
            UUID userId,
            UUID cardId,
            String completionNote
    ) {
    }

    public record PracticeCardView(
            UUID id,
            UUID savedContentId,
            String category,
            String categoryLabel,
            String status,
            String actionTitle,
            String actionDetail,
            String detailTitle,
            String detailBody,
            List<PracticeCardSection> detailSections,
            List<String> ideaOptions,
            String encouragementMessage,
            String rationale,
            int estimatedMinutes,
            String energyLevel,
            LocalDate scheduledFor,
            OffsetDateTime completedAt,
            String completionNote,
            String sourceTitle,
            String sourceUrl,
            String sourceDomain,
            String enhancementStatus,
            String enhancementStatusLabel,
            String enhancementNote,
            OffsetDateTime enhancementUpdatedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record AiEnhancementMonitoringView(
            int pendingCount,
            int enhancedCount,
            int skippedCount,
            int failedCount,
            int reportedCount,
            List<AiEnhancementEventView> recentEvents
    ) {
    }

    public record AiEnhancementEventView(
            UUID practiceCardId,
            String eventType,
            String eventTypeLabel,
            String note,
            OffsetDateTime createdAt
    ) {
    }
}
