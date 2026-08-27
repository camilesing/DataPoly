// Use of this source code is governed by a BSD-style license
package com.cs.core.extension;

import com.cs.core.dto.ApiAssignmentSaveRequest;
import com.cs.persistence.entity.ApiAssignmentEntity;
import lombok.Builder;
import lombok.Data;

/**
 * Snapshot of one persisted assignment update handed to
 * {@link ApiAssignmentPostProcessor#postUpdate} and published inside
 * {@link ApiAssignmentUpdateEvent}; treat instances as read-only.
 */
@Data
@Builder
public class ApiUpdatePostContext {

    /** Original update request as validated by the service. */
    private ApiAssignmentSaveRequest request;

    /** Entity state as persisted (method and path are immutable and not updated). */
    private ApiAssignmentEntity entity;
}
