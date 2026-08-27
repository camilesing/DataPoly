// Use of this source code is governed by a BSD-style license
package com.cs.core.extension;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published after a debug execution reaches a terminal state (success or failure).
 * Extensions wanting push-style reaction listen with a plain {@code @EventListener}.
 */
@Getter
public class ApiAssignmentDebugEvent extends ApplicationEvent {

    private final ApiDebugPostContext context;

    public ApiAssignmentDebugEvent(ApiDebugPostContext context) {
        super(context);
        this.context = context;
    }
}
