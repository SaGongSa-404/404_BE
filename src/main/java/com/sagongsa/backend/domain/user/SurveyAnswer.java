package com.sagongsa.backend.domain.user;

import com.sagongsa.backend.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "survey_answers",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_survey_answers_response_question", columnNames = {"response_set_id", "question_code"})
	}
)
public class SurveyAnswer extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "response_set_id", nullable = false)
	private SurveyResponseSet responseSet;

	@Column(nullable = false, length = 80)
	private String questionCode;

	@Column(columnDefinition = "text")
	private String answerText;

	@Column
	private Integer answerNumber;

	@Column(length = 80)
	private String answerChoice;

	protected SurveyAnswer() {
	}
}
