// Use of this source code is governed by a BSD-style license
package com.cs.core.extension;

import com.cs.common.dto.OutParam;
import com.cs.core.dto.ApiDebugExecuteRequest;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Outcome of one debug execution handed to {@link ApiAssignmentPostProcessor#postDebug}
 * and published inside {@link ApiAssignmentDebugEvent}. Carries the full debug product
 * (result rows, execution logs, column types); treat instances as read-only.
 */
@Data
@Builder
public class ApiDebugPostContext {

    /** Original debug request (data source, engine, SQL list, parameter values). */
    private ApiDebugExecuteRequest request;

    /** Whether the execution completed successfully. */
    private boolean success;

    /** Reshaped query result (may be a List/Object/null depending on statement count). */
    private Object answer;

    /** Joined debug execution logs; null on failure. */
    private String logs;

    /** Column type metadata of the result; null on failure. */
    private List<OutParam> types;

    /** Failure reason; null on success. */
    private String errorMessage;

    /** Wall-clock duration of the execution in milliseconds. */
    private long elapsedMs;
}
