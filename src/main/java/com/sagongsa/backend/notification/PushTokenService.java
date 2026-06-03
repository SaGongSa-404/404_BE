package com.sagongsa.backend.notification;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.UserStatus;
import com.sagongsa.backend.domain.notification.DevicePushToken;
import com.sagongsa.backend.domain.notification.DevicePushTokenRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PushTokenService {

	private final UserAccountRepository userAccountRepository;
	private final DevicePushTokenRepository devicePushTokenRepository;

	public PushTokenService(
		UserAccountRepository userAccountRepository,
		DevicePushTokenRepository devicePushTokenRepository
	) {
		this.userAccountRepository = userAccountRepository;
		this.devicePushTokenRepository = devicePushTokenRepository;
	}

	@Transactional
	public PushTokenResponse register(UUID userId, PushTokenRegisterRequest request) {
		UserAccount user = findActiveUserOrThrow(userId);
		DevicePushToken token = devicePushTokenRepository.findByPushToken(request.token())
			.map(existing -> {
				existing.activateFor(user, request.deviceId(), request.platform());
				return existing;
			})
			.orElseGet(() -> devicePushTokenRepository.save(
				DevicePushToken.create(user, request.deviceId(), request.platform(), request.token())));
		return PushTokenResponse.of(token);
	}

	@Transactional
	public void deactivate(UUID userId, PushTokenDeleteRequest request) {
		findActiveUserOrThrow(userId);
		devicePushTokenRepository.findByPushToken(request.token())
			.filter(token -> token.getUser().getId().equals(userId))
			.ifPresent(DevicePushToken::deactivate);
	}

	private UserAccount findActiveUserOrThrow(UUID userId) {
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found."));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Push tokens can be used only by active users.");
		}
		return user;
	}
}
