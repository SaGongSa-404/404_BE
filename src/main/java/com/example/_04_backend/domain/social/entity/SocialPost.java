package com.example._04_backend.domain.social.entity;

import com.example._04_backend.global.common.BaseEntity;
import com.example._04_backend.global.common.enums.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "social_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private UUID wishId;

    private String productUrl;

    private String imageUrl;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String body;

    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private int goCount = 0;

    @Column(nullable = false)
    private int stopCount = 0;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Vote> votes = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @Builder
    public SocialPost(UUID userId, UUID wishId, String productUrl, String imageUrl,
                      String title, String body, Integer price, Category category) {
        this.userId = userId;
        this.wishId = wishId;
        this.productUrl = productUrl;
        this.imageUrl = imageUrl;
        this.title = title;
        this.body = body;
        this.price = price;
        this.category = category;
    }

    public void incrementGoCount() {
        this.goCount++;
    }

    public void decrementGoCount() {
        if (this.goCount > 0) this.goCount--;
    }

    public void incrementStopCount() {
        this.stopCount++;
    }

    public void decrementStopCount() {
        if (this.stopCount > 0) this.stopCount--;
    }
}
