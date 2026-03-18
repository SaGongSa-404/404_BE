package com.fourohfour.backend.worker.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RetryFailedPushJob {

    private static final Logger log = LoggerFactory.getLogger(RetryFailedPushJob.class);

    public void run() {
        log.info("retryFailedPushJob triggered");
    }
}

