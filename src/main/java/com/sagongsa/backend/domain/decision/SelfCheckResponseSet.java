package com.sagongsa.backend.domain.decision;

import com.sagongsa.backend.domain.common.BaseEntity;
import com.sagongsa.backend.domain.enums.RationalityResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "self_check_response_sets",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_self_check_response_sets_decision_id", columnNames = "decision_id")
	}
)
public class SelfCheckResponseSet extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "decision_id", nullable = false)
	private PurchaseDecision decision;

	@Column(nullable = false)
	private short yesCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RationalityResult rationalityResult;

	@Column(nullable = false)
	private Instant submittedAt;

	protected SelfCheckResponseSet() {
	}
}
