package com.sagongsa.backend.domain.user;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.common.BaseEntity;
import com.sagongsa.backend.domain.enums.SurveyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "survey_response_sets",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_survey_response_sets_user_type", columnNames = {"user_id", "survey_type"})
	}
)
public class SurveyResponseSet extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private SurveyType surveyType;

	@Column(nullable = false)
	private Instant submittedAt;

	protected SurveyResponseSet() {
	}
}
