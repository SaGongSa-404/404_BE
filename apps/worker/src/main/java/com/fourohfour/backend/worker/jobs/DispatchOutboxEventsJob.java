package com.fourohfour.backend.worker.jobs;

import com.fourohfour.backend.modules.notification.application.NotificationService;
import com.fourohfour.backend.packages.events.OutboxJdbcRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DispatchOutboxEventsJob {

    private static final Logger log = LoggerFactory.getLogger(DispatchOutboxEventsJob.class);

    private final OutboxJdbcRepository outboxJdbcRepository;
    private final NotificationService notificationService;

    public DispatchOutboxEventsJob(OutboxJdbcRepository outboxJdbcRepository, NotificationService notificationService) {
        this.outboxJdbcRepository = outboxJdbcRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void run() {
        outboxJdbcRepository.listPendingEvents(50, Instant.now()).forEach(eventRecord -> {
            try {
                notificationService.createFromOutboxEvent(eventRecord);
                outboxJdbcRepository.markDispatched(eventRecord.outboxEventId(), Instant.now());
            } catch (RuntimeException exception) {
                log.warn("failed to dispatch outbox event {}", eventRecord.outboxEventId(), exception);
                outboxJdbcRepository.markFailed(eventRecord.outboxEventId(), Instant.now().plus(Duration.ofMinutes(5)), Instant.now());
            }
        });
    }
}
