package com.example.oauthsocialtest.config;

import com.example.oauthsocialtest.auth.AppRedirectUriSupport;
import com.example.oauthsocialtest.auth.CustomOAuth2UserService;
import com.example.oauthsocialtest.auth.OAuth2AppAuthenticationFailureHandler;
import com.example.oauthsocialtest.auth.OAuth2AppAuthenticationSuccessHandler;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
@EnableConfigurationProperties(AppAuthProperties.class)
public class SecurityConfig {

	private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		CustomOAuth2UserService customOAuth2UserService,
		OAuth2AuthorizationRequestResolver authorizationRequestResolver,
		OAuth2AppAuthenticationSuccessHandler authenticationSuccessHandler,
		OAuth2AppAuthenticationFailureHandler authenticationFailureHandler,
		Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter
	) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/index.html", "/favicon.ico", "/error").permitAll()
				.requestMatchers("/api/auth/token/refresh").permitAll()
				.requestMatchers("/api/auth/me").authenticated()
				.anyRequest().permitAll()
			)
			.oauth2Login(oauth2 -> oauth2
				.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
				.userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
				.successHandler(authenticationSuccessHandler)
				.failureHandler(authenticationFailureHandler)
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
			.oauth2Client(Customizer.withDefaults())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

		return http.build();
	}

	@Bean
	OAuth2AuthorizationRequestResolver authorizationRequestResolver(
		ClientRegistrationRepository clientRegistrationRepository,
		AppRedirectUriSupport appRedirectUriSupport
	) {
		DefaultOAuth2AuthorizationRequestResolver delegate =
			new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, AUTHORIZATION_BASE_URI);

		return new OAuth2AuthorizationRequestResolver() {
			@Override
			public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
				appRedirectUriSupport.captureRedirectUri(request);
				return customize(request, delegate.resolve(request));
			}

			@Override
			public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
				appRedirectUriSupport.captureRedirectUri(request);
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

	@Bean
	JwtEncoder jwtEncoder(AppAuthProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey(properties)));
	}

	@Bean
	JwtDecoder jwtDecoder(AppAuthProperties properties) {
		return NimbusJwtDecoder.withSecretKey(jwtSecretKey(properties))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
	}

	@Bean
	Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
		return jwt -> new JwtAuthenticationToken(jwt, extractAuthorities(jwt), jwt.getSubject());
	}

	private List<GrantedAuthority> extractAuthorities(Jwt jwt) {
		List<String> authorities = jwt.getClaimAsStringList("authorities");
		if (authorities == null || authorities.isEmpty()) {
			return List.of(new SimpleGrantedAuthority("ROLE_USER"));
		}
		return authorities.stream()
			.map(SimpleGrantedAuthority::new)
			.map(GrantedAuthority.class::cast)
			.toList();
	}

	private SecretKey jwtSecretKey(AppAuthProperties properties) {
		try {
			byte[] secretBytes = MessageDigest.getInstance("SHA-256")
				.digest(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(secretBytes, "HmacSHA256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 not available", exception);
		}
	}
}
