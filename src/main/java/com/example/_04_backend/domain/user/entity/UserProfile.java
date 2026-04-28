package com.example._04_backend.domain.user.entity;

import com.example._04_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends BaseEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private java.util.UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(nullable = false, length = 40)
    private String mascotName;

    @Column(nullable = false, length = 40)
    private String timezone;

    @Column(columnDefinition = "text")
    private String profileImageUrl;

    @Builder
    public UserProfile(User user, String nickname, String mascotName, String profileImageUrl) {
        this.user = user;
        this.nickname = nickname;
        this.mascotName = mascotName != null ? mascotName : "너구리";
        this.timezone = "Asia/Seoul";
        this.profileImageUrl = profileImageUrl;
    }

    public void updateProfile(String nickname, String mascotName) {
        if (nickname != null) this.nickname = nickname;
        if (mascotName != null) this.mascotName = mascotName;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
