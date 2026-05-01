package com.sagongsa.backend.domain.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.common.BaseEntity;
import com.sagongsa.backend.domain.enums.PostVoteType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
	name = "post_votes",
	indexes = {
		@Index(name = "idx_post_votes_post_type_active", columnList = "post_id,vote_type")
	}
)
public class PostVote extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private FeedPost post;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PostVoteType voteType;

	@Column
	private Instant canceledAt;

	protected PostVote() {
	}
}
