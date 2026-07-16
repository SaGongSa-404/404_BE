package com.sagongsa.backend.itemimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportService;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobAcceptedResponse;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobError;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobResponse;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobService;
import com.sagongsa.backend.itemimport.job.ShoppingImportJobStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ShoppingImportSyncServiceTest {

	private final ShoppingLinkImportService directService = mock(ShoppingLinkImportService.class);
	private final ShoppingImportJobService jobService = mock(ShoppingImportJobService.class);
	private final ShoppingImportProperties properties = new ShoppingImportProperties();
	private final ShoppingImportSyncService service = new ShoppingImportSyncService(directService, jobService, properties);
	private final UUID userId = UUID.randomUUID();
	private final UUID jobId = UUID.randomUUID();
	private final ShoppingLinkImportRequest request = new ShoppingLinkImportRequest(
		ItemInputSource.SHARE,
		"https://www.musinsa.com/products/6632593",
		null,
		null,
		null,
		null
	);

	@BeforeEach
	void setUp() {
		when(jobService.submit(userId, request)).thenReturn(
			new ShoppingImportJobAcceptedResponse(jobId, ShoppingImportJobStatus.PENDING, OffsetDateTime.now())
		);
	}

	@Test
	void returnsCompletedQueueResultWithOriginalResponseType() {
		ShoppingLinkImportResponse result = new ShoppingLinkImportResponse("SUCCESS", null, null, null, List.of());
		when(jobService.get(userId, jobId)).thenReturn(job(ShoppingImportJobStatus.SUCCEEDED, result, null));

		assertThat(service.importLink(userId, request)).isSameAs(result);
		verify(jobService).submit(userId, request);
		verifyNoInteractions(directService);
	}

	@Test
	void fallsBackToDirectImportWhenBridgeIsDisabled() {
		properties.getSyncBridge().setEnabled(false);
		ShoppingLinkImportResponse result = new ShoppingLinkImportResponse("SUCCESS", null, null, null, List.of());
		when(directService.importLink(request)).thenReturn(result);

		assertThat(service.importLink(userId, request)).isSameAs(result);
		verifyNoInteractions(jobService);
	}

	@Test
	void mapsQueueFailureToSynchronousHttpError() {
		ShoppingImportJobError error = new ShoppingImportJobError(
			"SHOPPING_PAGE_UNAVAILABLE",
			"쇼핑 페이지에 일시적으로 접근할 수 없습니다."
		);
		when(jobService.get(userId, jobId)).thenReturn(job(ShoppingImportJobStatus.FAILED, null, error));

		assertThatThrownBy(() -> service.importLink(userId, request))
			.isInstanceOfSatisfying(ResponseStatusException.class, exception ->
				assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)
			);
	}

	@Test
	void timesOutWithoutCancellingQueuedJob() {
		properties.getSyncBridge().setWaitTimeout(Duration.ofNanos(1));
		properties.getSyncBridge().setPollInterval(Duration.ofNanos(1));
		when(jobService.get(userId, jobId)).thenReturn(job(ShoppingImportJobStatus.PENDING, null, null));

		assertThatThrownBy(() -> service.importLink(userId, request))
			.isInstanceOfSatisfying(ResponseStatusException.class, exception ->
				assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
			);
		verify(jobService).submit(userId, request);
	}

	private ShoppingImportJobResponse job(
		ShoppingImportJobStatus status,
		ShoppingLinkImportResponse result,
		ShoppingImportJobError error
	) {
		return new ShoppingImportJobResponse(
			jobId,
			status,
			result,
			error,
			status == ShoppingImportJobStatus.PENDING ? 0 : 1,
			OffsetDateTime.now(),
			null,
			status == ShoppingImportJobStatus.PENDING ? null : OffsetDateTime.now()
		);
	}
}
