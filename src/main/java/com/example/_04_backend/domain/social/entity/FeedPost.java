package com.example._04_backend.domain.social.entity;

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
        name = "feed_posts",
        indexes = {
                @Index(name = "idx_feed_posts_visible_created", columnList = "created_at"),
                @Index(name = "idx_feed_posts_user_created", columnList = "user_id,created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private int goCount = 0;

    @Column(nullable = false)
    private int stopCount = 0;

    // 소셜 피드 표시용 이미지 (선택)
    @Column(columnDefinition = "text")
    private String imageUrl;

    // 소셜 피드 표시용 가격 (선택)
    private Integer price;

    @Column
    private LocalDateTime deletedAt;

    @Builder
    public FeedPost(User user, String title, String body, String imageUrl, Integer price) {
        this.user = user;
        this.title = title;
        this.body = body;
        this.imageUrl = imageUrl;
        this.price = price;
    }

    public void incrementGoCount() { this.goCount++; }
    public void decrementGoCount() { if (this.goCount > 0) this.goCount--; }
    public void incrementStopCount() { this.stopCount++; }
    public void decrementStopCount() { if (this.stopCount > 0) this.stopCount--; }

    public void softDelete() { this.deletedAt = LocalDateTime.now(); }
    public boolean isDeleted() { return this.deletedAt != null; }
}
