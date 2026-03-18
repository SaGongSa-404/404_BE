package com.fourohfour.backend.worker.jobs;

import com.fourohfour.backend.modules.review.application.ReviewService;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BuildWeeklySnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(BuildWeeklySnapshotJob.class);

    private final ReviewService reviewService;

    public BuildWeeklySnapshotJob(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Transactional
    public void run() {
        LocalDate lastMonday = LocalDate.now()
                .minusWeeks(1)
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        reviewService.buildWeeklySnapshot(lastMonday);
        log.info("buildWeeklySnapshotJob completed for weekStart={}", lastMonday);
    }
}
