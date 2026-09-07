package com.sagongsa.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

class UserAccessServiceTest {
	private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
	private final UserAccountRepository repository = mock(UserAccountRepository.class);
	private final UserAccessService service = new UserAccessService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void missingAccountIsDelegatedToApiButRejectedForTokenIssue() {
		UUID userId = UUID.randomUUID();
		when(repository.findById(userId)).thenReturn(Optional.empty());
		assertThatCode(() -> service.assertApiAccessible(userId)).doesNotThrowAnyException();
		assertThatThrownBy(() -> service.assertTokenIssueAllowed(userId))
			.isInstanceOf(BadCredentialsException.class).hasMessage("User account is not available");
	}

	@Test
	void tokenIssueRequiresUserId() {
		assertThatThrownBy(() -> service.assertTokenIssueAllowed(null))
			.isInstanceOf(BadCredentialsException.class).hasMessage("User account is required");
	}

	@Test
	void expiredSuspensionActivatesAtExactBoundary() {
		var user = UserAccount.create();
		user.suspendUntil(NOW);
		assertThat(service.isAccessible(user)).isTrue();
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getSuspendedUntil()).isNull();
	}

	@Test
	void futureSuspensionIsNotActivatedEarly() {
		var user = UserAccount.create();
		user.suspendUntil(NOW.plusNanos(1));
		assertThat(service.isAccessible(user)).isFalse();
		assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
	}

	@Test
	void restrictedAccountKeepsDifferentApiAndTokenErrors() {
		UUID userId = UUID.randomUUID();
		var user = UserAccount.create();
		user.banPermanently();
		when(repository.findById(userId)).thenReturn(Optional.of(user));
		assertThatThrownBy(() -> service.assertApiAccessible(userId))
			.isInstanceOfSatisfying(ResponseStatusException.class,
				error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
		assertThatThrownBy(() -> service.assertTokenIssueAllowed(userId))
			.isInstanceOf(BadCredentialsException.class).hasMessage("User account is restricted");
	}

	@Test
	void onboardingRequirementsRemainOutsideAccountAccessPolicy() {
		assertThat(service.isAccessible(UserAccount.create())).isTrue();
	}

	@Test
	void withdrawnAccountIsNotReactivated() {
		var user = UserAccount.create();
		user.withdraw();
		assertThat(service.isAccessible(user)).isFalse();
		assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
	}
}
