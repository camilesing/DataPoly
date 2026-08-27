// Use of this source code is governed by a BSD-style license
package com.cs.core.extension;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Composite over all registered {@link ApiAssignmentPostProcessor} providers plus the
 * Spring event fan-out. Each hook is isolated: a throwing processor is logged and
 * skipped, and the corresponding event is still published at the end.
 */
@Slf4j
public class ApiAssignmentPostProcessors {

    private final List<ApiAssignmentPostProcessor> processors;
    private final ApplicationEventPublisher eventPublisher;

    public ApiAssignmentPostProcessors(List<ApiAssignmentPostProcessor> processors,
                                       ApplicationEventPublisher eventPublisher) {
        List<ApiAssignmentPostProcessor> copy = new ArrayList<>();
        if (null != processors) {
            copy.addAll(processors);
        }
        this.processors = Collections.unmodifiableList(copy);
        this.eventPublisher = eventPublisher;
    }

    /** No-op instance used when no extension is wired (unit tests, service default). */
    public static ApiAssignmentPostProcessors empty() {
        return new ApiAssignmentPostProcessors(Collections.<ApiAssignmentPostProcessor>emptyList(), null);
    }

    /**
     * Merge registration channels: Spring beans first (already ordered by {@code @Order}
     * through list injection), then JDK ServiceLoader providers appended. A class
     * registered both ways fires only once — the Spring bean wins.
     */
    public static ApiAssignmentPostProcessors aggregate(List<ApiAssignmentPostProcessor> springBeans,
                                                        ApplicationEventPublisher eventPublisher) {
        List<ApiAssignmentPostProcessor> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (null != springBeans) {
            for (ApiAssignmentPostProcessor processor : springBeans) {
                if (seen.add(processor.getClass().getName())) {
                    merged.add(processor);
                }
            }
        }
        try {
            for (ApiAssignmentPostProcessor processor : ServiceLoader.load(
                    ApiAssignmentPostProcessor.class, ApiAssignmentPostProcessor.class.getClassLoader())) {
                if (seen.add(processor.getClass().getName())) {
                    merged.add(processor);
                } else {
                    log.warn("Ignore SPI ApiAssignmentPostProcessor {} already registered as Spring bean",
                            processor.getClass().getName());
                }
            }
        } catch (Throwable t) {
            log.warn("Failed to discover ApiAssignmentPostProcessor providers via ServiceLoader: {}", t.getMessage());
        }
        return new ApiAssignmentPostProcessors(merged, eventPublisher);
    }

    /** Registered processors in invocation order (Spring beans ordered by @Order, SPI appended). */
    public List<ApiAssignmentPostProcessor> registered() {
        return processors;
    }

    public void postDebug(ApiDebugPostContext context) {
        for (ApiAssignmentPostProcessor processor : processors) {
            try {
                processor.postDebug(context);
            } catch (Exception e) {
                log.warn("postDebug processor {} failed: {}", processor.getClass().getName(), e.getMessage(), e);
            }
        }
        publish(new ApiAssignmentDebugEvent(context), "debug");
    }

    public void postUpdate(ApiUpdatePostContext context) {
        for (ApiAssignmentPostProcessor processor : processors) {
            try {
                processor.postUpdate(context);
            } catch (Exception e) {
                log.warn("postUpdate processor {} failed: {}", processor.getClass().getName(), e.getMessage(), e);
            }
        }
        publish(new ApiAssignmentUpdateEvent(context), "update");
    }

    private void publish(Object event, String action) {
        if (null == eventPublisher) {
            return;
        }
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish api assignment {} event: {}", action, e.getMessage());
        }
    }
}
