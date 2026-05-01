package com.sagongsa.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.domain.auth.SocialAccountRepository;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
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
}
