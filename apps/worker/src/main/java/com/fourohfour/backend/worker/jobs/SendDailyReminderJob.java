package com.fourohfour.backend.worker.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SendDailyReminderJob {

    private static final Logger log = LoggerFactory.getLogger(SendDailyReminderJob.class);

    public void run() {
        log.info("sendDailyReminderJob triggered");
    }
}

