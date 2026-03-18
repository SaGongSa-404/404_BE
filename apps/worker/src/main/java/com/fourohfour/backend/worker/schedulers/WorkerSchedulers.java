package com.fourohfour.backend.worker.schedulers;

import com.fourohfour.backend.worker.jobs.BuildWeeklySnapshotJob;
import com.fourohfour.backend.worker.jobs.CleanupExpiredSessionsJob;
import com.fourohfour.backend.worker.jobs.DispatchOutboxEventsJob;
import com.fourohfour.backend.worker.jobs.RetryFailedPushJob;
import com.fourohfour.backend.worker.jobs.SendDailyReminderJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkerSchedulers {

    private final SendDailyReminderJob sendDailyReminderJob;
    private final DispatchOutboxEventsJob dispatchOutboxEventsJob;
    private final RetryFailedPushJob retryFailedPushJob;
    private final BuildWeeklySnapshotJob buildWeeklySnapshotJob;
    private final CleanupExpiredSessionsJob cleanupExpiredSessionsJob;

    public WorkerSchedulers(
            SendDailyReminderJob sendDailyReminderJob,
            DispatchOutboxEventsJob dispatchOutboxEventsJob,
            RetryFailedPushJob retryFailedPushJob,
            BuildWeeklySnapshotJob buildWeeklySnapshotJob,
            CleanupExpiredSessionsJob cleanupExpiredSessionsJob
    ) {
        this.sendDailyReminderJob = sendDailyReminderJob;
        this.dispatchOutboxEventsJob = dispatchOutboxEventsJob;
        this.retryFailedPushJob = retryFailedPushJob;
        this.buildWeeklySnapshotJob = buildWeeklySnapshotJob;
        this.cleanupExpiredSessionsJob = cleanupExpiredSessionsJob;
    }

    @Scheduled(cron = "${jobs.send-daily-reminder.cron:0 0 8 * * *}")
    public void scheduleDailyReminder() {
        sendDailyReminderJob.run();
    }

    @Scheduled(fixedDelayString = "${jobs.dispatch-outbox.fixed-delay-ms:10000}")
    public void scheduleOutboxDispatch() {
        dispatchOutboxEventsJob.run();
    }

    @Scheduled(fixedDelayString = "${jobs.retry-failed-push.fixed-delay-ms:300000}")
    public void scheduleRetryFailedPush() {
        retryFailedPushJob.run();
    }

    @Scheduled(cron = "${jobs.build-weekly-snapshot.cron:0 0 1 * * MON}")
    public void scheduleWeeklySnapshot() {
        buildWeeklySnapshotJob.run();
    }

    @Scheduled(cron = "${jobs.cleanup-expired-sessions.cron:0 0 * * * *}")
    public void scheduleCleanupExpiredSessions() {
        cleanupExpiredSessionsJob.run();
    }
}

