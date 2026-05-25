package com.sagongsa.backend.decision;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sagongsa.backend.decision.DecisionCompleteRequest.SelfCheckAnswerRequest;
import com.sagongsa.backend.decision.DecisionResultUpdateRequest.SelfCheckAnswerUpdateRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DecisionServiceUnitTest {

	private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ITEM_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID DECISION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

	private JdbcTemplate jdbcTemplate;
	private DecisionService service;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		service = new DecisionService(jdbcTemplate);
	}

	@Test
	void completeRejectsDuplicatedSelfCheckQuestionCodeBeforeDatabaseAccess() {
		DecisionCompleteRequest request = completeRequest(List.of(
			answer("NEED", true),
			answer("BUDGET", false),
			answer("NEED", true),
			answer("DELAY", false)
		));

		assertThatThrownBy(() -> service.complete(USER_ID, request))
			.isInstanceOf(DecisionBadRequestException.class)
			.hasMessage("selfCheckAnswers must not contain duplicated questionCode.");
		verifyNoInteractions(jdbcTemplate);
	}

	@Test
	void completeRejectsUnknownSelfCheckQuestionCodeBeforeDatabaseAccess() {
		DecisionCompleteRequest request = completeRequest(List.of(
			answer("NEED", true),
			answer("BUDGET", false),
			answer("UNKNOWN", true),
			answer("DELAY", false)
		));

		assertThatThrownBy(() -> service.complete(USER_ID, request))
			.isInstanceOf(DecisionBadRequestException.class)
			.hasMessage("questionCode must be one of NEED, BUDGET, ALTERNATIVE, DELAY.");
		verifyNoInteractions(jdbcTemplate);
	}

	@Test
	void completeRejectsTooLongRationaleTextBeforeDatabaseAccess() {
		DecisionCompleteRequest request = new DecisionCompleteRequest(
			ITEM_ID,
			"GO",
			10000,
			"a".repeat(1001),
			validAnswers()
		);

		assertThatThrownBy(() -> service.complete(USER_ID, request))
			.isInstanceOf(DecisionBadRequestException.class)
			.hasMessage("rationaleText must be 1000 characters or fewer.");
		verifyNoInteractions(jdbcTemplate);
	}

	@Test
	void updateRejectsTooLongChangeReasonBeforeDatabaseAccess() {
		DecisionResultUpdateRequest request = new DecisionResultUpdateRequest(
			"STOP",
			null,
			"a".repeat(1001),
			null
		);

		assertThatThrownBy(() -> service.updateResult(USER_ID, DECISION_ID, request))
			.isInstanceOf(DecisionBadRequestException.class)
			.hasMessage("changeReason must be 1000 characters or fewer.");
		verifyNoInteractions(jdbcTemplate);
	}

	@Test
	void updateRejectsDuplicatedSelfCheckQuestionCodeBeforeDatabaseAccess() {
		DecisionResultUpdateRequest request = new DecisionResultUpdateRequest(
			"GO",
			10000,
			null,
			List.of(
				updateAnswer("NEED", true),
				updateAnswer("BUDGET", false),
				updateAnswer("ALTERNATIVE", false),
				updateAnswer("NEED", true)
			)
		);

		assertThatThrownBy(() -> service.updateResult(USER_ID, DECISION_ID, request))
			.isInstanceOf(DecisionBadRequestException.class)
			.hasMessage("selfCheckAnswers must not contain duplicated questionCode.");
		verifyNoInteractions(jdbcTemplate);
	}

	private static DecisionCompleteRequest completeRequest(List<SelfCheckAnswerRequest> answers) {
		return new DecisionCompleteRequest(ITEM_ID, "GO", 10000, null, answers);
	}

	private static List<SelfCheckAnswerRequest> validAnswers() {
		return List.of(
			answer("NEED", true),
			answer("BUDGET", false),
			answer("ALTERNATIVE", false),
			answer("DELAY", false)
		);
	}

	private static SelfCheckAnswerRequest answer(String questionCode, boolean answerBoolean) {
		return new SelfCheckAnswerRequest(questionCode, answerBoolean);
	}

	private static SelfCheckAnswerUpdateRequest updateAnswer(String questionCode, boolean answerBoolean) {
		return new SelfCheckAnswerUpdateRequest(questionCode, answerBoolean);
	}
}
