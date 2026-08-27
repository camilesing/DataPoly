// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.util.InetUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background poller turning this application instance into a data task worker.
 * Disabled unless {@code datapoly.data-task.enabled=true} (executor enables it).
 * Claimed jobs are handed to a small dedicated bounded pool so a long scan never
 * starves other @Scheduled work sharing the scheduler threads.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "datapoly.data-task", name = "enabled", havingValue = "true")
public class DataTaskWorker {

    private final String workerAddr;
    private final int maxConcurrentJobs;
    private final String reapMessage;
    private final ThreadPoolExecutor jobPool;
    private final AtomicInteger outstanding = new AtomicInteger();

    @Resource
    private DataTaskJobEngine engine;

    public DataTaskWorker(
            @Value("${datapoly.data-task.workers:2}") int maxConcurrentJobs,
            @Value("${datapoly.data-task.reap-message:data task lease expired, executor lost}") String reapMessage) {
        this.workerAddr = InetUtils.getLocalIpStr();
        this.maxConcurrentJobs = Math.max(1, maxConcurrentJobs);
        this.reapMessage = reapMessage;
        this.jobPool = new ThreadPoolExecutor(
                this.maxConcurrentJobs,
                this.maxConcurrentJobs,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(this.maxConcurrentJobs * 4),
                runnable -> {
                    Thread thread = new Thread(runnable, "data-task-worker-" + WorkerThreadCounter.next());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        ((ThreadPoolExecutor) this.jobPool).allowCoreThreadTimeOut(true);
        log.info("Data task worker enabled on {} with {} concurrent slots", workerAddr, this.maxConcurrentJobs);
    }

    @Scheduled(fixedDelayString = "${datapoly.data-task.poll-interval-ms:5000}")
    public void pollTick() {
        try {
            while (outstanding.get() < maxConcurrentJobs) {
                if (outstanding.incrementAndGet() > maxConcurrentJobs) {
                    outstanding.decrementAndGet();
                    break;
                }
                Long jobId;
                try {
                    jobId = engine.claimNext(workerAddr);
                } catch (Exception e) {
                    log.warn("Failed claiming data task job: {}", e.getMessage());
                    outstanding.decrementAndGet();
                    break;
                }
                if (null == jobId) {
                    outstanding.decrementAndGet();
                    break;
                }
                try {
                    jobPool.execute(() -> {
                        try {
                            engine.run(jobId);
                        } catch (Throwable t) {
                            log.error("Unhandled error running data task job {}", jobId, t);
                        } finally {
                            outstanding.decrementAndGet();
                        }
                    });
                } catch (Exception reject) {
                    outstanding.decrementAndGet();
                    log.warn("Job pool rejected data task job {}, retrying later", jobId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Data task poll tick failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${datapoly.data-task.reap-interval-ms:30000}")
    public void reapTick() {
        try {
            engine.reapLost(reapMessage);
        } catch (Exception e) {
            log.warn("Failed reaping expired data task jobs: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        jobPool.shutdown();
        try {
            jobPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class WorkerThreadCounter {
        private static final AtomicInteger COUNTER = new AtomicInteger(0);

        static int next() {
            return COUNTER.incrementAndGet();
        }
    }
}
