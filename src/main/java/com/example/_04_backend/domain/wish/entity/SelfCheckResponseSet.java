package com.example._04_backend.domain.wish.entity;

import com.example._04_backend.domain.wish.enums.RationalityResult;
import com.example._04_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "self_check_response_sets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_self_check_response_sets_item_id",
                columnNames = "item_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SelfCheckResponseSet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private SavedItem item;

    @Column(nullable = false)
    private short yesCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RationalityResult rationalityResult;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Builder
    public SelfCheckResponseSet(SavedItem item, short yesCount, RationalityResult rationalityResult) {
        this.item = item;
        this.yesCount = yesCount;
        this.rationalityResult = rationalityResult;
        this.submittedAt = LocalDateTime.now();
    }
}
