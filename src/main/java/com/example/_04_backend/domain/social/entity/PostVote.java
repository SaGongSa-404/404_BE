package com.example._04_backend.domain.social.entity;

import com.example._04_backend.domain.social.enums.PostVoteType;
import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "post_votes",
        indexes = {
                @Index(name = "idx_post_votes_post_type_active", columnList = "post_id,vote_type")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_post_votes_post_user", columnNames = {"post_id", "user_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostVote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private FeedPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PostVoteType voteType;

    @Column
    private LocalDateTime canceledAt;

    @Builder
    public PostVote(FeedPost post, User user, PostVoteType voteType) {
        this.post = post;
        this.user = user;
        this.voteType = voteType;
    }

    public void changeVoteType(PostVoteType voteType) {
        this.voteType = voteType;
        this.canceledAt = null;
    }

    public void cancel() {
        this.canceledAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.canceledAt == null;
    }
}
