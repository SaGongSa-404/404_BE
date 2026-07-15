package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShoppingImportPropertiesTest {

	@Test
	void limitsActiveJobsToOnePerUserByDefault() {
		ShoppingImportProperties properties = new ShoppingImportProperties();

		assertThat(properties.getJobWorker().getMaxActivePerUser()).isEqualTo(1);
	}

	@Test
	void fallsBackToOneWhenMaxActivePerUserIsNotPositive() {
		ShoppingImportProperties properties = new ShoppingImportProperties();

		properties.getJobWorker().setMaxActivePerUser(0);

		assertThat(properties.getJobWorker().getMaxActivePerUser()).isEqualTo(1);
	}
}
