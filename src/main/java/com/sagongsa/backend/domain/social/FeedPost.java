package com.sagongsa.backend.domain.social;

import com.sagongsa.backend.domain.auth.UserAccount;
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

	@Column(columnDefinition = "text")
	private String imageUrl;

	@Column
	private Integer price;

	@Column(nullable = false)
	private int goCount;

	@Column(nullable = false)
	private int stopCount;

	@Column
	private Instant deletedAt;

	protected FeedPost() {
	}

	public FeedPost(UserAccount user, String title, String body, String imageUrl, Integer price) {
		this(user, null, title, body, imageUrl, price);
	}

	public FeedPost(UserAccount user, SavedItem item, String title, String body, String imageUrl, Integer price) {
		super(user);
		this.item = item;
		this.title = title;
		this.body = body;
		this.imageUrl = imageUrl;
		this.price = price;
		this.goCount = 0;
		this.stopCount = 0;
	}

	public SavedItem getItem() { return item; }
	public String getTitle() { return title; }
	public String getBody() { return body; }
	public String getImageUrl() { return imageUrl; }
	public Integer getPrice() { return price; }
	public int getGoCount() { return goCount; }
	public int getStopCount() { return stopCount; }
	public Instant getDeletedAt() { return deletedAt; }

	public boolean isDeleted() { return deletedAt != null; }

	public void softDelete() { this.deletedAt = Instant.now(); }

	public void incrementGoCount() { this.goCount++; }
	public void decrementGoCount() { if (this.goCount > 0) this.goCount--; }
	public void incrementStopCount() { this.stopCount++; }
	public void decrementStopCount() { if (this.stopCount > 0) this.stopCount--; }
}
