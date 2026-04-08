package com.example.oauthsocialtest.config;

import com.example.oauthsocialtest.auth.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
public class SecurityConfig {

	private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		CustomOAuth2UserService customOAuth2UserService,
		OAuth2AuthorizationRequestResolver authorizationRequestResolver
	) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/index.html", "/favicon.ico", "/error").permitAll()
				.requestMatchers("/api/auth/me").authenticated()
				.anyRequest().permitAll()
			)
			.oauth2Login(oauth2 -> oauth2
				.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
				.userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
				.defaultSuccessUrl("/", true)
			)
			.logout(logout -> logout
				.logoutUrl("/api/logout")
				.logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
			)
			.exceptionHandling(exceptions -> exceptions
				.defaultAuthenticationEntryPointFor(
					new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
					RegexRequestMatcher.regexMatcher("^/api/.*")
				)
			)
			.oauth2Client(Customizer.withDefaults());

		return http.build();
	}

	@Bean
	OAuth2AuthorizationRequestResolver authorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
		DefaultOAuth2AuthorizationRequestResolver delegate =
			new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, AUTHORIZATION_BASE_URI);

		return new OAuth2AuthorizationRequestResolver() {
			@Override
			public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
				return customize(request, delegate.resolve(request));
			}

			@Override
			public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
				return customize(clientRegistrationId, delegate.resolve(request, clientRegistrationId));
			}
		};
	}

	private OAuth2AuthorizationRequest customize(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
		String registrationId = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/') + 1);
		return customize(registrationId, authorizationRequest);
	}

	private OAuth2AuthorizationRequest customize(String registrationId, OAuth2AuthorizationRequest authorizationRequest) {
		if (authorizationRequest == null) {
			return authorizationRequest;
		}

		Map<String, Object> additionalParameters = new LinkedHashMap<>(authorizationRequest.getAdditionalParameters());

		if ("google".equals(registrationId)) {
			additionalParameters.put("prompt", "select_account");
		}

		if ("kakao".equals(registrationId)) {
			additionalParameters.put("prompt", "login");
		}

		if ("naver".equals(registrationId)) {
			additionalParameters.put("auth_type", "reprompt");
		}

		if ("apple".equals(registrationId)) {
			additionalParameters.put("response_mode", "form_post");
		}

		return OAuth2AuthorizationRequest.from(authorizationRequest)
			.additionalParameters(additionalParameters)
			.build();
	}
}
