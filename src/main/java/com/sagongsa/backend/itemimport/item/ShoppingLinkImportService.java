package com.sagongsa.backend.itemimport.item;

import static com.sagongsa.backend.itemimport.item.ShoppingExtractionData.*;
import static com.sagongsa.backend.itemimport.item.ShoppingUrlNormalizer.*;
import static com.sagongsa.backend.itemimport.item.ShoppingProductPolicy.*;
import static com.sagongsa.backend.itemimport.item.ShoppingMetadataText.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.domain.enums.ItemStatus;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingLinkImportService {
	private static final double DEFAULT_CATEGORY_CONFIDENCE = 0.35d;
	private final PageFetcher pageFetcher;
	private final ObjectMapper objectMapper;
	private final ShoppingPageExtractor extractor;
	public ShoppingLinkImportService(PageFetcher pageFetcher, ObjectMapper objectMapper) {
		this.pageFetcher = pageFetcher;
		this.objectMapper = objectMapper;
		this.extractor = new ShoppingPageExtractor(objectMapper);
	}

	public ShoppingLinkImportResponse importLink(ShoppingLinkImportRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}

		ItemInputSource inputSource = Optional.ofNullable(request.inputSource())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "inputSource is required"));

		return switch (inputSource) {
			case SHARE -> importSharedLink(request);
			case DIRECT_INPUT -> importManualInput(request);
		};
	}

	private ShoppingLinkImportResponse importSharedLink(ShoppingLinkImportRequest request) {
		URI originalUri = parseHttpUri(request.url(), "url is required for SHARE");
		NormalizationResult normalized = normalizeShoppingUri(originalUri);
		FetchedPage page = pageFetcher.fetch(normalized.uri());

		if (page.statusCode() >= 400) {
			throw new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Shopping page returned " + page.statusCode()
			);
		}

		Document document = Jsoup.parse(page.body(), page.finalUri().toString());
		if (isBlockedShoppingPage(document, page)) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Shopping page access was challenged");
		}
		ExtractionResult extracted = extractor.extractFromPage(document, page);
		List<String> warnings = new ArrayList<>(normalized.warnings());
		validateVerifiedProduct(originalUri, page.finalUri(), extracted);

		if (extracted.isPartial()) {
			warnings.add("상품 메타데이터가 일부만 추출되었습니다.");
		}

		if (isBlank(extracted.title())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unable to extract shopping item title");
		}
		if (extracted.price() == null && isBlank(extracted.imageUrl())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unable to extract shopping metadata");
		}

		ItemCategory category = classifyCategory(sourceDomain(page.finalUri()), extracted.title(), extracted.summary());
		SavedItemDraft item = new SavedItemDraft(
			ItemInputSource.SHARE,
			originalUri.toString(),
			page.finalUri().toString(),
			extracted.title(),
			firstNonBlank(request.brandName(), extracted.brandName()),
			extracted.summary(),
			extracted.imageUrl(),
			extracted.price(),
			extracted.currencyCode(),
			category,
			category == ItemCategory.ETC ? null : DEFAULT_CATEGORY_CONFIDENCE,
			false,
			ItemStatus.SAVED
		);
		ItemSourceMetadataDraft sourceMetadata = new ItemSourceMetadataDraft(
			sourceDomain(page.finalUri()),
			extracted.rawTitle(),
			extracted.rawDescription(),
			extracted.rawPriceText(),
			toJson(extracted.rawPayloadJson()),
			Instant.now(),
			extracted.method()
		);

		return new ShoppingLinkImportResponse(
			extracted.isPartial() ? "PARTIAL" : "SUCCESS",
			item,
			sourceMetadata,
			toWishlistSaveDraft(item, sourceMetadata),
			List.copyOf(warnings)
		);
	}

	private ShoppingLinkImportResponse importManualInput(ShoppingLinkImportRequest request) {
		if (isBlank(request.title())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required for DIRECT_INPUT");
		}
		if (request.price() != null && request.price() < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price must be zero or greater for DIRECT_INPUT");
		}

		URI normalizedUri = null;
		List<String> warnings = new ArrayList<>();
		if (!isBlank(request.url())) {
			NormalizationResult normalizationResult = normalizeShoppingUri(parseHttpUri(request.url(), "Invalid url"));
			normalizedUri = normalizationResult.uri();
			warnings.addAll(normalizationResult.warnings());
		}

		String normalizedTitle = normalizeWhitespace(request.title());
		String normalizedBrandName = normalizeWhitespace(request.brandName());
		String normalizedImageUrl = normalizeImageUrl(request.imageUrl());
		ItemCategory category = classifyCategory(
			normalizedUri == null ? null : sourceDomain(normalizedUri),
			normalizedTitle,
			null
		);

		SavedItemDraft item = new SavedItemDraft(
			ItemInputSource.DIRECT_INPUT,
			blankToNull(request.url()),
			normalizedUri == null ? null : normalizedUri.toString(),
			normalizedTitle,
			normalizedBrandName,
			null,
			normalizedImageUrl,
			request.price(),
			request.price() == null ? null : "KRW",
			category,
			null,
			false,
			ItemStatus.SAVED
		);
		ItemSourceMetadataDraft sourceMetadata = new ItemSourceMetadataDraft(
			normalizedUri == null ? null : sourceDomain(normalizedUri),
			normalizedTitle,
			null,
			request.price() == null ? null : String.valueOf(request.price()),
			toJson(Map.of(
				"inputSource", ItemInputSource.DIRECT_INPUT.name(),
				"manualFields", List.of("title", "brandName", "price", "imageUrl")
			)),
			Instant.now(),
			"MANUAL"
		);

		return new ShoppingLinkImportResponse(
			"SUCCESS",
			item,
			sourceMetadata,
			toWishlistSaveDraft(item, sourceMetadata),
			List.copyOf(warnings)
		);
	}

	private String toJson(Map<String, Object> rawPayloadJson) {
		try {
			return objectMapper.writeValueAsString(rawPayloadJson);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to serialize import metadata", exception);
		}
	}

	private WishlistSaveDraft toWishlistSaveDraft(SavedItemDraft item, ItemSourceMetadataDraft metadata) {
		return new WishlistSaveDraft(
			item.inputSource(),
			item.originalUrl(),
			item.normalizedUrl(),
			item.title(),
			item.imageUrl(),
			item.listedPrice(),
			item.currencyCode(),
			item.category(),
			item.categoryConfidence(),
			item.categoryLockedByUser(),
			metadata.sourceDomain(),
			metadata.rawTitle(),
			metadata.rawDescription(),
			metadata.rawPriceText(),
			metadata.rawPayloadJson()
		);
	}
}
