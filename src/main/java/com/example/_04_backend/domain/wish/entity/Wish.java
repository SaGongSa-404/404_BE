package com.example._04_backend.domain.wish.entity;

import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.wish.enums.WishStatus;
import com.example._04_backend.global.common.BaseEntity;
import com.example._04_backend.global.common.enums.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wishes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wish extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private Integer price;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String productUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WishStatus status = WishStatus.PENDING;

    private LocalDateTime decisionAt;

    private Boolean regret;

    private LocalDateTime regretRecordedAt;

    @Builder
    public Wish(User user, String title, Integer price, String imageUrl,
                String productUrl, Category category) {
        this.user = user;
        this.title = title;
        this.price = price;
        this.imageUrl = imageUrl;
        this.productUrl = productUrl;
        this.category = category;
        this.status = WishStatus.PENDING;
    }

    public void decide(WishStatus decision) {
        this.status = decision;
        this.decisionAt = LocalDateTime.now();
    }

    public void recordRegret(boolean regret) {
        this.regret = regret;
        this.regretRecordedAt = LocalDateTime.now();
    }
}
