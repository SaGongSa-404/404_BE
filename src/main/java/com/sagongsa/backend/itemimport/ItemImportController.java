package com.sagongsa.backend.itemimport;

import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
public class ItemImportController {

	private final ShoppingLinkImportService shoppingLinkImportService;

	public ItemImportController(ShoppingLinkImportService shoppingLinkImportService) {
		this.shoppingLinkImportService = shoppingLinkImportService;
	}

	@PostMapping("/import-link")
	public ShoppingLinkImportResponse importLink(@RequestBody ShoppingLinkImportRequest request) {
		return shoppingLinkImportService.importLink(request);
	}
}
