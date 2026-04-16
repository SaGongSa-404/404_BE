package com.example._04_backend.global.auth;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * OAuth2 로그인 성공 후 SecurityContext에 저장되는 인증 사용자 객체.
 * 컨트롤러에서 @AuthenticationPrincipal LoginUser 로 주입받아 사용한다.
 */
public class LoginUser implements OAuth2User {

    private final UUID id;
    private final SocialUserProfile profile;
    private final Collection<? extends GrantedAuthority> authorities;

    public LoginUser(UUID id, SocialUserProfile profile, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.profile = profile;
        this.authorities = authorities;
    }

    public UUID getId() {
        return id;
    }

    public SocialUserProfile profile() {
        return profile;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return profile.rawAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return profile.provider() + ":" + profile.providerUserId();
    }
}
