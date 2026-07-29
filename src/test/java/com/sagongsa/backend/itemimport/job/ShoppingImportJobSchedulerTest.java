package com.sagongsa.backend.itemimport.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ShoppingImportJobSchedulerTest {

	@Test
	void runsConfiguredWorkersInParallel() throws Exception {
		ShoppingImportJobWorker worker = mock(ShoppingImportJobWorker.class);
		ShoppingImportProperties properties = new ShoppingImportProperties();
		properties.getJobWorker().setConcurrency(2);
		CountDownLatch started = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		when(worker.hasClaimableJob()).thenReturn(true);
		when(worker.processNextJob()).thenAnswer(invocation -> {
			started.countDown();
			assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
			return true;
		});

		try (
			ExecutorService workerExecutor = Executors.newFixedThreadPool(2);
			ExecutorService schedulerExecutor = Executors.newSingleThreadExecutor()
		) {
			ShoppingImportJobScheduler scheduler = new ShoppingImportJobScheduler(
				worker,
				properties,
				workerExecutor
			);
			Future<?> scheduled = schedulerExecutor.submit(scheduler::processNextJob);

			assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
			release.countDown();
			scheduled.get();
		}

		verify(worker, times(2)).processNextJob();
	}

	@Test
	void skipsWorkerFanOutWhenQueueHasNoClaimableJob() {
		ShoppingImportJobWorker worker = mock(ShoppingImportJobWorker.class);
		ShoppingImportProperties properties = new ShoppingImportProperties();
		ExecutorService workerExecutor = Executors.newSingleThreadExecutor();
		when(worker.hasClaimableJob()).thenReturn(false);

		try (workerExecutor) {
			ShoppingImportJobScheduler scheduler = new ShoppingImportJobScheduler(
				worker,
				properties,
				workerExecutor
			);

			scheduler.processNextJob();
		}

		verify(worker).hasClaimableJob();
		verify(worker, never()).processNextJob();
	}

	@Test
	void recoversStaleJobsOnIndependentSchedule() {
		ShoppingImportJobWorker worker = mock(ShoppingImportJobWorker.class);
		ShoppingImportProperties properties = new ShoppingImportProperties();

		try (ExecutorService workerExecutor = Executors.newSingleThreadExecutor()) {
			ShoppingImportJobScheduler scheduler = new ShoppingImportJobScheduler(
				worker,
				properties,
				workerExecutor
			);

			scheduler.recoverStaleJobs();
		}

		verify(worker).recoverStaleJobs();
	}
}
