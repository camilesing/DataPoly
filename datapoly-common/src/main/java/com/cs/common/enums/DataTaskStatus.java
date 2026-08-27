// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

/**
 * Lifecycle states of an asynchronous data task job.
 * PENDING -> RUNNING -> SUCCESS/FAILED; a queued job may go straight to CANCELED,
 * a running one first carries cancel_requested and ends as CANCELED cooperatively.
 */
public enum DataTaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED;

    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == CANCELED;
    }
}
