package com.sagongsa.backend.auth;

import java.util.Collection;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class SocialOAuth2User implements OAuth2User {

	private final SocialUserProfile profile;
	private final Collection<? extends GrantedAuthority> authorities;

	public SocialOAuth2User(SocialUserProfile profile, Collection<? extends GrantedAuthority> authorities) {
		this.profile = profile;
		this.authorities = authorities;
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
		return profile.userId() != null ? profile.userId().toString() : profile.provider() + ":" + profile.providerUserId();
	}
}
