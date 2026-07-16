package com.sagongsa.backend.itemimport;

import com.sagongsa.backend.auth.CurrentUserId;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobAcceptedResponse;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobResponse;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobService;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
@Tag(name = "Item Import", description = "Shopping link preview API before saving an item to wishlist")
public class ItemImportController {

	private static final Logger log = LoggerFactory.getLogger(ItemImportController.class);

	private final ShoppingImportSyncService shoppingImportSyncService;
	private final ShoppingImportJobService shoppingImportJobService;

	public ItemImportController(
		ShoppingImportSyncService shoppingImportSyncService,
		ShoppingImportJobService shoppingImportJobService
	) {
		this.shoppingImportSyncService = shoppingImportSyncService;
		this.shoppingImportJobService = shoppingImportJobService;
	}

	@PostMapping("/import-link")
	@Operation(
		summary = "Import shopping link",
		description = "Creates a wishlist save draft from a shared or manually entered shopping link payload.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Import preview generated"),
			@ApiResponse(responseCode = "400", description = "Invalid import payload"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "429", description = "Import queue is full"),
			@ApiResponse(responseCode = "504", description = "Import is still processing after the synchronous wait limit")
		}
	)
	public ShoppingLinkImportResponse importLink(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@RequestBody ShoppingLinkImportRequest request
	) {
		ShoppingLinkImportResponse response = shoppingImportSyncService.importLink(userId, request);
		logImportResult(response);
		return response;
	}

	private void logImportResult(ShoppingLinkImportResponse response) {
		var item = response.item();
		var sourceMetadata = response.sourceMetadata();
		log.info(
			"shopping link import completed retrievalStatus={} sourceDomain={} listedPrice={} currencyCode={} extractionMethod={}",
			response.retrievalStatus(),
			sourceMetadata == null ? null : sourceMetadata.sourceDomain(),
			item == null ? null : item.listedPrice(),
			item == null ? null : item.currencyCode(),
			sourceMetadata == null ? null : sourceMetadata.extractionMethod()
		);
	}

	@PostMapping("/import-jobs")
	@Operation(
		summary = "Submit shopping link import job",
		description = "Queues a shopping link import and immediately returns a job identifier.",
		responses = {
			@ApiResponse(responseCode = "202", description = "Import job accepted"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "429", description = "Import queue is full")
		}
	)
	public ResponseEntity<ShoppingImportJobAcceptedResponse> submitImportJob(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@RequestBody ShoppingLinkImportRequest request
	) {
		ShoppingImportJobAcceptedResponse response = shoppingImportJobService.submit(userId, request);
		URI location = URI.create("/api/v1/items/import-jobs/" + response.jobId());
		return ResponseEntity.accepted().location(location).body(response);
	}

	@GetMapping("/import-jobs/{jobId}")
	@Operation(
		summary = "Get shopping link import job",
		description = "Returns the authenticated user's import job state and result when completed.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Import job found"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "404", description = "Import job not found")
		}
	)
	public ShoppingImportJobResponse getImportJob(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@PathVariable UUID jobId
	) {
		return shoppingImportJobService.get(userId, jobId);
	}
}
