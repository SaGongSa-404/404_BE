package com.sagongsa.backend.domain.decision;

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
	name = "self_check_answers",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_self_check_answers_response_question", columnNames = {"response_set_id", "question_code"})
	}
)
public class SelfCheckAnswer extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "response_set_id", nullable = false)
	private SelfCheckResponseSet responseSet;

	@Column(nullable = false, length = 80)
	private String questionCode;

	@Column(nullable = false)
	private boolean answerBoolean;

	protected SelfCheckAnswer() {
	}
}
