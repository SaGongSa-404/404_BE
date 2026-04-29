package com.example._04_backend.global.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Google 등 OIDC 프로바이더 로그인 후 SecurityContext에 저장되는 인증 사용자 객체.
 * LoginUser를 상속하므로 @AuthenticationPrincipal LoginUser 주입이 정상 작동한다.
 */
public class OidcLoginUser extends LoginUser implements OidcUser {

    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public OidcLoginUser(UUID id, SocialUserProfile profile,
                         Collection<? extends GrantedAuthority> authorities,
                         OidcIdToken idToken, OidcUserInfo userInfo) {
        super(id, profile, authorities);
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    @Override
    public Map<String, Object> getClaims() {
        return idToken.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }
}
