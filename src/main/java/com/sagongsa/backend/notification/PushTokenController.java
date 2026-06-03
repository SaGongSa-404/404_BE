package com.sagongsa.backend.notification;

import com.sagongsa.backend.auth.CurrentUserId;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push-tokens")
public class PushTokenController {

	private final PushTokenService pushTokenService;

	public PushTokenController(PushTokenService pushTokenService) {
		this.pushTokenService = pushTokenService;
	}

	@PostMapping
	public ResponseEntity<PushTokenResponse> register(
		@CurrentUserId UUID userId,
		@Valid @RequestBody PushTokenRegisterRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(pushTokenService.register(userId, request));
	}

	@DeleteMapping
	public ResponseEntity<Void> deactivate(
		@CurrentUserId UUID userId,
		@Valid @RequestBody PushTokenDeleteRequest request
	) {
		pushTokenService.deactivate(userId, request);
		return ResponseEntity.noContent().build();
	}
}
