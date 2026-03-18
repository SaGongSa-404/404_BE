package com.fourohfour.backend.modules.notification.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.notification.infrastructure.NotificationJdbcRepository;
import com.fourohfour.backend.packages.events.OutboxJdbcRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationJdbcRepository notificationJdbcRepository;
    private final HouseService houseService;
    private final ObjectMapper objectMapper;

    public NotificationService(
            NotificationJdbcRepository notificationJdbcRepository,
            HouseService houseService,
            ObjectMapper objectMapper
    ) {
        this.notificationJdbcRepository = notificationJdbcRepository;
        this.houseService = houseService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public NotificationFeedView listHouseNotifications(UUID userId, UUID houseId, Instant cursor, int limit) {
        houseService.assertActiveMember(userId, houseId);
        List<NotificationItemView> items = notificationJdbcRepository.listNotifications(userId, houseId, cursor, limit).stream()
                .map(record -> new NotificationItemView(
                        record.notificationId(),
                        record.houseId(),
                        record.type(),
                        record.title(),
                        record.body(),
                        parsePayload(record.payloadJson()),
                        record.occurredAt(),
                        record.isRead()
                ))
                .toList();
        String nextCursor = items.isEmpty() ? null : items.get(items.size() - 1).occurredAt().toString();
        return new NotificationFeedView(items, notificationJdbcRepository.unreadCount(userId, houseId), nextCursor);
    }

    @Transactional
    public void markAsRead(UUID userId, UUID notificationId) {
        notificationJdbcRepository.markAsRead(notificationId, userId, Instant.now());
    }

    @Transactional
    public void markAsReadBatch(UUID userId, List<UUID> notificationIds) {
        Instant now = Instant.now();
        for (UUID notificationId : notificationIds) {
            notificationJdbcRepository.markAsRead(notificationId, userId, now);
        }
    }

    @Transactional
    public void registerPushDevice(UUID userId, RegisterPushDeviceCommand command) {
        notificationJdbcRepository.registerPushDevice(userId, command.platform(), command.pushToken(), command.deviceId(), Instant.now());
    }

    @Transactional
    public void deactivatePushDevice(UUID userId, UUID deviceId) {
        notificationJdbcRepository.deactivatePushDevice(userId, deviceId, Instant.now());
    }

    @Transactional
    public void createFromOutboxEvent(OutboxJdbcRepository.OutboxEventRecord eventRecord) {
        Map<String, Object> payload = parsePayload(eventRecord.payloadJson());
        UUID houseId = UUID.fromString(String.valueOf(payload.get("houseId")));
        UUID actorUserId = payload.get("actorUserId") == null ? null : UUID.fromString(String.valueOf(payload.get("actorUserId")));
        NotificationTemplate template = toTemplate(eventRecord.eventType(), payload);
        Instant now = Instant.now();
        UUID notificationId = notificationJdbcRepository.createNotification(
                houseId,
                actorUserId,
                template.type(),
                template.title(),
                template.body(),
                eventRecord.payloadJson(),
                now,
                now
        );
        notificationJdbcRepository.createReceiptsForHouseMembers(notificationId, houseId, now);
    }

    private NotificationTemplate toTemplate(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "adjustment.requested" -> new NotificationTemplate("ADJUSTMENT_REQUEST", "대타 요청이 도착했어요", "새로운 대타 또는 일정 조정 요청이 등록됐어요.");
            case "adjustment.accepted" -> new NotificationTemplate("ADJUSTMENT_RESULT", "요청이 수락됐어요", "대타 또는 일정 조정 요청이 수락됐어요.");
            case "adjustment.rejected" -> new NotificationTemplate("ADJUSTMENT_RESULT", "요청이 거절됐어요", "대타 또는 일정 조정 요청이 거절됐어요.");
            case "chore.completed" -> new NotificationTemplate("SYSTEM", "집안일이 완료됐어요", "오늘 집안일 하나가 완료 처리됐어요.");
            default -> new NotificationTemplate("SYSTEM", "새 알림", "새로운 이벤트가 도착했어요.");
        };
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse notification payload", exception);
        }
    }

    public record RegisterPushDeviceCommand(
            String platform,
            String pushToken,
            String deviceId
    ) {
    }

    public record NotificationFeedView(
            List<NotificationItemView> items,
            long unreadCount,
            String nextCursor
    ) {
    }

    public record NotificationItemView(
            UUID notificationId,
            UUID houseId,
            String type,
            String title,
            String body,
            Map<String, Object> payload,
            Instant occurredAt,
            boolean isRead
    ) {
    }

    private record NotificationTemplate(
            String type,
            String title,
            String body
    ) {
    }
}

