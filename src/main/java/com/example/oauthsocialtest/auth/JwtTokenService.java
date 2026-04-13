package com.example.oauthsocialtest.auth;

import com.example.oauthsocialtest.config.AppAuthProperties;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtTokenService {

	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;
	private final AppAuthProperties properties;

	public JwtTokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, AppAuthProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.jwtDecoder = jwtDecoder;
		this.properties = properties;
	}

	public TokenPair issueTokenPair(SocialUserProfile profile, Collection<? extends GrantedAuthority> authorities) {
		Instant issuedAt = Instant.now();
		List<String> authorityNames = normalizeAuthorities(authorities);

		String accessToken = encodeToken(buildClaims(profile, authorityNames, issuedAt, issuedAt.plus(properties.getAccessTokenTtl()), "access"));
		String refreshToken = encodeToken(buildClaims(profile, authorityNames, issuedAt, issuedAt.plus(properties.getRefreshTokenTtl()), "refresh"));

		return new TokenPair(
			"Bearer",
			accessToken,
			issuedAt.plus(properties.getAccessTokenTtl()),
			refreshToken,
			issuedAt.plus(properties.getRefreshTokenTtl()),
			profile
		);
	}

	public TokenPair refresh(String refreshToken) {
		if (!StringUtils.hasText(refreshToken)) {
			throw new BadCredentialsException("Refresh token is required");
		}

		Jwt jwt = jwtDecoder.decode(refreshToken);
		String tokenType = jwt.getClaimAsString("token_type");
		if (!"refresh".equals(tokenType)) {
			throw new BadCredentialsException("Invalid refresh token");
		}

		SocialUserProfile profile = SocialUserProfile.fromTokenClaims(jwt.getClaims());
		List<String> authorityNames = jwt.getClaimAsStringList("authorities");
		List<GrantedAuthority> authorities = authorityNames == null
			? List.of(new SimpleGrantedAuthority("ROLE_USER"))
			: authorityNames.stream()
				.map(SimpleGrantedAuthority::new)
				.map(GrantedAuthority.class::cast)
				.toList();

		return issueTokenPair(profile, authorities);
	}

	private String encodeToken(JwtClaimsSet claimsSet) {
		JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsSet)).getTokenValue();
	}

	private JwtClaimsSet buildClaims(
		SocialUserProfile profile,
		List<String> authorities,
		Instant issuedAt,
		Instant expiresAt,
		String tokenType
	) {
		return JwtClaimsSet.builder()
			.issuer(properties.getIssuer())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.subject(profile.provider() + ":" + profile.providerUserId())
			.claim("provider", profile.provider())
			.claim("providerUserId", profile.providerUserId())
			.claim("name", profile.name())
			.claim("email", profile.email())
			.claim("profileImageUrl", profile.profileImageUrl())
			.claim("authorities", authorities)
			.claim("token_type", tokenType)
			.build();
	}

	private List<String> normalizeAuthorities(Collection<? extends GrantedAuthority> authorities) {
		Set<String> authorityNames = new LinkedHashSet<>();
		if (authorities != null) {
			authorityNames.addAll(
				authorities.stream()
					.map(GrantedAuthority::getAuthority)
					.filter(StringUtils::hasText)
					.collect(Collectors.toCollection(LinkedHashSet::new))
			);
		}
		authorityNames.add("ROLE_USER");
		return List.copyOf(authorityNames);
	}

	public record TokenPair(
		String tokenType,
		String accessToken,
		Instant accessTokenExpiresAt,
		String refreshToken,
		Instant refreshTokenExpiresAt,
		SocialUserProfile profile
	) {
	}
}
