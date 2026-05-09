package com.sagongsa.backend.auth;

import com.sagongsa.backend.config.AppAuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtTokenService {

	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;
	private final AppAuthProperties properties;
	private final JdbcTemplate jdbcTemplate;

	public JwtTokenService(
		JwtEncoder jwtEncoder,
		JwtDecoder jwtDecoder,
		AppAuthProperties properties,
		JdbcTemplate jdbcTemplate
	) {
		this.jwtEncoder = jwtEncoder;
		this.jwtDecoder = jwtDecoder;
		this.properties = properties;
		this.jdbcTemplate = jdbcTemplate;
	}

	public TokenPair issueTokenPair(SocialUserProfile profile, Collection<? extends GrantedAuthority> authorities) {
		Instant issuedAt = Instant.now();
		List<String> authorityNames = normalizeAuthorities(authorities);

		String accessToken = encodeToken(buildClaims(profile, authorityNames, issuedAt, issuedAt.plus(properties.getAccessTokenTtl()), "access"));
		String refreshToken = encodeToken(buildClaims(profile, authorityNames, issuedAt, issuedAt.plus(properties.getRefreshTokenTtl()), "refresh"));
		Instant refreshTokenExpiresAt = issuedAt.plus(properties.getRefreshTokenTtl());

		storeRefreshToken(profile.userId(), refreshToken, refreshTokenExpiresAt, issuedAt);

		return new TokenPair(
			"Bearer",
			accessToken,
			issuedAt.plus(properties.getAccessTokenTtl()),
			refreshToken,
			refreshTokenExpiresAt,
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
		assertRefreshTokenActive(refreshToken);

		SocialUserProfile profile = SocialUserProfile.fromTokenClaims(jwt.getClaims());
		List<String> authorityNames = jwt.getClaimAsStringList("authorities");
		List<GrantedAuthority> authorities = authorityNames == null
			? List.of(new SimpleGrantedAuthority("ROLE_USER"))
			: authorityNames.stream()
				.map(SimpleGrantedAuthority::new)
				.map(GrantedAuthority.class::cast)
				.toList();

		revokeRefreshToken(refreshToken);
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
		JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
			.issuer(properties.getIssuer())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.subject(profile.userId() != null ? profile.userId().toString() : profile.provider() + ":" + profile.providerUserId())
			.claim("provider", profile.provider())
			.claim("providerUserId", profile.providerUserId())
			.claim("name", profile.name())
			.claim("email", profile.email())
			.claim("profileImageUrl", profile.profileImageUrl())
			.claim("authorities", authorities)
			.claim("token_type", tokenType);

		if (profile.userId() != null) {
			builder.claim("userId", profile.userId().toString());
		}

		return builder.build();
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

	private void storeRefreshToken(UUID userId, String refreshToken, Instant expiresAt, Instant issuedAt) {
		if (userId == null) {
			throw new BadCredentialsException("Refresh token requires persisted user");
		}
		jdbcTemplate.update(
			"""
			insert into refresh_tokens (id, user_id, token_hash, expires_at, created_at, updated_at)
			values (?, ?, ?, ?, ?, ?)
			""",
			UUID.randomUUID(),
			userId,
			hashToken(refreshToken),
			expiresAt,
			issuedAt,
			issuedAt
		);
	}

	private void assertRefreshTokenActive(String refreshToken) {
		Boolean active = jdbcTemplate.queryForObject(
			"""
			select exists(
				select 1
				from refresh_tokens
				where token_hash = ?
				  and revoked_at is null
				  and expires_at > ?
			)
			""",
			Boolean.class,
			hashToken(refreshToken),
			Instant.now()
		);
		if (!Boolean.TRUE.equals(active)) {
			throw new BadCredentialsException("Invalid refresh token");
		}
	}

	private void revokeRefreshToken(String refreshToken) {
		Instant now = Instant.now();
		jdbcTemplate.update(
			"""
			update refresh_tokens
			set revoked_at = ?,
				last_used_at = ?,
				updated_at = ?
			where token_hash = ?
			  and revoked_at is null
			""",
			now,
			now,
			now,
			hashToken(refreshToken)
		);
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 digest is not available", exception);
		}
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
