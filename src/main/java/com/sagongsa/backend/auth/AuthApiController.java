package com.sagongsa.backend.auth;

import com.sagongsa.backend.config.AppAuthProperties;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "OAuth login session and JWT refresh APIs")
public class AuthApiController {

	private static final Logger log = LoggerFactory.getLogger(AuthApiController.class);
	private static final String REVIEWER_TOKEN_HEADER = "X-Reviewer-Token";
	private static final UUID APP_REVIEWER_USER_ID = UUID.fromString("40400000-0000-0000-0000-000000000055");
	private static final String APP_REVIEWER_PROVIDER = "kakao";
	private static final String APP_REVIEWER_PROVIDER_USER_ID = "app-reviewer-fixed";
	private static final String APP_REVIEWER_NAME = "앱 심사 계정";
	private static final String APP_REVIEWER_EMAIL = "app-reviewer@sagongsa.dev";

	private final JwtTokenService jwtTokenService;
	private final UserAccountRepository userAccountRepository;
	private final UserAccessService userAccessService;
	private final AppAuthProperties appAuthProperties;
	private final ReviewerTokenRateLimiter reviewerTokenRateLimiter;

	public AuthApiController(JwtTokenService jwtTokenService,
		UserAccountRepository userAccountRepository,
		UserAccessService userAccessService,
		AppAuthProperties appAuthProperties,
		ReviewerTokenRateLimiter reviewerTokenRateLimiter) {
		this.jwtTokenService = jwtTokenService;
		this.userAccountRepository = userAccountRepository;
		this.userAccessService = userAccessService;
		this.appAuthProperties = appAuthProperties;
		this.reviewerTokenRateLimiter = reviewerTokenRateLimiter;
	}

	@GetMapping("/me")
	@Operation(
		summary = "Get authenticated user",
		description = "Returns the current OAuth or JWT user profile used by the app after login.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Authenticated user profile"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
		}
	)
	public AuthenticatedUserResponse me(Authentication authentication) {
		SocialUserProfile profile = extractProfile(authentication);
		userAccessService.assertApiAccessible(profile.userId());
		List<String> authorities = authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.sorted()
			.toList();

		return new AuthenticatedUserResponse(
			true,
			profile.userId(),
			profile.provider(),
			profile.providerUserId(),
			profile.name(),
			profile.email(),
			profile.profileImageUrl(),
			authentication.getName(),
			authorities
		);
	}

	@PostMapping("/token/refresh")
	@Operation(
		summary = "Refresh access token",
		description = "Issues a new access token and refresh token pair from a valid refresh token.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Token pair refreshed"),
			@ApiResponse(responseCode = "401", description = "Refresh token is invalid")
		}
	)
	public TokenRefreshResponse refresh(@RequestBody TokenRefreshRequest request) {
		try {
			JwtTokenService.TokenPair tokenPair = jwtTokenService.refresh(request.refreshToken());
			return toTokenResponse(tokenPair);
		} catch (RuntimeException exception) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token", exception);
		}
	}

	@PostMapping("/reviewer-token")
	@Operation(
		summary = "Issue app reviewer token",
		description = "Issues a regular user JWT token pair for the fixed app review account after validating the reviewer secret.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Reviewer token pair issued"),
			@ApiResponse(responseCode = "403", description = "Reviewer token secret is missing or invalid"),
			@ApiResponse(responseCode = "429", description = "Too many reviewer token requests"),
			@ApiResponse(responseCode = "500", description = "Reviewer account seed data is missing")
		}
	)
	public TokenRefreshResponse issueReviewerToken(
		@RequestHeader(name = REVIEWER_TOKEN_HEADER, required = false) String reviewerToken,
		HttpServletRequest request
	) {
		assertReviewerTokenAllowed(reviewerToken, request);
		if (!userAccountRepository.existsById(APP_REVIEWER_USER_ID)) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "App reviewer account is not configured.");
		}

		JwtTokenService.TokenPair tokenPair = jwtTokenService.issueTokenPair(
			new SocialUserProfile(
				APP_REVIEWER_PROVIDER,
				APP_REVIEWER_PROVIDER_USER_ID,
				APP_REVIEWER_NAME,
				APP_REVIEWER_EMAIL,
				null,
				Map.of("purpose", "app-review"),
				APP_REVIEWER_USER_ID
			),
			List.of(new SimpleGrantedAuthority("ROLE_USER"))
		);
		log.info("App reviewer token issued from remoteAddress={}", request.getRemoteAddr());
		return toTokenResponse(tokenPair);
	}

	private void assertReviewerTokenAllowed(String reviewerToken, HttpServletRequest request) {
		AppAuthProperties.ReviewerToken properties = appAuthProperties.getReviewerToken();
		if (!properties.isEnabled()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}

		reviewerTokenRateLimiter.assertAllowed(request.getRemoteAddr());
		if (!StringUtils.hasText(properties.getSecret()) || !constantTimeEquals(properties.getSecret(), reviewerToken)) {
			log.warn("App reviewer token rejected from remoteAddress={}", request.getRemoteAddr());
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid reviewer token secret");
		}
	}

	private boolean constantTimeEquals(String expected, String actual) {
		if (actual == null) {
			return false;
		}
		byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
		byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expectedBytes, actualBytes);
	}

	private SocialUserProfile extractProfile(Authentication authentication) {
		if (authentication instanceof OAuth2AuthenticationToken oauthToken && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
			return oauth2User instanceof SocialOAuth2User socialOAuth2User
				? socialOAuth2User.profile()
				: SocialUserProfile.from(oauthToken.getAuthorizedClientRegistrationId(), oauth2User.getAttributes());
		}

		if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
			return SocialUserProfile.fromTokenClaims(jwtAuthenticationToken.getToken().getClaims());
		}

		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
	}

	private TokenRefreshResponse toTokenResponse(JwtTokenService.TokenPair tokenPair) {
		return new TokenRefreshResponse(
			tokenPair.tokenType(),
			tokenPair.accessToken(),
			tokenPair.accessTokenExpiresAt(),
			tokenPair.refreshToken(),
			tokenPair.refreshTokenExpiresAt()
		);
	}

	public record AuthenticatedUserResponse(
		boolean authenticated,
		java.util.UUID userId,
		String provider,
		String providerUserId,
		String name,
		String email,
		String profileImageUrl,
		String principalName,
		List<String> authorities
	) {
	}

	public record TokenRefreshRequest(String refreshToken) {
	}

	public record TokenRefreshResponse(
		String tokenType,
		String accessToken,
		Instant accessTokenExpiresAt,
		String refreshToken,
		Instant refreshTokenExpiresAt
	) {
	}
}
