// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.persistence.mapper.AccessRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class AccessLogCleanService {

    @Value("${access.log.clean.days:30}")
    private Integer cleanJobLogDays;

    @Resource
    private AccessRecordMapper accessRecordMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void cleanOnceAfterRestart() {
        doCleanHistoryLog();
    }

    @Scheduled(cron = "0 0 0 * * ? ")
    public void cleanSchedule() {
        doCleanHistoryLog();
    }

    private void doCleanHistoryLog() {
        try {
            accessRecordMapper.deleteHistoryBeforeDays(cleanJobLogDays);
            log.info("Success to clean history access log for {} days", cleanJobLogDays);
        } catch (Throwable t) {
            log.error("Failed to clean history access log,", t);
        }
    }
}
