package com.fourohfour.backend.modules.review.application;

import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.shared.api.ApiException;
import com.fourohfour.backend.modules.review.infrastructure.ReviewJdbcRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewJdbcRepository reviewJdbcRepository;
    private final HouseService houseService;

    public ReviewService(ReviewJdbcRepository reviewJdbcRepository, HouseService houseService) {
        this.reviewJdbcRepository = reviewJdbcRepository;
        this.houseService = houseService;
    }

    @Transactional(readOnly = true)
    public WeeklyRecapView getLatestWeeklyRecap(UUID userId, UUID houseId) {
        houseService.assertActiveMember(userId, houseId);
        ReviewJdbcRepository.WeeklySnapshotRecord snapshotRecord = reviewJdbcRepository.findLatestSnapshot(houseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_READY", "주간 회고가 아직 생성되지 않았습니다."));
        return toWeeklyRecapView(snapshotRecord, reviewJdbcRepository.hasSubmittedSatisfaction(snapshotRecord.snapshotId(), userId));
    }

    @Transactional(readOnly = true)
    public Optional<WeeklyRecapView> findLatestWeeklyRecap(UUID userId, UUID houseId) {
        houseService.assertActiveMember(userId, houseId);
        return reviewJdbcRepository.findLatestSnapshot(houseId)
                .map(snapshotRecord -> toWeeklyRecapView(snapshotRecord, reviewJdbcRepository.hasSubmittedSatisfaction(snapshotRecord.snapshotId(), userId)));
    }

    @Transactional(readOnly = true)
    public List<WeeklyRecapView> listPastWeeklyRecaps(UUID userId, UUID houseId) {
        houseService.assertActiveMember(userId, houseId);
        return reviewJdbcRepository.listSnapshots(houseId).stream()
                .map(record -> toWeeklyRecapView(record, reviewJdbcRepository.hasSubmittedSatisfaction(record.snapshotId(), userId)))
                .toList();
    }

    @Transactional
    public void submitWeeklySatisfaction(UUID userId, UUID houseId, UUID snapshotId, int score, String comment) {
        houseService.assertActiveMember(userId, houseId);
        reviewJdbcRepository.submitSatisfaction(snapshotId, userId, score, comment, Instant.now());
    }

    @Transactional
    public void buildWeeklySnapshot(LocalDate weekStartDate) {
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        Instant now = Instant.now();
        for (UUID houseId : reviewJdbcRepository.listActiveHouseIds()) {
            UUID snapshotId = reviewJdbcRepository.findSnapshotIdByHouseAndWeek(houseId, weekStartDate)
                    .orElseGet(() -> reviewJdbcRepository.createSnapshot(houseId, weekStartDate, weekEndDate, now));
            reviewJdbcRepository.replaceHouseStats(snapshotId, houseId, weekStartDate, weekEndDate, now);
            reviewJdbcRepository.replaceMemberStats(snapshotId, houseId, weekStartDate, weekEndDate, now);
        }
    }

    private WeeklyRecapView toWeeklyRecapView(ReviewJdbcRepository.WeeklySnapshotRecord snapshotRecord, boolean satisfactionSubmitted) {
        List<WeeklyMemberRecapView> memberStats = reviewJdbcRepository.listMemberStats(snapshotRecord.snapshotId()).stream()
                .map(record -> new WeeklyMemberRecapView(
                        record.membershipId(),
                        record.nickname(),
                        record.assignedChores(),
                        record.completedChores(),
                        record.completionRate(),
                        record.substituteAcceptances()
                ))
                .toList();

        return new WeeklyRecapView(
                snapshotRecord.snapshotId(),
                snapshotRecord.houseId(),
                snapshotRecord.weekStartDate(),
                snapshotRecord.weekEndDate(),
                snapshotRecord.totalChores(),
                snapshotRecord.completedChores(),
                snapshotRecord.completionRate(),
                snapshotRecord.acceptedAdjustments(),
                memberStats,
                satisfactionSubmitted
        );
    }

    public record WeeklyRecapView(
            UUID snapshotId,
            UUID houseId,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            int totalChores,
            int completedChores,
            double completionRate,
            int acceptedAdjustments,
            List<WeeklyMemberRecapView> memberStats,
            boolean satisfactionSubmitted
    ) {
    }

    public record WeeklyMemberRecapView(
            UUID membershipId,
            String nickname,
            int assignedChores,
            int completedChores,
            double completionRate,
            int substituteAcceptances
    ) {
    }
}
