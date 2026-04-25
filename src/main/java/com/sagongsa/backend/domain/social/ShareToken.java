package com.sagongsa.backend.domain.social;

import com.sagongsa.backend.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "share_tokens",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_share_tokens_feed_post", columnNames = "feed_post_id"),
		@UniqueConstraint(name = "uk_share_tokens_token", columnNames = "token")
	}
)
public class ShareToken extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "feed_post_id", nullable = false)
	private FeedPost feedPost;

	@Column(nullable = false, length = 120)
	private String token;

	protected ShareToken() {
	}
}
