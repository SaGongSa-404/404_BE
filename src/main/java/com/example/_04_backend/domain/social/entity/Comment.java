package com.example._04_backend.domain.social.entity;

import com.example._04_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private SocialPost post;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 300)
    private String body;

    @Builder
    public Comment(SocialPost post, UUID userId, String body) {
        this.post = post;
        this.userId = userId;
        this.body = body;
    }
}
