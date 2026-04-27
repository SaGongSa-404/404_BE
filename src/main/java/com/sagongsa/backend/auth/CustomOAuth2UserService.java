package com.sagongsa.backend.auth;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
	private final SocialAccountLinkService socialAccountLinkService;

	public CustomOAuth2UserService(SocialAccountLinkService socialAccountLinkService) {
		this.socialAccountLinkService = socialAccountLinkService;
	}

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oauth2User = delegate.loadUser(userRequest);
		String registrationId = userRequest.getClientRegistration().getRegistrationId();
		SocialUserProfile profile = socialAccountLinkService.linkOrCreateUser(
			SocialUserProfile.from(registrationId, oauth2User.getAttributes())
		);

		Set<GrantedAuthority> authorities = new LinkedHashSet<>(oauth2User.getAuthorities());
		authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

		return new SocialOAuth2User(profile, authorities);
	}
}
