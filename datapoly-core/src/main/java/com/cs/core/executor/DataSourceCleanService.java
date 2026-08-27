// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.executor;

import com.cs.core.util.DataSourceUtils;
import com.cs.persistence.dao.DataSourceDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Set;

@Slf4j
@Service
public class DataSourceCleanService {

    @Resource
    private DataSourceDao dataSourceDao;

    /*Runs once per hour*/
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${cron.datasource.clean.expression:0 0 0/1 * * ? }")
    public void autoClean() {
        log.info("Start check deleted datasource for close it ...");
        try {
            Set<Long> running = DataSourceUtils.getAllDataSourceIdSet();
            Set<Long> exists = dataSourceDao.getAllIdList();
            for (Long id : running) {
                if (!exists.contains(id)) {
                    DataSourceUtils.dropHikariDataSource(id);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to auto clean deleted datasource,error: {}", e.getMessage(), e);
        }
    }


}
