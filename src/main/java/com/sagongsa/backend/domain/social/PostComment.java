package com.sagongsa.backend.domain.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.common.BaseEntity;
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
	name = "post_comments",
	indexes = {
		@Index(name = "idx_post_comments_post_created_visible", columnList = "post_id,created_at")
	}
)
public class PostComment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private FeedPost post;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(nullable = false, length = 300)
	private String body;

	@Column
	private Instant deletedAt;

	protected PostComment() {
	}

	public PostComment(FeedPost post, UserAccount user, String body) {
		this.post = post;
		this.user = user;
		this.body = body;
	}

	public FeedPost getPost() { return post; }
	public UserAccount getUser() { return user; }
	public String getBody() { return body; }
	public Instant getDeletedAt() { return deletedAt; }

	public boolean isDeleted() { return deletedAt != null; }

	public void softDelete() { this.deletedAt = Instant.now(); }
}
