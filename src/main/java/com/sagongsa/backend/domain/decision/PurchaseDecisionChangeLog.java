package com.sagongsa.backend.domain.decision;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.enums.PurchaseDecisionResult;
import com.sagongsa.backend.domain.enums.RationalityResult;
import com.sagongsa.backend.domain.item.SavedItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
	name = "purchase_decision_change_logs",
	indexes = {
		@Index(name = "idx_purchase_decision_change_logs_decision_changed", columnList = "decision_id,changed_at"),
		@Index(name = "idx_purchase_decision_change_logs_user_changed", columnList = "user_id,changed_at")
	}
)
public class PurchaseDecisionChangeLog {

	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "decision_id", nullable = false)
	private PurchaseDecision decision;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private SavedItem item;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PurchaseDecisionResult previousResult;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PurchaseDecisionResult newResult;

	@Column
	private Integer previousFinalPrice;

	@Column
	private Integer newFinalPrice;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private RationalityResult previousRationalityResult;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private RationalityResult newRationalityResult;

	@Column
	private Short previousSelfCheckYesCount;

	@Column
	private Short newSelfCheckYesCount;

	@Column(columnDefinition = "text")
	private String reasonText;

	@Column(nullable = false)
	private Instant changedAt;

	protected PurchaseDecisionChangeLog() {
	}
}
