package com.example._04_backend.domain.wish.entity;

import com.example._04_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wish_deliberations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishDeliberation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wish_id", nullable = false, unique = true)
    private Wish wish;

    @Column(nullable = false)
    private Boolean answer1;

    @Column(nullable = false)
    private Boolean answer2;

    @Column(nullable = false)
    private Boolean answer3;

    @Column(nullable = false)
    private Boolean answer4;

    @Column(nullable = false)
    private Integer yesCount;

    @Column(nullable = false)
    private Boolean warningTriggered;

    @Builder
    public WishDeliberation(Wish wish,
                             Boolean answer1, Boolean answer2,
                             Boolean answer3, Boolean answer4,
                             Integer yesCount, Boolean warningTriggered) {
        this.wish = wish;
        this.answer1 = answer1;
        this.answer2 = answer2;
        this.answer3 = answer3;
        this.answer4 = answer4;
        this.yesCount = yesCount;
        this.warningTriggered = warningTriggered;
    }
}