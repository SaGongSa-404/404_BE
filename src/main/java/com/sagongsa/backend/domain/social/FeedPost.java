package com.sagongsa.backend.domain.social;

import com.sagongsa.backend.domain.common.UserScopedEntity;
import com.sagongsa.backend.domain.decision.PurchaseDecision;
import com.sagongsa.backend.domain.item.SavedItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
	name = "feed_posts",
	indexes = {
		@Index(name = "idx_feed_posts_visible_created", columnList = "created_at"),
		@Index(name = "idx_feed_posts_user_created", columnList = "user_id,created_at")
	}
)
public class FeedPost extends UserScopedEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "item_id")
	private SavedItem item;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decision_id")
	private PurchaseDecision decision;

	@Column(nullable = false, length = 140)
	private String title;

	@Column(columnDefinition = "text")
	private String body;

	@Column(nullable = false)
	private int goCount;

	@Column(nullable = false)
	private int stopCount;

	@Column
	private Instant deletedAt;

	protected FeedPost() {
	}
}
