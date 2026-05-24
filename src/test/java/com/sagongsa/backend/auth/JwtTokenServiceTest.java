package com.sagongsa.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
class JwtTokenServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private JwtTokenService jwtTokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void refreshConsumesOldRefreshTokenAndStoresNewOne() {
		UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
		insertUser(userId);
		JwtTokenService.TokenPair issued = jwtTokenService.issueTokenPair(
			profile(userId),
			List.of(new SimpleGrantedAuthority("ROLE_USER"))
		);

		JwtTokenService.TokenPair refreshed = jwtTokenService.refresh(issued.refreshToken());

		assertThat(refreshed.refreshToken()).isNotEqualTo(issued.refreshToken());
		assertThat(countRefreshTokens(userId)).isEqualTo(2);
		assertThat(countRevokedRefreshTokens(userId)).isEqualTo(1);
		assertThatThrownBy(() -> jwtTokenService.refresh(issued.refreshToken()))
			.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void refreshRejectsValidJwtThatIsNotStoredAsActiveRefreshToken() {
		UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000002");
		insertUser(userId);
		JwtTokenService.TokenPair issued = jwtTokenService.issueTokenPair(
			profile(userId),
			List.of(new SimpleGrantedAuthority("ROLE_USER"))
		);
		jdbcTemplate.update("delete from refresh_tokens where user_id = ?", userId);

		assertThatThrownBy(() -> jwtTokenService.refresh(issued.refreshToken()))
			.isInstanceOf(BadCredentialsException.class);
	}

	private void insertUser(UUID userId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"""
			insert into users (id, status, onboarding_status, created_at, updated_at)
			values (?, 'ACTIVE', 'COMPLETED', ?, ?)
			""",
			userId,
			now,
			now
		);
	}

	private SocialUserProfile profile(UUID userId) {
		return new SocialUserProfile(
			"google",
			"google-" + userId,
			"Google Tester",
			"google@test.dev",
			null,
			Map.of(),
			userId
		);
	}

	private int countRefreshTokens(UUID userId) {
		Integer count = jdbcTemplate.queryForObject(
			"select count(*) from refresh_tokens where user_id = ?",
			Integer.class,
			userId
		);
		return count == null ? 0 : count;
	}

	private int countRevokedRefreshTokens(UUID userId) {
		Integer count = jdbcTemplate.queryForObject(
			"select count(*) from refresh_tokens where user_id = ? and revoked_at is not null and last_used_at is not null",
			Integer.class,
			userId
		);
		return count == null ? 0 : count;
	}
}
