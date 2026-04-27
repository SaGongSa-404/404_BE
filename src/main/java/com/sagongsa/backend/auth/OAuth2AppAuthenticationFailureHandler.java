package com.sagongsa.backend.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2AppAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

	private final AppRedirectUriSupport appRedirectUriSupport;

	public OAuth2AppAuthenticationFailureHandler(AppRedirectUriSupport appRedirectUriSupport) {
		this.appRedirectUriSupport = appRedirectUriSupport;
		setDefaultFailureUrl("/?error");
	}

	@Override
	public void onAuthenticationFailure(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException exception
	) throws IOException, ServletException {
		var redirectUri = appRedirectUriSupport.consumeRedirectUri(request);
		if (redirectUri.isPresent()) {
			response.sendRedirect(appRedirectUriSupport.buildFailureRedirectUri(redirectUri.get(), "oauth2_login_failed").toString());
			return;
		}

		super.onAuthenticationFailure(request, response, exception);
	}
}
