package com.sagongsa.backend.itemimport.item;

import java.net.URI;
import java.util.List;
import java.util.Map;

final class ShoppingExtractionData {

	record NormalizationResult(URI uri, List<String> warnings) {
	}

	record EmbeddedMetadata(
		String title,
		int titlePriority,
		String description,
		int descriptionPriority,
		String priceText,
		int pricePriority,
		String imageUrl,
		int imagePriority
	) {
		static EmbeddedMetadata empty() {
			return new EmbeddedMetadata(null, 0, null, 0, null, 0, null, 0);
		}

		EmbeddedMetadata merge(EmbeddedMetadata other) {
			return new EmbeddedMetadata(
				bestValue(title, titlePriority, other.title, other.titlePriority),
				bestPriority(title, titlePriority, other.title, other.titlePriority),
				bestValue(description, descriptionPriority, other.description, other.descriptionPriority),
				bestPriority(description, descriptionPriority, other.description, other.descriptionPriority),
				bestValue(priceText, pricePriority, other.priceText, other.pricePriority),
				bestPriority(priceText, pricePriority, other.priceText, other.pricePriority),
				bestValue(imageUrl, imagePriority, other.imageUrl, other.imagePriority),
				bestPriority(imageUrl, imagePriority, other.imageUrl, other.imagePriority)
			);
		}

		EmbeddedMetadata withTitle(String value, int priority) {
			return new EmbeddedMetadata(
				bestValue(title, titlePriority, value, priority),
				bestPriority(title, titlePriority, value, priority),
				description,
				descriptionPriority,
				priceText,
				pricePriority,
				imageUrl,
				imagePriority
			);
		}

		EmbeddedMetadata withDescription(String value, int priority) {
			return new EmbeddedMetadata(
				title,
				titlePriority,
				bestValue(description, descriptionPriority, value, priority),
				bestPriority(description, descriptionPriority, value, priority),
				priceText,
				pricePriority,
				imageUrl,
				imagePriority
			);
		}

		EmbeddedMetadata withPriceText(String value, int priority) {
			return new EmbeddedMetadata(
				title,
				titlePriority,
				description,
				descriptionPriority,
				bestValue(priceText, pricePriority, value, priority),
				bestPriority(priceText, pricePriority, value, priority),
				imageUrl,
				imagePriority
			);
		}

		EmbeddedMetadata withImageUrl(String value, int priority) {
			return new EmbeddedMetadata(
				title,
				titlePriority,
				description,
				descriptionPriority,
				priceText,
				pricePriority,
				bestValue(imageUrl, imagePriority, value, priority),
				bestPriority(imageUrl, imagePriority, value, priority)
			);
		}

		boolean hasAnyValue() {
			return title != null || description != null || priceText != null || imageUrl != null;
		}

		boolean hasAllProductValues() {
			return title != null && priceText != null && imageUrl != null;
		}

		static String bestValue(String current, int currentPriority, String next, int nextPriority) {
			if (next == null) {
				return current;
			}
			if (current == null || nextPriority > currentPriority) {
				return next;
			}
			return current;
		}

		static int bestPriority(String current, int currentPriority, String next, int nextPriority) {
			if (next == null) {
				return currentPriority;
			}
			if (current == null || nextPriority > currentPriority) {
				return nextPriority;
			}
			return currentPriority;
		}
	}

	record ZigzagMetadata(String title, String priceText, String imageUrl) {

		static ZigzagMetadata empty() {
			return new ZigzagMetadata(null, null, null);
		}

		boolean hasAnyValue() {
			return title != null || priceText != null || imageUrl != null;
		}
	}

	record ExtractionResult(
		String title,
		String brandName,
		String summary,
		Integer price,
		String rawPriceText,
		String currencyCode,
		String imageUrl,
		String method,
		Map<String, Object> rawPayloadJson
	) {
		String rawTitle() {
			return title;
		}

		String rawDescription() {
			return summary;
		}

		boolean isPartial() {
			return isBlank(title) || price == null || price <= 0 || isBlank(currencyCode) || isBlank(imageUrl);
		}

		boolean isBlank(String value) {
			return value == null || value.isBlank();
		}
	}
}
