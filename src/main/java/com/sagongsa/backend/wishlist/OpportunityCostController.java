package com.sagongsa.backend.wishlist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Wishlist", description = "Saved wishlist item create, list, detail, update, category update, and delete APIs")
public class OpportunityCostController {

	private final OpportunityCostService opportunityCostService;

	public OpportunityCostController(OpportunityCostService opportunityCostService) {
		this.opportunityCostService = opportunityCostService;
	}

	@GetMapping({"/api/v1/wishlist/opportunity-cost", "/api/v1/wishes/opportunity-cost"})
	@Operation(
		summary = "Calculate opportunity cost preview",
		description = "Returns an opportunity-cost item for a wishlist price and category before saving or updating the item.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Opportunity cost returned"),
			@ApiResponse(responseCode = "400", description = "Invalid price or category")
		}
	)
	public OpportunityCostResponse calculate(
		@Parameter(required = true, example = "35000") @RequestParam Integer price,
		@Parameter(required = true, example = "FASHION") @RequestParam String category
	) {
		return opportunityCostService.calculate(price, category);
	}
}
