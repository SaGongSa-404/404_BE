package com.fourohfour.backend.worker.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CleanupExpiredSessionsJob {

    private static final Logger log = LoggerFactory.getLogger(CleanupExpiredSessionsJob.class);

    public void run() {
        log.info("cleanupExpiredSessionsJob triggered");
    }
}

