package com.sagongsa.backend.itemimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.domain.enums.ItemStatus;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobService;
import com.sagongsa.backend.itemimport.item.ItemSourceMetadataDraft;
import com.sagongsa.backend.itemimport.item.SavedItemDraft;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ItemImportControllerTest {

	@Test
	void logsImportResultWithoutProductDetails(CapturedOutput output) {
		ShoppingLinkImportService service = mock(ShoppingLinkImportService.class);
		ShoppingImportJobService jobService = mock(ShoppingImportJobService.class);
		ItemImportController controller = new ItemImportController(service, jobService);
		ShoppingLinkImportRequest request = new ShoppingLinkImportRequest(
			ItemInputSource.SHARE,
			"https://ably.airbridge.io/goods/70247267?tracking_content=secret",
			null,
			null,
			null,
			null
		);
		ShoppingLinkImportResponse response = response();
		when(service.importLink(request)).thenReturn(response);

		assertThat(controller.importLink(request)).isSameAs(response);

		assertThat(output)
			.contains("shopping link import completed retrievalStatus=SUCCESS")
			.contains("sourceDomain=m.a-bly.com")
			.contains("listedPrice=52110")
			.contains("currencyCode=KRW")
			.contains("extractionMethod=ABLY_PRODUCT_META")
			.doesNotContain("tracking_content=secret")
			.doesNotContain("테스트 전용 상품명")
			.doesNotContain("https://cdn.example.com/private-product.jpg");
	}

	private ShoppingLinkImportResponse response() {
		SavedItemDraft item = new SavedItemDraft(
			ItemInputSource.SHARE,
			"https://ably.airbridge.io/goods/70247267?tracking_content=secret",
			"https://m.a-bly.com/goods/70247267",
			"테스트 전용 상품명",
			null,
			null,
			"https://cdn.example.com/private-product.jpg",
			52110,
			"KRW",
			ItemCategory.FASHION,
			0.35d,
			false,
			ItemStatus.SAVED
		);
		ItemSourceMetadataDraft sourceMetadata = new ItemSourceMetadataDraft(
			"m.a-bly.com",
			"테스트 전용 상품명",
			null,
			"52,110원",
			null,
			Instant.parse("2026-07-15T00:00:00Z"),
			"ABLY_PRODUCT_META"
		);
		return new ShoppingLinkImportResponse("SUCCESS", item, sourceMetadata, null, List.of());
	}
}
