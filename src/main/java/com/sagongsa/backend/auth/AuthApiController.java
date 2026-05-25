package com.sagongsa.backend.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "OAuth login session and JWT refresh APIs")
public class AuthApiController {

	private final JwtTokenService jwtTokenService;

	public AuthApiController(JwtTokenService jwtTokenService) {
		this.jwtTokenService = jwtTokenService;
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
			return new TokenRefreshResponse(
				tokenPair.tokenType(),
				tokenPair.accessToken(),
				tokenPair.accessTokenExpiresAt(),
				tokenPair.refreshToken(),
				tokenPair.refreshTokenExpiresAt()
			);
		} catch (RuntimeException exception) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token", exception);
		}
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
