package com.sagongsa.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.domain.auth.SocialAccountRepository;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SocialAccountLinkServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private SocialAccountLinkService socialAccountLinkService;

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void createsUserAndSocialAccountOnFirstLogin() {
		SocialUserProfile linkedProfile = socialAccountLinkService.linkOrCreateUser(
			new SocialUserProfile(
				"google",
				"google-123",
				"Google Tester",
				"google@test.dev",
				"https://example.com/google.png",
				java.util.Map.of(),
				null
			)
		);

		assertThat(linkedProfile.userId()).isNotNull();
		assertThat(userAccountRepository.count()).isEqualTo(1);
		assertThat(socialAccountRepository.count()).isEqualTo(1);
	}

	@Test
	void reusesExistingUserForSameProviderIdentity() {
		SocialUserProfile firstLogin = socialAccountLinkService.linkOrCreateUser(
			new SocialUserProfile(
				"kakao",
				"987654321",
				"Kakao Tester",
				null,
				"https://example.com/kakao-before.png",
				java.util.Map.of(),
				null
			)
		);

		SocialUserProfile secondLogin = socialAccountLinkService.linkOrCreateUser(
			new SocialUserProfile(
				"kakao",
				"987654321",
				"Kakao Tester",
				"kakao@test.dev",
				"https://example.com/kakao-after.png",
				java.util.Map.of(),
				null
			)
		);

		assertThat(secondLogin.userId()).isEqualTo(firstLogin.userId());
		assertThat(userAccountRepository.count()).isEqualTo(1);
		assertThat(socialAccountRepository.count()).isEqualTo(1);
		assertThat(socialAccountRepository.findAll().getFirst().getEmail()).isEqualTo("kakao@test.dev");
		assertThat(socialAccountRepository.findAll().getFirst().getProfileImageUrl()).isEqualTo("https://example.com/kakao-after.png");
	}

	@Test
	void createsNewUserWhenExistingSocialAccountBelongsToWithdrawnUser() {
		SocialUserProfile firstLogin = socialAccountLinkService.linkOrCreateUser(
			new SocialUserProfile(
				"google",
				"withdrawn-google-123",
				"Google Tester",
				"before@test.dev",
				"https://example.com/before.png",
				java.util.Map.of(),
				null
			)
		);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"update users set status = 'WITHDRAWN', withdrawn_at = ?, updated_at = ? where id = ?",
			now, now, firstLogin.userId()
		);

		SocialUserProfile secondLogin = socialAccountLinkService.linkOrCreateUser(
			new SocialUserProfile(
				"google",
				"withdrawn-google-123",
				"Google Tester",
				"after@test.dev",
				"https://example.com/after.png",
				java.util.Map.of(),
				null
			)
		);

		assertThat(secondLogin.userId()).isNotEqualTo(firstLogin.userId());
		assertThat(userAccountRepository.count()).isEqualTo(2);
		assertThat(socialAccountRepository.count()).isEqualTo(1);
		assertThat(socialAccountRepository.findAll().getFirst().getUser().getId()).isEqualTo(secondLogin.userId());
		assertThat(socialAccountRepository.findAll().getFirst().getEmail()).isEqualTo("after@test.dev");
		assertThat(queryString("select status from users where id = ?", secondLogin.userId())).isEqualTo("ACTIVE");
		assertThat(queryString("select onboarding_status from users where id = ?", secondLogin.userId())).isEqualTo("NOT_STARTED");
	}

	private String queryString(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}
}
