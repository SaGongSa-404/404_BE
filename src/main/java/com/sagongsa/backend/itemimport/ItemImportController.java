package com.sagongsa.backend.itemimport;

import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
@Tag(name = "Item Import", description = "Shopping link preview API before saving an item to wishlist")
public class ItemImportController {

	private final ShoppingLinkImportService shoppingLinkImportService;

	public ItemImportController(ShoppingLinkImportService shoppingLinkImportService) {
		this.shoppingLinkImportService = shoppingLinkImportService;
	}

	@PostMapping("/import-link")
	@Operation(
		summary = "Import shopping link",
		description = "Creates a wishlist save draft from a shared or manually entered shopping link payload.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Import preview generated"),
			@ApiResponse(responseCode = "400", description = "Invalid import payload"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
		}
	)
	public ShoppingLinkImportResponse importLink(@RequestBody ShoppingLinkImportRequest request) {
		return shoppingLinkImportService.importLink(request);
	}
}
