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
	@ValueSource(strings = {"decision/DecisionPolicy.java", "wishlist/WishlistPolicy.java", "itemimport/item/ShoppingUrlNormalizer.java", "itemimport/item/ShoppingProductPolicy.java", "itemimport/item/ShoppingMetadataText.java", "notification/NotificationTriggerPolicy.java"})
	void policiesDoNotReadDatabaseOrPerformNetworkIo(String file) throws Exception {
		assertThat(Files.readString(SOURCE.resolve(file)))
			.doesNotContain("JdbcTemplate", "EntityManager", "PageFetcher", "HttpClient", "RestClient", "@Transactional");
	}

	@ParameterizedTest
	@ValueSource(strings = {"decision/DecisionService.java", "decision/DecisionCommands.java", "home/HomeSummaryService.java", "mypage/MypageService.java", "itemimport/job/ShoppingImportJobWorker.java", "itemimport/job/ShoppingImportJobService.java", "notification/PushNotificationService.java", "wishlist/WishlistService.java", "notification/NotificationTriggerWorker.java"})
	void orchestrationDoesNotExecuteSql(String file) throws Exception {
		assertThat(Files.readString(SOURCE.resolve(file))).doesNotContain("JdbcTemplate", "EntityManager", "createNativeQuery");
	}

	@ParameterizedTest
	@ValueSource(strings = {"domain/budget/BudgetLedger.java", "domain/budget/BudgetCycleRolloverService.java"})
	void budgetDoesNotDependOnDecisionOrWebAdapters(String file) throws Exception {
		assertThat(Files.readString(SOURCE.resolve(file))).doesNotContain("backend.decision.", "backend.mypage.", "org.springframework.web.");
	}

	@ParameterizedTest
	@ValueSource(strings = {"mypage/MypageService.java", "mypage/ProfileCommands.java"})
	void screenAndProfileUseSocialPublicQueryBoundary(String file) throws Exception {
		assertThat(Files.readString(SOURCE.resolve(file)))
			.doesNotContain("backend.domain.social.").contains("SocialActivityQueries");
	}

	@ParameterizedTest
	@ValueSource(strings = {"notification/NotificationTriggerPolicy.java", "notification/NotificationTriggerTargets.java"})
	void notificationRulesDoNotDependOnTargetQueryImplementation(String file) throws Exception {
		assertThat(Files.readString(SOURCE.resolve(file))).doesNotContain("NotificationTriggerQueries", "JdbcTemplate");
	}
}
