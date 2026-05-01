package com.sagongsa.backend.domain.mascot;

import com.sagongsa.backend.domain.common.UserScopedEntity;
import com.sagongsa.backend.domain.decision.PurchaseDecision;
import com.sagongsa.backend.domain.enums.MascotState;
import com.sagongsa.backend.domain.enums.MascotStateEventType;
import com.sagongsa.backend.domain.item.SavedItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
	name = "mascot_state_events",
	indexes = {
		@Index(name = "idx_mascot_state_events_user_created", columnList = "user_id,created_at"),
		@Index(name = "idx_mascot_state_events_decision_id", columnList = "decision_id")
	}
)
public class MascotStateEvent extends UserScopedEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "item_id")
	private SavedItem item;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decision_id")
	private PurchaseDecision decision;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private MascotStateEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private MascotState previousState;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MascotState newState;

	@Column(length = 140)
	private String reactionMessage;

	protected MascotStateEvent() {
	}
}
