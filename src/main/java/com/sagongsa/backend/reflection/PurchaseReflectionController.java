package com.sagongsa.backend.reflection;

import com.sagongsa.backend.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reflections")
@Tag(name = "Reflection", description = "Post-purchase reflection API")
public class PurchaseReflectionController {

	private final PurchaseReflectionService purchaseReflectionService;

	public PurchaseReflectionController(PurchaseReflectionService purchaseReflectionService) {
		this.purchaseReflectionService = purchaseReflectionService;
	}

	@PostMapping
	@Operation(
		summary = "Create purchase reflection",
		description = "Stores satisfaction and regret feedback for a completed purchase decision.",
		responses = {
			@ApiResponse(responseCode = "201", description = "Reflection created"),
			@ApiResponse(responseCode = "400", description = "Invalid reflection payload"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "Decision belongs to another user"),
			@ApiResponse(responseCode = "404", description = "Decision or reminder does not exist"),
			@ApiResponse(responseCode = "409", description = "Reflection already exists")
		}
	)
	public ResponseEntity<PurchaseReflectionResponse> create(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@RequestBody(required = false) PurchaseReflectionRequest request
	) {
		PurchaseReflectionResponse response = purchaseReflectionService.create(userId, request);
		return ResponseEntity
			.created(URI.create("/api/v1/reflections/" + response.id()))
			.body(response);
	}
}
