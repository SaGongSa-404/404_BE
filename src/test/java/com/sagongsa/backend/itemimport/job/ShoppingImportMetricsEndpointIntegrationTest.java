package com.sagongsa.backend.itemimport.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = {
		"app.notification.reminder-worker.enabled=false",
		"app.notification.trigger-worker.enabled=false",
		"app.shopping.import.job-worker.enabled=false",
		"spring.datasource.hikari.maximum-pool-size=2",
		"management.server.address=127.0.0.1",
		"management.server.port=0",
		"management.prometheus.metrics.export.enabled=true",
		"management.endpoints.web.exposure.include=health,prometheus"
	}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShoppingImportMetricsEndpointIntegrationTest extends PostgreSqlContainerTest {

	@LocalManagementPort
	private int managementPort;

	@Autowired
	private ShoppingImportMetrics metrics;

	@Autowired
	private MeterRegistry meterRegistry;

	@Test
	void exposesShoppingImportMetricsOnLocalManagementPort() throws Exception {
		assertThat(meterRegistry.getClass().getName()).containsIgnoringCase("prometheus");
		metrics.recordActualCrawl("oliveyoung");

		HttpResponse<String> response = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + managementPort + "/actuator/prometheus"))
				.GET()
				.build(),
			HttpResponse.BodyHandlers.ofString()
		);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body())
			.contains("shopping_import_crawls_total")
			.contains("site=\"oliveyoung\"");
	}
}
