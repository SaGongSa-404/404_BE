package com.sagongsa.backend.reflection;

import com.sagongsa.backend.domain.enums.ReflectionRegretLevel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PurchaseReflectionService {

	private final JdbcTemplate jdbcTemplate;

	public PurchaseReflectionService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public PurchaseReflectionResponse create(UUID userId, PurchaseReflectionRequest request) {
		NormalizedRequest normalized = normalize(request);
		requireUsableUser(userId);
		DecisionSnapshot decision = findGoDecision(userId, normalized.decisionId());
		if (reflectionExists(normalized.decisionId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Reflection already exists for this decision.");
		}
		UUID reminderId = findReminderId(normalized.decisionId());
		UUID reflectionId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		try {
			jdbcTemplate.update(
				"""
				insert into purchase_reflections (
					id, user_id, item_id, decision_id, reminder_id, satisfaction_score,
					regret_level, still_using, reflection_note, reflected_at, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				reflectionId,
				userId,
				decision.itemId(),
				decision.decisionId(),
				reminderId,
				normalized.satisfactionScore(),
				normalized.regretLevel().name(),
				normalized.stillUsing(),
				normalized.reflectionNote(),
				now,
				now,
				now
			);
		}
		catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Reflection already exists for this decision.");
		}
		return findById(userId, reflectionId);
	}

	private NormalizedRequest normalize(PurchaseReflectionRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
		}
		if (request.decisionId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decisionId is required.");
		}
		Integer satisfactionScore = request.satisfactionScore();
		if (satisfactionScore != null && (satisfactionScore < 1 || satisfactionScore > 5)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "satisfactionScore must be between 1 and 5.");
		}
		ReflectionRegretLevel regretLevel = parseRegretLevel(request.regretLevel());
		String note = cleanOptional(request.reflectionNote(), 500);
		return new NormalizedRequest(request.decisionId(), satisfactionScore, regretLevel, request.stillUsing(), note);
	}

	private ReflectionRegretLevel parseRegretLevel(String rawRegretLevel) {
		if (!StringUtils.hasText(rawRegretLevel)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regretLevel is required.");
		}
		try {
			return ReflectionRegretLevel.valueOf(rawRegretLevel.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regretLevel must be NONE, LOW, MEDIUM, or HIGH.");
		}
	}

	private String cleanOptional(String value, int maxLength) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String cleaned = value.trim();
		if (cleaned.length() > maxLength) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reflectionNote must be 500 characters or fewer.");
		}
		return cleaned;
	}

	private void requireUsableUser(UUID userId) {
		try {
			UserState user = jdbcTemplate.queryForObject(
				"select status, onboarding_status from users where id = ?",
				(rs, rowNumber) -> new UserState(rs.getString("status"), rs.getString("onboarding_status")),
				userId
			);
			if (!Objects.equals(user.status(), "ACTIVE") || !Objects.equals(user.onboardingStatus(), "COMPLETED")) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reflection can be used only after onboarding.");
			}
		}
		catch (EmptyResultDataAccessException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found.");
		}
	}

	private DecisionSnapshot findGoDecision(UUID userId, UUID decisionId) {
		try {
			DecisionSnapshot decision = jdbcTemplate.queryForObject(
				"""
				select id, item_id, result
				from purchase_decisions
				where user_id = ?
				  and id = ?
				""",
				(rs, rowNumber) -> new DecisionSnapshot(
					rs.getObject("id", UUID.class),
					rs.getObject("item_id", UUID.class),
					rs.getString("result")
				),
				userId,
				decisionId
			);
			if (!Objects.equals(decision.result(), "GO")) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Reflection can be created only for GO decisions.");
			}
			return decision;
		}
		catch (EmptyResultDataAccessException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase decision was not found.");
		}
	}

	private boolean reflectionExists(UUID decisionId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"select exists(select 1 from purchase_reflections where decision_id = ?)",
			Boolean.class,
			decisionId
		);
		return Boolean.TRUE.equals(exists);
	}

	private UUID findReminderId(UUID decisionId) {
		return jdbcTemplate.query(
				"""
				select id
				from reminder_schedules
				where decision_id = ?
				  and reminder_type = 'REGRET_CHECK_7_DAYS'
				limit 1
				""",
				(rs, rowNumber) -> rs.getObject("id", UUID.class),
				decisionId
			)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private PurchaseReflectionResponse findById(UUID userId, UUID reflectionId) {
		try {
			return jdbcTemplate.queryForObject(
				"""
				select id, decision_id, item_id, reminder_id, satisfaction_score,
				       regret_level, still_using, reflection_note, reflected_at
				from purchase_reflections
				where user_id = ?
				  and id = ?
				""",
				this::mapReflection,
				userId,
				reflectionId
			);
		}
		catch (EmptyResultDataAccessException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reflection was not found.");
		}
	}

	private PurchaseReflectionResponse mapReflection(ResultSet rs, int rowNumber) throws SQLException {
		int score = rs.getInt("satisfaction_score");
		boolean scoreWasNull = rs.wasNull();
		return new PurchaseReflectionResponse(
			rs.getObject("id", UUID.class),
			rs.getObject("decision_id", UUID.class),
			rs.getObject("item_id", UUID.class),
			rs.getObject("reminder_id", UUID.class),
			scoreWasNull ? null : score,
			rs.getString("regret_level"),
			rs.getObject("still_using", Boolean.class),
			rs.getString("reflection_note"),
			readInstant(rs, "reflected_at")
		);
	}

	private Instant readInstant(ResultSet rs, String columnName) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(columnName);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private record NormalizedRequest(
		UUID decisionId,
		Integer satisfactionScore,
		ReflectionRegretLevel regretLevel,
		Boolean stillUsing,
		String reflectionNote
	) {
	}

	private record UserState(String status, String onboardingStatus) {
	}

	private record DecisionSnapshot(UUID decisionId, UUID itemId, String result) {
	}
}
