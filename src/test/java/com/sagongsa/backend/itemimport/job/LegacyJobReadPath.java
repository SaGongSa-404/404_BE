package com.sagongsa.backend.itemimport.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.itemimport.item.SavedItemDraft;
import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import com.sagongsa.backend.itemimport.item.WishlistSaveDraft;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

// Exact get/response decoding methods from f565eea; test-only comparison oracle.
class LegacyJobReadPath {
	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	LegacyJobReadPath(JdbcTemplate jdbc, ObjectMapper mapper) { jdbcTemplate=jdbc; objectMapper=mapper; }
	public ShoppingImportJobResponse get(UUID userId, UUID jobId) {
		return jdbcTemplate.query(
			"""
			select id, status, request_json::text, result_json::text, error_code, error_message, attempt_count,
			       created_at, started_at, completed_at
			from shopping_import_jobs
			where id = ? and user_id = ?
			""",
			(rs, rowNumber) -> new ShoppingImportJobResponse(
				rs.getObject("id", UUID.class),
				ShoppingImportJobStatus.valueOf(rs.getString("status")),
				readResult(rs.getString("result_json"), rs.getString("request_json")),
				readError(rs.getString("error_code"), rs.getString("error_message")),
				rs.getInt("attempt_count"),
				rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("started_at", OffsetDateTime.class),
				rs.getObject("completed_at", OffsetDateTime.class)
			),
			jobId,
			userId
		).stream().findFirst().orElseThrow(() ->
			new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping import job not found")
		);
	}

	private ShoppingLinkImportResponse readResult(String resultJson, String requestJson) {
		if (resultJson == null) {
			return null;
		}
		try {
			ShoppingLinkImportResponse result = objectMapper.readValue(resultJson, ShoppingLinkImportResponse.class);
			ShoppingLinkImportRequest request = objectMapper.readValue(requestJson, ShoppingLinkImportRequest.class);
			return personalize(result, request);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to deserialize shopping import result", exception);
		}
	}

	private ShoppingLinkImportResponse personalize(
		ShoppingLinkImportResponse result,
		ShoppingLinkImportRequest request
	) {
		if (request.inputSource() != ItemInputSource.SHARE || request.url() == null) {
			return result;
		}
		SavedItemDraft item = result.item();
		SavedItemDraft personalizedItem = item == null ? null : new SavedItemDraft(
			item.inputSource(),
			request.url(),
			item.normalizedUrl(),
			item.title(),
			request.brandName() == null || request.brandName().isBlank() ? item.brandName() : request.brandName(),
			item.summary(),
			item.imageUrl(),
			item.listedPrice(),
			item.currencyCode(),
			item.category(),
			item.categoryConfidence(),
			item.categoryLockedByUser(),
			item.status()
		);
		WishlistSaveDraft saveRequest = result.saveRequest();
		WishlistSaveDraft personalizedSaveRequest = saveRequest == null ? null : new WishlistSaveDraft(
			saveRequest.inputSource(),
			request.url(),
			saveRequest.normalizedUrl(),
			saveRequest.title(),
			saveRequest.imageUrl(),
			saveRequest.listedPrice(),
			saveRequest.currencyCode(),
			saveRequest.category(),
			saveRequest.categoryConfidence(),
			saveRequest.categoryLockedByUser(),
			saveRequest.sourceDomain(),
			saveRequest.rawTitle(),
			saveRequest.rawDescription(),
			saveRequest.rawPriceText(),
			saveRequest.rawPayloadJson()
		);
		return new ShoppingLinkImportResponse(
			result.retrievalStatus(),
			personalizedItem,
			result.sourceMetadata(),
			personalizedSaveRequest,
			result.warnings()
		);
	}

	private ShoppingImportJobError readError(String code, String message) {
		return code == null ? null : new ShoppingImportJobError(code, message);
	}

}
