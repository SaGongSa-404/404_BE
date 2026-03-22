package com.fourohfour.backend.modules.practice.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourohfour.backend.modules.content.application.GeneratedPracticeCard;
import com.fourohfour.backend.modules.content.application.PracticeCardSection;
import com.fourohfour.backend.modules.content.domain.ActionCardCategory;
import com.fourohfour.backend.modules.practice.domain.CardStatus;
import com.fourohfour.backend.modules.practice.domain.EnergyLevel;
import com.fourohfour.backend.modules.shared.api.ApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PracticeCardJdbcRepository {

    private static final int ACTION_TITLE_MAX_LENGTH = 140;
    private static final int ACTION_DETAIL_MAX_LENGTH = 500;
    private static final int DETAIL_TITLE_MAX_LENGTH = 140;
    private static final int DETAIL_BODY_MAX_LENGTH = 4000;
    private static final int DETAIL_SECTIONS_JSON_MAX_LENGTH = 12000;
    private static final int ENCOURAGEMENT_MAX_LENGTH = 255;
    private static final int RATIONALE_MAX_LENGTH = 255;
    private static final int COMPLETION_NOTE_MAX_LENGTH = 500;
    private static final int IDEA_OPTIONS_JSON_MAX_LENGTH = 4000;
    private static final int ENHANCEMENT_NOTE_MAX_LENGTH = 255;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PracticeCardJdbcRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public PracticeCardRecord saveCard(
            UUID userId,
            UUID savedContentId,
            GeneratedPracticeCard generatedPracticeCard,
            OffsetDateTime now,
            String enhancementStatus,
            String enhancementNote
    ) {
        UUID cardId = UUID.randomUUID();
        String actionTitle = limit(generatedPracticeCard.actionTitle(), ACTION_TITLE_MAX_LENGTH);
        String actionDetail = limit(generatedPracticeCard.actionDetail(), ACTION_DETAIL_MAX_LENGTH);
        String detailTitle = limit(generatedPracticeCard.detailTitle(), DETAIL_TITLE_MAX_LENGTH);
        String detailBody = limit(generatedPracticeCard.detailBody(), DETAIL_BODY_MAX_LENGTH);
        String detailSectionsJson = serializeDetailSections(generatedPracticeCard.detailSections());
        String encouragementMessage = limit(generatedPracticeCard.encouragementMessage(), ENCOURAGEMENT_MAX_LENGTH);
        String rationale = limit(generatedPracticeCard.rationale(), RATIONALE_MAX_LENGTH);
        String ideaOptionsJson = serializeIdeaOptions(generatedPracticeCard.ideaOptions());
        jdbcTemplate.update(
                """
                insert into practice_cards (
                    id, saved_content_id, user_id, category, status, action_title, action_detail,
                    detail_title, detail_body, detail_sections_json, idea_options_json, encouragement_message, rationale, estimated_minutes, energy_level, scheduled_for,
                    completed_at, completion_note, enhancement_status, enhancement_note, enhancement_updated_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                cardId,
                savedContentId,
                userId,
                generatedPracticeCard.category().name(),
                CardStatus.OPEN.name(),
                actionTitle,
                actionDetail,
                detailTitle,
                detailBody,
                detailSectionsJson,
                ideaOptionsJson,
                encouragementMessage,
                rationale,
                generatedPracticeCard.estimatedMinutes(),
                generatedPracticeCard.energyLevel().name(),
                generatedPracticeCard.scheduledFor(),
                null,
                null,
                enhancementStatus,
                limit(enhancementNote, ENHANCEMENT_NOTE_MAX_LENGTH),
                now,
                now,
                now
        );
        createEvent(cardId, userId, "CREATED", null, now);
        return getCard(userId, cardId);
    }

    public boolean replaceCardContentIfOpen(
            UUID userId,
            UUID cardId,
            GeneratedPracticeCard generatedPracticeCard,
            OffsetDateTime now
    ) {
        String actionTitle = limit(generatedPracticeCard.actionTitle(), ACTION_TITLE_MAX_LENGTH);
        String actionDetail = limit(generatedPracticeCard.actionDetail(), ACTION_DETAIL_MAX_LENGTH);
        String detailTitle = limit(generatedPracticeCard.detailTitle(), DETAIL_TITLE_MAX_LENGTH);
        String detailBody = limit(generatedPracticeCard.detailBody(), DETAIL_BODY_MAX_LENGTH);
        String detailSectionsJson = serializeDetailSections(generatedPracticeCard.detailSections());
        String encouragementMessage = limit(generatedPracticeCard.encouragementMessage(), ENCOURAGEMENT_MAX_LENGTH);
        String rationale = limit(generatedPracticeCard.rationale(), RATIONALE_MAX_LENGTH);
        String ideaOptionsJson = serializeIdeaOptions(generatedPracticeCard.ideaOptions());

        int updated = jdbcTemplate.update(
                """
                update practice_cards
                set category = ?, action_title = ?, action_detail = ?, detail_title = ?, detail_body = ?,
                    detail_sections_json = ?, idea_options_json = ?, encouragement_message = ?, rationale = ?,
                    estimated_minutes = ?, energy_level = ?, scheduled_for = ?, enhancement_status = ?, enhancement_note = ?, enhancement_updated_at = ?, updated_at = ?
                where id = ? and user_id = ? and status = ?
                """,
                generatedPracticeCard.category().name(),
                actionTitle,
                actionDetail,
                detailTitle,
                detailBody,
                detailSectionsJson,
                ideaOptionsJson,
                encouragementMessage,
                rationale,
                generatedPracticeCard.estimatedMinutes(),
                generatedPracticeCard.energyLevel().name(),
                generatedPracticeCard.scheduledFor(),
                "ENHANCED",
                limit(rationale, ENHANCEMENT_NOTE_MAX_LENGTH),
                now,
                now,
                cardId,
                userId,
                CardStatus.OPEN.name()
        );

        if (updated > 0) {
            createEvent(cardId, userId, "AI_ENHANCED", rationale, now);
            return true;
        }
        return false;
    }

    public void updateEnhancementStatus(
            UUID userId,
            UUID cardId,
            String enhancementStatus,
            String enhancementNote,
            String eventType,
            OffsetDateTime now
    ) {
        int updated = jdbcTemplate.update(
                """
                update practice_cards
                set enhancement_status = ?, enhancement_note = ?, enhancement_updated_at = ?, updated_at = ?
                where id = ? and user_id = ?
                """,
                enhancementStatus,
                limit(enhancementNote, ENHANCEMENT_NOTE_MAX_LENGTH),
                now,
                now,
                cardId,
                userId
        );
        if (updated > 0 && eventType != null && !eventType.isBlank()) {
            createEvent(cardId, userId, eventType, enhancementNote, now);
        }
    }

    public void reportIssue(UUID userId, UUID cardId, String reason, OffsetDateTime now) {
        getCard(userId, cardId);
        createEvent(cardId, userId, "REPORTED", reason, now);
    }

    public List<PracticeCardRecord> findDeck(UUID userId, LocalDate targetDate) {
        return jdbcTemplate.query(
                """
                select
                    pc.id,
                    pc.saved_content_id,
                    sc.url,
                    sc.title as source_title,
                    sc.note as source_note,
                    sc.source_domain,
                    pc.category,
                    pc.status,
                    pc.action_title,
                    pc.action_detail,
                    pc.detail_title,
                    pc.detail_body,
                    pc.detail_sections_json,
                    pc.idea_options_json,
                    pc.encouragement_message,
                    pc.rationale,
                    pc.estimated_minutes,
                    pc.energy_level,
                    pc.scheduled_for,
                    pc.completed_at,
                    pc.completion_note,
                    pc.enhancement_status,
                    pc.enhancement_note,
                    pc.enhancement_updated_at,
                    pc.created_at
                from practice_cards pc
                join saved_contents sc on sc.id = pc.saved_content_id
                where pc.user_id = ?
                  and (
                      pc.status = 'OPEN'
                      or cast(pc.completed_at as date) = ?
                  )
                order by
                    case when pc.status = 'OPEN' then 0 else 1 end,
                    pc.created_at desc
                """,
                practiceCardRowMapper(),
                userId,
                targetDate
        );
    }

    public PracticeCardRecord completeCard(
            UUID userId,
            UUID cardId,
            String completionNote,
            OffsetDateTime completedAt
    ) {
        String limitedCompletionNote = limit(completionNote, COMPLETION_NOTE_MAX_LENGTH);
        int updated = jdbcTemplate.update(
                """
                update practice_cards
                set status = ?, completed_at = ?, completion_note = ?, updated_at = ?
                where id = ? and user_id = ?
                """,
                CardStatus.DONE.name(),
                completedAt,
                limitedCompletionNote,
                completedAt,
                cardId,
                userId
        );
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "실천 카드를 찾을 수 없습니다.");
        }
        createEvent(cardId, userId, "COMPLETED", limitedCompletionNote, completedAt);
        return getCard(userId, cardId);
    }

    public PracticeCardRecord getCard(UUID userId, UUID cardId) {
        List<PracticeCardRecord> cards = jdbcTemplate.query(
                """
                select
                    pc.id,
                    pc.saved_content_id,
                    sc.url,
                    sc.title as source_title,
                    sc.note as source_note,
                    sc.source_domain,
                    pc.category,
                    pc.status,
                    pc.action_title,
                    pc.action_detail,
                    pc.detail_title,
                    pc.detail_body,
                    pc.detail_sections_json,
                    pc.idea_options_json,
                    pc.encouragement_message,
                    pc.rationale,
                    pc.estimated_minutes,
                    pc.energy_level,
                    pc.scheduled_for,
                    pc.completed_at,
                    pc.completion_note,
                    pc.enhancement_status,
                    pc.enhancement_note,
                    pc.enhancement_updated_at,
                    pc.created_at
                from practice_cards pc
                join saved_contents sc on sc.id = pc.saved_content_id
                where pc.user_id = ? and pc.id = ?
                """,
                practiceCardRowMapper(),
                userId,
                cardId
        );
        if (cards.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "실천 카드를 찾을 수 없습니다.");
        }
        return cards.getFirst();
    }

    public int countSavedContents(UUID userId, LocalDate weekStartDate, LocalDate weekEndDate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from saved_contents
                where user_id = ?
                  and cast(created_at as date) between ? and ?
                """,
                Integer.class,
                userId,
                weekStartDate,
                weekEndDate
        );
        return count == null ? 0 : count;
    }

    public int countCompletedCards(UUID userId, LocalDate weekStartDate, LocalDate weekEndDate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from practice_cards
                where user_id = ?
                  and status = 'DONE'
                  and cast(completed_at as date) between ? and ?
                """,
                Integer.class,
                userId,
                weekStartDate,
                weekEndDate
        );
        return count == null ? 0 : count;
    }

    public List<CategoryCountRecord> findSavedCategoryCounts(UUID userId, LocalDate weekStartDate, LocalDate weekEndDate) {
        return jdbcTemplate.query(
                """
                select category, count(*) as total_count
                from saved_contents
                where user_id = ?
                  and cast(created_at as date) between ? and ?
                group by category
                order by total_count desc, category asc
                """,
                (rs, rowNum) -> new CategoryCountRecord(
                        ActionCardCategory.valueOf(rs.getString("category")),
                        rs.getInt("total_count")
                ),
                userId,
                weekStartDate,
                weekEndDate
        );
    }

    public List<CategoryCountRecord> findCompletedCategoryCounts(UUID userId, LocalDate weekStartDate, LocalDate weekEndDate) {
        return jdbcTemplate.query(
                """
                select category, count(*) as total_count
                from practice_cards
                where user_id = ?
                  and status = 'DONE'
                  and cast(completed_at as date) between ? and ?
                group by category
                order by total_count desc, category asc
                """,
                (rs, rowNum) -> new CategoryCountRecord(
                        ActionCardCategory.valueOf(rs.getString("category")),
                        rs.getInt("total_count")
                ),
                userId,
                weekStartDate,
                weekEndDate
        );
    }

    public List<CategoryProgressRecord> findCategoryProgress(UUID userId, LocalDate weekStartDate, LocalDate weekEndDate) {
        return jdbcTemplate.query(
                """
                select
                    category,
                    sum(case when cast(created_at as date) between ? and ? then 1 else 0 end) as saved_count,
                    sum(case when completed_at is not null and cast(completed_at as date) between ? and ? then 1 else 0 end) as completed_count
                from practice_cards
                where user_id = ?
                group by category
                """,
                (rs, rowNum) -> new CategoryProgressRecord(
                        ActionCardCategory.valueOf(rs.getString("category")),
                        rs.getInt("saved_count"),
                        rs.getInt("completed_count")
                ),
                weekStartDate,
                weekEndDate,
                weekStartDate,
                weekEndDate,
                userId
        );
    }

    public List<CompletionRecord> findRecentCompletions(UUID userId, LocalDate weekStartDate, LocalDate weekEndDate) {
        return jdbcTemplate.query(
                """
                select action_title, category, completed_at
                from practice_cards
                where user_id = ?
                  and status = 'DONE'
                  and cast(completed_at as date) between ? and ?
                order by completed_at desc
                limit 5
                """,
                (rs, rowNum) -> new CompletionRecord(
                        rs.getString("action_title"),
                        ActionCardCategory.valueOf(rs.getString("category")),
                        rs.getObject("completed_at", OffsetDateTime.class)
                ),
                userId,
                weekStartDate,
                weekEndDate
        );
    }

    private void createEvent(UUID cardId, UUID userId, String eventType, String note, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                insert into practice_card_events (id, practice_card_id, user_id, event_type, note, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                cardId,
                userId,
                eventType,
                limit(note, COMPLETION_NOTE_MAX_LENGTH),
                createdAt
        );
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private RowMapper<PracticeCardRecord> practiceCardRowMapper() {
        return (rs, rowNum) -> new PracticeCardRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("saved_content_id", UUID.class),
                rs.getString("url"),
                rs.getString("source_title"),
                rs.getString("source_note"),
                rs.getString("source_domain"),
                ActionCardCategory.valueOf(rs.getString("category")),
                CardStatus.valueOf(rs.getString("status")),
                rs.getString("action_title"),
                rs.getString("action_detail"),
                rs.getString("detail_title"),
                rs.getString("detail_body"),
                deserializeDetailSections(rs.getString("detail_sections_json")),
                deserializeIdeaOptions(rs.getString("idea_options_json")),
                rs.getString("encouragement_message"),
                rs.getString("rationale"),
                rs.getInt("estimated_minutes"),
                EnergyLevel.valueOf(rs.getString("energy_level")),
                rs.getObject("scheduled_for", LocalDate.class),
                rs.getObject("completed_at", OffsetDateTime.class),
                rs.getString("completion_note"),
                rs.getString("enhancement_status"),
                rs.getString("enhancement_note"),
                rs.getObject("enhancement_updated_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    public EnhancementMonitoringSnapshot getEnhancementMonitoringSnapshot(UUID userId) {
        int pending = countEnhancementStatus(userId, "PENDING");
        int enhanced = countEnhancementStatus(userId, "ENHANCED");
        int skipped = countEnhancementStatus(userId, "SKIPPED");
        int failed = countEnhancementStatus(userId, "FAILED");
        int reported = countReported(userId);
        List<EnhancementEventRecord> recent = jdbcTemplate.query(
                """
                select practice_card_id, event_type, note, created_at
                from practice_card_events
                where user_id = ?
                  and event_type in ('AI_PENDING', 'AI_ENHANCED', 'AI_SKIPPED', 'AI_FAILED', 'REPORTED')
                order by created_at desc
                limit 10
                """,
                (rs, rowNum) -> new EnhancementEventRecord(
                        rs.getObject("practice_card_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("note"),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                userId
        );
        return new EnhancementMonitoringSnapshot(pending, enhanced, skipped, failed, reported, recent);
    }

    private int countEnhancementStatus(UUID userId, String enhancementStatus) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from practice_cards where user_id = ? and enhancement_status = ?",
                Integer.class,
                userId,
                enhancementStatus
        );
        return count == null ? 0 : count;
    }

    private int countReported(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from practice_card_events where user_id = ? and event_type = 'REPORTED'",
                Integer.class,
                userId
        );
        return count == null ? 0 : count;
    }

    public record PracticeCardRecord(
            UUID id,
            UUID savedContentId,
            String url,
            String sourceTitle,
            String sourceNote,
            String sourceDomain,
            ActionCardCategory category,
            CardStatus status,
            String actionTitle,
            String actionDetail,
            String detailTitle,
            String detailBody,
            List<PracticeCardSection> detailSections,
            List<String> ideaOptions,
            String encouragementMessage,
            String rationale,
            int estimatedMinutes,
            EnergyLevel energyLevel,
            LocalDate scheduledFor,
            OffsetDateTime completedAt,
            String completionNote,
            String enhancementStatus,
            String enhancementNote,
            OffsetDateTime enhancementUpdatedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record CategoryCountRecord(ActionCardCategory category, int count) {
    }

    public record CategoryProgressRecord(ActionCardCategory category, int savedCount, int completedCount) {
    }

    public record CompletionRecord(String actionTitle, ActionCardCategory category, OffsetDateTime completedAt) {
    }

    public record EnhancementEventRecord(
            UUID practiceCardId,
            String eventType,
            String note,
            OffsetDateTime createdAt
    ) {
    }

    public record EnhancementMonitoringSnapshot(
            int pendingCount,
            int enhancedCount,
            int skippedCount,
            int failedCount,
            int reportedCount,
            List<EnhancementEventRecord> recentEvents
    ) {
    }

    private String serializeIdeaOptions(List<String> ideaOptions) {
        if (ideaOptions == null || ideaOptions.isEmpty()) {
            return null;
        }
        try {
            return limit(objectMapper.writeValueAsString(ideaOptions), IDEA_OPTIONS_JSON_MAX_LENGTH);
        } catch (Exception exception) {
            return null;
        }
    }

    private List<String> deserializeIdeaOptions(String ideaOptionsJson) {
        if (ideaOptionsJson == null || ideaOptionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(ideaOptionsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String serializeDetailSections(List<PracticeCardSection> detailSections) {
        if (detailSections == null || detailSections.isEmpty()) {
            return null;
        }
        try {
            return limit(objectMapper.writeValueAsString(detailSections), DETAIL_SECTIONS_JSON_MAX_LENGTH);
        } catch (Exception exception) {
            return null;
        }
    }

    private List<PracticeCardSection> deserializeDetailSections(String detailSectionsJson) {
        if (detailSectionsJson == null || detailSectionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(detailSectionsJson, new TypeReference<List<PracticeCardSection>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }
}
