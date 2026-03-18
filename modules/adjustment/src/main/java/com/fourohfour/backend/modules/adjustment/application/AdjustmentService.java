package com.fourohfour.backend.modules.adjustment.application;

import com.fourohfour.backend.modules.adjustment.infrastructure.AdjustmentJdbcRepository;
import com.fourohfour.backend.modules.chore.application.ChoreService;
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
public class AdjustmentService {

    private final AdjustmentJdbcRepository adjustmentJdbcRepository;
    private final HouseService houseService;
    private final ChoreService choreService;
    private final OutboxJdbcRepository outboxJdbcRepository;

    public AdjustmentService(
            AdjustmentJdbcRepository adjustmentJdbcRepository,
            HouseService houseService,
            ChoreService choreService,
            OutboxJdbcRepository outboxJdbcRepository
    ) {
        this.adjustmentJdbcRepository = adjustmentJdbcRepository;
        this.houseService = houseService;
        this.choreService = choreService;
        this.outboxJdbcRepository = outboxJdbcRepository;
    }

    @Transactional
    public AdjustmentRequestView requestSubstitute(UUID userId, UUID houseId, CreateAdjustmentCommand command) {
        HouseService.MembershipView requesterMembership = houseService.assertActiveMember(userId, houseId);
        Instant now = Instant.now();
        UUID adjustmentRequestId = UUID.randomUUID();
        adjustmentJdbcRepository.createRequest(
                adjustmentRequestId,
                houseId,
                command.choreInstanceId(),
                requesterMembership.membershipId(),
                "SUBSTITUTE",
                command.reason(),
                null,
                command.expiresAt(),
                now
        );
        emitOutboxEvent("adjustment.requested", adjustmentRequestId, houseId, userId, command.choreInstanceId(), "SUBSTITUTE");
        return getRequestView(adjustmentRequestId);
    }

    @Transactional
    public AdjustmentRequestView requestReschedule(UUID userId, UUID houseId, CreateAdjustmentCommand command) {
        HouseService.MembershipView requesterMembership = houseService.assertActiveMember(userId, houseId);
        if (command.requestedDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "변경할 날짜가 필요합니다.");
        }
        Instant now = Instant.now();
        UUID adjustmentRequestId = UUID.randomUUID();
        adjustmentJdbcRepository.createRequest(
                adjustmentRequestId,
                houseId,
                command.choreInstanceId(),
                requesterMembership.membershipId(),
                "RESCHEDULE",
                command.reason(),
                command.requestedDate(),
                command.expiresAt(),
                now
        );
        emitOutboxEvent("adjustment.requested", adjustmentRequestId, houseId, userId, command.choreInstanceId(), "RESCHEDULE");
        return getRequestView(adjustmentRequestId);
    }

    @Transactional
    public void acceptAdjustment(UUID userId, UUID adjustmentRequestId) {
        AdjustmentJdbcRepository.AdjustmentRequestRecord requestRecord = assertOpenRequest(adjustmentRequestId);
        HouseService.MembershipView responderMembership = houseService.assertActiveMember(userId, requestRecord.houseId());
        Instant now = Instant.now();
        adjustmentJdbcRepository.createResponse(UUID.randomUUID(), adjustmentRequestId, responderMembership.membershipId(), "ACCEPT", now);
        adjustmentJdbcRepository.updateStatus(adjustmentRequestId, "ACCEPTED", now);

        if ("SUBSTITUTE".equals(requestRecord.requestType())) {
            choreService.applySubstituteAssignment(userId, requestRecord.choreInstanceId(), responderMembership.membershipId());
            adjustmentJdbcRepository.incrementRewardCounter(responderMembership.membershipId(), now);
        } else if ("RESCHEDULE".equals(requestRecord.requestType()) && requestRecord.requestedDate() != null) {
            choreService.rescheduleChoreInstance(userId, requestRecord.choreInstanceId(), requestRecord.requestedDate());
        }

        emitOutboxEvent("adjustment.accepted", adjustmentRequestId, requestRecord.houseId(), userId, requestRecord.choreInstanceId(), requestRecord.requestType());
    }

    @Transactional
    public void rejectAdjustment(UUID userId, UUID adjustmentRequestId) {
        AdjustmentJdbcRepository.AdjustmentRequestRecord requestRecord = assertOpenRequest(adjustmentRequestId);
        HouseService.MembershipView responderMembership = houseService.assertActiveMember(userId, requestRecord.houseId());
        Instant now = Instant.now();
        adjustmentJdbcRepository.createResponse(UUID.randomUUID(), adjustmentRequestId, responderMembership.membershipId(), "REJECT", now);
        adjustmentJdbcRepository.updateStatus(adjustmentRequestId, "REJECTED", now);
        emitOutboxEvent("adjustment.rejected", adjustmentRequestId, requestRecord.houseId(), userId, requestRecord.choreInstanceId(), requestRecord.requestType());
    }

    @Transactional
    public void cancelAdjustment(UUID userId, UUID adjustmentRequestId) {
        AdjustmentJdbcRepository.AdjustmentRequestRecord requestRecord = adjustmentJdbcRepository.findRequest(adjustmentRequestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADJUSTMENT_NOT_FOUND", "요청을 찾을 수 없습니다."));
        HouseService.MembershipView requesterMembership = houseService.assertActiveMember(userId, requestRecord.houseId());
        if (!requesterMembership.membershipId().equals(requestRecord.requesterMembershipId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADJUSTMENT_NOT_OPEN", "요청자만 취소할 수 있습니다.");
        }
        if (!"OPEN".equals(requestRecord.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ADJUSTMENT_NOT_OPEN", "이미 종료된 요청입니다.");
        }
        adjustmentJdbcRepository.updateStatus(adjustmentRequestId, "CANCELLED", Instant.now());
    }

    @Transactional(readOnly = true)
    public List<AdjustmentRequestView> listOpenRequests(UUID userId, UUID houseId) {
        houseService.assertActiveMember(userId, houseId);
        return adjustmentJdbcRepository.listOpenRequests(houseId).stream()
                .map(record -> new AdjustmentRequestView(
                        record.adjustmentRequestId(),
                        record.houseId(),
                        record.choreInstanceId(),
                        record.requestType(),
                        record.reason(),
                        record.requestedDate(),
                        record.status(),
                        record.expiresAt(),
                        record.choreTitle(),
                        record.requesterName()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdjustmentRequestView getRequestView(UUID adjustmentRequestId) {
        AdjustmentJdbcRepository.AdjustmentRequestRecord requestRecord = adjustmentJdbcRepository.findRequest(adjustmentRequestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADJUSTMENT_NOT_FOUND", "요청을 찾을 수 없습니다."));
        AdjustmentJdbcRepository.AdjustmentRequestSummaryRecord record = adjustmentJdbcRepository.listOpenRequests(requestRecord.houseId()).stream()
                .filter(item -> item.adjustmentRequestId().equals(adjustmentRequestId))
                .findFirst()
                .orElse(new AdjustmentJdbcRepository.AdjustmentRequestSummaryRecord(
                        requestRecord.adjustmentRequestId(),
                        requestRecord.houseId(),
                        requestRecord.choreInstanceId(),
                        requestRecord.requesterMembershipId(),
                        requestRecord.requestType(),
                        requestRecord.reason(),
                        requestRecord.requestedDate(),
                        requestRecord.status(),
                        requestRecord.expiresAt(),
                        null,
                        null
                ));
        return new AdjustmentRequestView(
                record.adjustmentRequestId(),
                record.houseId(),
                record.choreInstanceId(),
                record.requestType(),
                record.reason(),
                record.requestedDate(),
                record.status(),
                record.expiresAt(),
                record.choreTitle(),
                record.requesterName()
        );
    }

    private AdjustmentJdbcRepository.AdjustmentRequestRecord assertOpenRequest(UUID adjustmentRequestId) {
        AdjustmentJdbcRepository.AdjustmentRequestRecord requestRecord = adjustmentJdbcRepository.findRequest(adjustmentRequestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADJUSTMENT_NOT_FOUND", "요청을 찾을 수 없습니다."));
        if (!"OPEN".equals(requestRecord.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ADJUSTMENT_NOT_OPEN", "처리 가능한 요청이 아닙니다.");
        }
        return requestRecord;
    }

    private void emitOutboxEvent(String eventType, UUID adjustmentRequestId, UUID houseId, UUID actorUserId, UUID choreInstanceId, String requestType) {
        Instant now = Instant.now();
        outboxJdbcRepository.appendEvent(
                "ADJUSTMENT_REQUEST",
                adjustmentRequestId,
                eventType,
                """
                        {
                          "houseId":"%s",
                          "actorUserId":"%s",
                          "adjustmentRequestId":"%s",
                          "choreInstanceId":"%s",
                          "requestType":"%s"
                        }
                        """.formatted(houseId, actorUserId, adjustmentRequestId, choreInstanceId, requestType).trim(),
                now
        );
    }

    public record CreateAdjustmentCommand(
            UUID choreInstanceId,
            String reason,
            LocalDate requestedDate,
            Instant expiresAt
    ) {
    }

    public record AdjustmentRequestView(
            UUID adjustmentRequestId,
            UUID houseId,
            UUID choreInstanceId,
            String requestType,
            String reason,
            LocalDate requestedDate,
            String status,
            Instant expiresAt,
            String choreTitle,
            String requesterName
    ) {
    }
}

