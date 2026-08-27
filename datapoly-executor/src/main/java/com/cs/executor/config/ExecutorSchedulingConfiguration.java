// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor scheduling thread pool: DataSourceCleanService (hourly) and
 * SentinelFlowControlManager (every minute) previously shared Spring's default
 * single-threaded scheduler, so a blocked task delayed the other. Provides a
 * dedicated TaskScheduler, adopted automatically by type via the scheduling
 * annotation post-processor.
 */
@Configuration
public class ExecutorSchedulingConfiguration implements SchedulingConfigurer {

    @Bean("executorTaskScheduler")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
        executor.setPoolSize(3);
        executor.setThreadNamePrefix("executor-scheduler-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar scheduledTaskRegistrar) {
        scheduledTaskRegistrar.setTaskScheduler(taskScheduler());
    }

}
