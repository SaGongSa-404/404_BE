package com.sagongsa.backend.auth;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAccessService {

	private final UserAccountRepository userAccountRepository;

	public UserAccessService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional
	public void assertApiAccessible(UUID userId) {
		userAccountRepository.findById(userId)
			.ifPresent(user -> {
				if (!isAccessible(user)) {
					throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다.");
				}
			});
	}

	@Transactional
	public void assertTokenIssueAllowed(UUID userId) {
		if (userId == null) {
			throw new BadCredentialsException("User account is required");
		}
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new BadCredentialsException("User account is not available"));
		if (!isAccessible(user)) {
			throw new BadCredentialsException("User account is restricted");
		}
	}

	@Transactional
	public boolean isAccessible(UserAccount user) {
		Instant now = Instant.now();
		user.activateIfSuspensionExpiredAt(now);
		return user.canAccessAt(now);
	}
}
