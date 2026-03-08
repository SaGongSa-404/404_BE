package com.example.oauthsocialtest.controller;

import com.example.oauthsocialtest.auth.SocialOAuth2User;
import com.example.oauthsocialtest.auth.SocialUserProfile;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

	@GetMapping("/me")
	public AuthenticatedUserResponse me(Authentication authentication) {
		if (!(authentication instanceof OAuth2AuthenticationToken oauthToken) || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
		}

		SocialUserProfile profile = oauth2User instanceof SocialOAuth2User socialOAuth2User
			? socialOAuth2User.profile()
			: SocialUserProfile.from(oauthToken.getAuthorizedClientRegistrationId(), oauth2User.getAttributes());

		List<String> authorities = authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.sorted()
			.toList();

		return new AuthenticatedUserResponse(
			true,
			profile.provider(),
			profile.providerUserId(),
			profile.name(),
			profile.email(),
			profile.profileImageUrl(),
			oauth2User.getName(),
			authorities,
			profile.rawAttributes()
		);
	}

	public record AuthenticatedUserResponse(
		boolean authenticated,
		String provider,
		String providerUserId,
		String name,
		String email,
		String profileImageUrl,
		String principalName,
		List<String> authorities,
		Object rawAttributes
	) {
	}
}
