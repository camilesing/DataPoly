// Use of this source code is governed by a BSD-style license
package com.cs.core.extension;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published after an assignment update has been persisted. Extensions wanting
 * push-style reaction (cache eviction, audit trail, sync to external systems, ...)
 * listen with a plain {@code @EventListener}.
 */
@Getter
public class ApiAssignmentUpdateEvent extends ApplicationEvent {

    private final ApiUpdatePostContext context;

    public ApiAssignmentUpdateEvent(ApiUpdatePostContext context) {
        super(context);
        this.context = context;
    }
}
