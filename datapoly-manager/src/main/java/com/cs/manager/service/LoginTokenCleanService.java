// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.persistence.dao.LoginTokenDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * Sweeps login session rows whose deadline has passed. Expiry is enforced on lookup anyway
 * (an expired row is deleted as it is found and never accepted), so this is housekeeping for
 * sessions nobody comes back for — after downtime the first pass can be a large one.
 */
@Slf4j
@Service
public class LoginTokenCleanService {

    @Resource
    private LoginTokenDao loginTokenDao;

    @EventListener(ApplicationReadyEvent.class)
    public void cleanOnceAfterRestart() {
        doCleanExpired();
    }

    @Scheduled(cron = "0 0 0 * * ? ")
    public void cleanSchedule() {
        doCleanExpired();
    }

    private void doCleanExpired() {
        try {
            int removed = loginTokenDao.deleteExpired(System.currentTimeMillis() / 1000L);
            log.info("Success to clean expired login session tokens, removed: {}", removed);
        } catch (Throwable t) {
            log.error("Failed to clean expired login session tokens,", t);
        }
    }
}