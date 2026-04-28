package com.example._04_backend.domain.user.entity;

import com.example._04_backend.domain.user.enums.OnboardingStatus;
import com.example._04_backend.domain.user.enums.UserStatus;
import com.example._04_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnboardingStatus onboardingStatus = OnboardingStatus.NOT_STARTED;

    @Column
    private LocalDateTime withdrawnAt;

    // OAuth 식별용 (CustomOAuth2UserService에서 사용)
    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Builder
    public User(String provider, String providerUserId) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.status = UserStatus.ACTIVE;
        this.onboardingStatus = OnboardingStatus.NOT_STARTED;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
    }

    public void updateOnboardingStatus(OnboardingStatus onboardingStatus) {
        this.onboardingStatus = onboardingStatus;
    }
}
