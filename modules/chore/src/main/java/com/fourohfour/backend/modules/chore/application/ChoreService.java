package com.fourohfour.backend.modules.chore.application;

import com.fourohfour.backend.modules.chore.infrastructure.ChoreJdbcRepository;
import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.shared.api.ApiException;
import com.fourohfour.backend.packages.events.OutboxJdbcRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChoreService {

    private final ChoreJdbcRepository choreJdbcRepository;
    private final HouseService houseService;
    private final OutboxJdbcRepository outboxJdbcRepository;

    public ChoreService(ChoreJdbcRepository choreJdbcRepository, HouseService houseService, OutboxJdbcRepository outboxJdbcRepository) {
        this.choreJdbcRepository = choreJdbcRepository;
        this.houseService = houseService;
        this.outboxJdbcRepository = outboxJdbcRepository;
    }

    @Transactional
    public ChoreRuleView createChore(UUID userId, UUID houseId, CreateChoreCommand command) {
        houseService.assertActiveMember(userId, houseId);
        Instant now = Instant.now();
        UUID choreRuleId = UUID.randomUUID();
        UUID choreInstanceId = UUID.randomUUID();
        LocalDate scheduledDate = command.recurrence().startDate() == null ? LocalDate.now() : command.recurrence().startDate();

        choreJdbcRepository.createChoreRule(
                choreRuleId,
                houseId,
                command.spaceId(),
                command.title(),
                command.description(),
                command.defaultAssigneeMembershipId(),
                command.estimatedMinutes(),
                now
        );
        choreJdbcRepository.createRecurrenceRule(
                UUID.randomUUID(),
                choreRuleId,
                command.recurrence().frequency(),
                command.recurrence().interval(),
                command.recurrence().daysOfWeek() == null ? null : command.recurrence().daysOfWeek().toArray(String[]::new),
                scheduledDate,
                now
        );
        choreJdbcRepository.createChoreInstance(
                choreInstanceId,
                houseId,
                choreRuleId,
                scheduledDate,
                command.defaultAssigneeMembershipId(),
                "RULE",
                now
        );
        if (command.defaultAssigneeMembershipId() != null) {
            choreJdbcRepository.createChoreAssignment(
                    UUID.randomUUID(),
                    choreInstanceId,
                    command.defaultAssigneeMembershipId(),
                    userId,
                    "AUTO",
                    now
            );
        }

        return new ChoreRuleView(choreRuleId, houseId, command.spaceId(), command.title(), command.description(), command.estimatedMinutes());
    }

    @Transactional
    public ChoreRuleView updateChore(UUID userId, UUID choreRuleId, UpdateChoreCommand command) {
        ChoreJdbcRepository.ChoreRuleRecord current = choreJdbcRepository.findChoreRule(choreRuleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHORE_NOT_FOUND", "집안일 규칙을 찾을 수 없습니다."));
        houseService.assertActiveMember(userId, current.houseId());

        String title = command.title() == null || command.title().isBlank() ? current.title() : command.title();
        String description = command.description() == null ? current.description() : command.description();
        Integer estimatedMinutes = command.estimatedMinutes() == null ? current.estimatedMinutes() : command.estimatedMinutes();

        choreJdbcRepository.updateChoreRule(choreRuleId, title, description, estimatedMinutes, Instant.now());
        return new ChoreRuleView(choreRuleId, current.houseId(), current.spaceId(), title, description, estimatedMinutes);
    }

    @Transactional
    public void deleteChore(UUID userId, UUID choreRuleId) {
        ChoreJdbcRepository.ChoreRuleRecord current = choreJdbcRepository.findChoreRule(choreRuleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHORE_NOT_FOUND", "집안일 규칙을 찾을 수 없습니다."));
        houseService.assertActiveMember(userId, current.houseId());
        choreJdbcRepository.archiveChoreRule(choreRuleId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<TodayChoreView> listTodayChores(UUID userId, UUID houseId, LocalDate date) {
        houseService.assertActiveMember(userId, houseId);
        return choreJdbcRepository.listTodayChores(houseId, date).stream()
                .map(record -> new TodayChoreView(
                        record.choreInstanceId(),
                        record.houseId(),
                        record.title(),
                        record.spaceName(),
                        record.assigneeName(),
                        record.status(),
                        record.scheduledDate(),
                        record.completed()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public DailyProgressView getDailyProgress(UUID userId, UUID houseId, LocalDate date) {
        houseService.assertActiveMember(userId, houseId);
        ChoreJdbcRepository.DailyProgressRecord progressRecord = choreJdbcRepository.getDailyProgress(houseId, date);
        double completionRate = progressRecord.totalCount() == 0
                ? 0.0
                : (double) progressRecord.completedCount() / progressRecord.totalCount() * 100.0;
        return new DailyProgressView(progressRecord.completedCount(), progressRecord.totalCount(), completionRate);
    }

    @Transactional(readOnly = true)
    public long countPendingActions(UUID userId, UUID houseId, LocalDate date) {
        houseService.assertActiveMember(userId, houseId);
        return choreJdbcRepository.countPendingActions(userId, houseId, date);
    }

    @Transactional
    public void toggleCompletion(UUID userId, UUID choreInstanceId, ToggleCompletionCommand command) {
        ChoreJdbcRepository.ChoreInstanceRecord choreInstanceRecord = choreJdbcRepository.findChoreInstance(choreInstanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHORE_NOT_FOUND", "집안일 인스턴스를 찾을 수 없습니다."));
        houseService.assertActiveMember(userId, choreInstanceRecord.houseId());
        Instant now = Instant.now();
        choreJdbcRepository.completeChore(choreInstanceId, userId, command.memo(), command.proofImageUrl(), now);
        outboxJdbcRepository.appendEvent(
                "CHORE_INSTANCE",
                choreInstanceId,
                "chore.completed",
                """
                        {
                          "houseId":"%s",
                          "actorUserId":"%s",
                          "choreInstanceId":"%s"
                        }
                        """.formatted(choreInstanceRecord.houseId(), userId, choreInstanceId).trim(),
                now
        );
    }

    @Transactional
    public void cancelCompletion(UUID userId, UUID choreInstanceId) {
        ChoreJdbcRepository.ChoreInstanceRecord choreInstanceRecord = choreJdbcRepository.findChoreInstance(choreInstanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHORE_NOT_FOUND", "집안일 인스턴스를 찾을 수 없습니다."));
        houseService.assertActiveMember(userId, choreInstanceRecord.houseId());
        choreJdbcRepository.cancelCompletion(choreInstanceId, Instant.now());
    }

    @Transactional
    public void applySubstituteAssignment(UUID actingUserId, UUID choreInstanceId, UUID assigneeMembershipId) {
        ChoreJdbcRepository.ChoreInstanceRecord choreInstanceRecord = choreJdbcRepository.findChoreInstance(choreInstanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHORE_NOT_FOUND", "집안일 인스턴스를 찾을 수 없습니다."));
        houseService.assertActiveMember(actingUserId, choreInstanceRecord.houseId());
        Instant now = Instant.now();
        choreJdbcRepository.reassignChoreInstance(choreInstanceId, assigneeMembershipId, now);
        choreJdbcRepository.createChoreAssignment(UUID.randomUUID(), choreInstanceId, assigneeMembershipId, actingUserId, "SUBSTITUTE", now);
    }

    @Transactional
    public void rescheduleChoreInstance(UUID actingUserId, UUID choreInstanceId, LocalDate scheduledDate) {
        ChoreJdbcRepository.ChoreInstanceRecord choreInstanceRecord = choreJdbcRepository.findChoreInstance(choreInstanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHORE_NOT_FOUND", "집안일 인스턴스를 찾을 수 없습니다."));
        houseService.assertActiveMember(actingUserId, choreInstanceRecord.houseId());
        choreJdbcRepository.rescheduleChoreInstance(choreInstanceId, scheduledDate, Instant.now());
    }

    public record CreateChoreCommand(
            UUID spaceId,
            String title,
            String description,
            Integer estimatedMinutes,
            UUID defaultAssigneeMembershipId,
            Recurrence recurrence
    ) {
    }

    public record UpdateChoreCommand(
            String title,
            String description,
            Integer estimatedMinutes
    ) {
    }

    public record ToggleCompletionCommand(
            String memo,
            String proofImageUrl
    ) {
    }

    public record Recurrence(
            String frequency,
            Integer interval,
            List<String> daysOfWeek,
            LocalDate startDate
    ) {
    }

    public record ChoreRuleView(
            UUID choreRuleId,
            UUID houseId,
            UUID spaceId,
            String title,
            String description,
            Integer estimatedMinutes
    ) {
    }

    public record TodayChoreView(
            UUID choreInstanceId,
            UUID houseId,
            String title,
            String spaceName,
            String assigneeName,
            String status,
            LocalDate scheduledDate,
            boolean completed
    ) {
    }

    public record DailyProgressView(
            int completedCount,
            int totalCount,
            double completionRate
    ) {
    }
}
