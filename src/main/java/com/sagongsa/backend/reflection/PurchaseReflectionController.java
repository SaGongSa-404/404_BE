package com.sagongsa.backend.reflection;

import com.sagongsa.backend.auth.CurrentUserId;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reflections")
public class PurchaseReflectionController {

	private final PurchaseReflectionService purchaseReflectionService;

	public PurchaseReflectionController(PurchaseReflectionService purchaseReflectionService) {
		this.purchaseReflectionService = purchaseReflectionService;
	}

	@PostMapping
	public ResponseEntity<PurchaseReflectionResponse> create(
		@CurrentUserId UUID userId,
		@RequestBody(required = false) PurchaseReflectionRequest request
	) {
		PurchaseReflectionResponse response = purchaseReflectionService.create(userId, request);
		return ResponseEntity
			.created(URI.create("/api/v1/reflections/" + response.id()))
			.body(response);
	}
}
