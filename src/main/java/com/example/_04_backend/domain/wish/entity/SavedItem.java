package com.example._04_backend.domain.wish.entity;

import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.wish.enums.ItemCategory;
import com.example._04_backend.domain.wish.enums.ItemInputSource;
import com.example._04_backend.domain.wish.enums.ItemStatus;
import com.example._04_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "saved_items",
        indexes = {
                @Index(name = "idx_saved_items_user_status_created", columnList = "user_id,status,created_at"),
                @Index(name = "idx_saved_items_user_category_created", columnList = "user_id,category,created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemInputSource inputSource;

    @Column(columnDefinition = "text")
    private String originalUrl;

    @Column(columnDefinition = "text")
    private String normalizedUrl;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String imageUrl;

    @Column
    private Integer listedPrice;

    @Column(length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ItemCategory category;

    @Column(nullable = false)
    private boolean categoryLockedByUser = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status = ItemStatus.SAVED;

    @Builder
    public SavedItem(User user, String title, Integer listedPrice, String imageUrl,
                     String originalUrl, ItemCategory category, ItemInputSource inputSource) {
        this.user = user;
        this.title = title;
        this.listedPrice = listedPrice;
        this.imageUrl = imageUrl;
        this.originalUrl = originalUrl;
        this.normalizedUrl = originalUrl;
        this.category = category;
        this.inputSource = inputSource != null ? inputSource : ItemInputSource.DIRECT_INPUT;
        this.status = ItemStatus.SAVED;
        this.categoryLockedByUser = false;
    }

    public void decide(ItemStatus decision) {
        this.status = decision;
    }
}
