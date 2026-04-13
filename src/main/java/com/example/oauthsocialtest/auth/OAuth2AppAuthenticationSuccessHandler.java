package com.example.oauthsocialtest.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2AppAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final JwtTokenService jwtTokenService;
	private final AppRedirectUriSupport appRedirectUriSupport;

	public OAuth2AppAuthenticationSuccessHandler(
		JwtTokenService jwtTokenService,
		AppRedirectUriSupport appRedirectUriSupport
	) {
		this.jwtTokenService = jwtTokenService;
		this.appRedirectUriSupport = appRedirectUriSupport;
		setDefaultTargetUrl("/");
	}

	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException, ServletException {
		var redirectUri = appRedirectUriSupport.consumeRedirectUri(request);
		if (redirectUri.isEmpty()) {
			super.onAuthenticationSuccess(request, response, authentication);
			return;
		}

		if (!(authentication instanceof OAuth2AuthenticationToken oauthToken) || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
			response.sendRedirect(appRedirectUriSupport.buildFailureRedirectUri(redirectUri.get(), "authentication_failed").toString());
			return;
		}

		SocialUserProfile profile = oauth2User instanceof SocialOAuth2User socialOAuth2User
			? socialOAuth2User.profile()
			: SocialUserProfile.from(oauthToken.getAuthorizedClientRegistrationId(), oauth2User.getAttributes());

		JwtTokenService.TokenPair tokenPair = jwtTokenService.issueTokenPair(profile, authentication.getAuthorities());
		clearAuthenticationAttributes(request);
		response.sendRedirect(appRedirectUriSupport.buildSuccessRedirectUri(redirectUri.get(), tokenPair).toString());
	}
}
