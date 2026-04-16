package com.example._04_backend.domain.user.entity;

import com.example._04_backend.domain.user.enums.ImpulseFrequency;
import com.example._04_backend.domain.user.enums.RaccoonStatus;
import com.example._04_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider", "provider_user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(length = 50)
    private String nickname;

    @Column(length = 50)
    private String raccoonName;

    @Column(length = 200)
    private String email;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RaccoonStatus raccoonStatus = RaccoonStatus.NORMAL;

    @Column
    private Integer monthlyBudget;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ImpulseFrequency impulseFrequency;

    @Column(nullable = false)
    private boolean notificationEnabled = true;

    @Builder
    public User(String provider, String providerUserId, String nickname, String email, String profileImageUrl) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.nickname = nickname;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.raccoonStatus = RaccoonStatus.NORMAL;
        this.notificationEnabled = true;
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateProfileWithRaccoonName(String nickname, String raccoonName) {
        if (nickname != null) this.nickname = nickname;
        if (raccoonName != null) this.raccoonName = raccoonName;
    }

    public void updateMonthlyBudget(Integer monthlyBudget) {
        this.monthlyBudget = monthlyBudget;
        recalculateRaccoonStatus(0, monthlyBudget);
    }

    public void updateNotificationEnabled(boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }

    public void updateImpulseFrequency(ImpulseFrequency impulseFrequency) {
        this.impulseFrequency = impulseFrequency;
    }

    public void recalculateRaccoonStatus(int spentAmount, Integer budget) {
        if (budget == null || budget == 0) {
            this.raccoonStatus = RaccoonStatus.NORMAL;
            return;
        }
        double usageRate = (double) spentAmount / budget * 100;
        if (usageRate < 60) {
            this.raccoonStatus = RaccoonStatus.NORMAL;
        } else if (usageRate < 80) {
            this.raccoonStatus = RaccoonStatus.CAUTION;
        } else {
            this.raccoonStatus = RaccoonStatus.DANGER;
        }
    }
}
