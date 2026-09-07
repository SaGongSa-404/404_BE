package com.sagongsa.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackendBoundaryTest {
	private static final Path SOURCE = Path.of("src/main/java/com/sagongsa/backend");

	@ParameterizedTest
	@ValueSource(strings = {"decision/DecisionCommands.java", "decision/DecisionQueries.java", "decision/DecisionPolicy.java", "decision/DecisionJdbcRepository.java", "mypage/ConsumptionService.java"})
	void decisionApplicationBoundaryDoesNotDependOnDecisionHttpDtos(String file) throws Exception {
		assertThat(Files.readString(SOURCE.resolve(file)))
			.doesNotContain("DecisionResultResponse", "DecisionCompleteRequest", "DecisionResultUpdateRequest");
	}

	@ParameterizedTest
	@ValueSource(strings = {"decision/DecisionPolicy.java", "wishlist/WishlistPolicy.java", "itemimport/item/ShoppingUrlNormalizer.java", "itemimport/item/ShoppingProductPolicy.java", "itemimport/item/ShoppingMetadataText.java"})
	void policiesDoNotReadDatabaseOrPerformNetworkIo(String file) throws Exception {
		assertThat(Files.readString(SOURCE.resolve(file)))
			.doesNotContain("JdbcTemplate", "EntityManager", "PageFetcher", "HttpClient", "RestClient", "@Transactional");
	}

	@ParameterizedTest
	@ValueSource(strings = {"decision/DecisionService.java", "decision/DecisionCommands.java", "home/HomeSummaryService.java", "mypage/MypageService.java", "itemimport/job/ShoppingImportJobWorker.java", "notification/PushNotificationService.java"})
	void orchestrationDoesNotExecuteSql(String file) throws Exception {
		assertThat(Files.readString(SOURCE.resolve(file))).doesNotContain("JdbcTemplate", "EntityManager", "createNativeQuery");
	}

	@ParameterizedTest
	@ValueSource(strings = {"domain/budget/BudgetLedger.java", "domain/budget/BudgetCycleRolloverService.java"})
	void budgetDoesNotDependOnDecisionOrWebAdapters(String file) throws Exception {
		assertThat(Files.readString(SOURCE.resolve(file))).doesNotContain("backend.decision.", "backend.mypage.", "org.springframework.web.");
	}
}
