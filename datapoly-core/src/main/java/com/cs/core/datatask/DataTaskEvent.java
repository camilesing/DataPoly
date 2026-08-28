// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.enums.DataTaskStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published on the executor node through the standard Spring event mechanism when a
 * job reaches a terminal state. Extensions wanting push-style frontend notification
 * (websocket/SSE/webhook) listen for this event with a plain @EventListener and may
 * filter on {@link #getSinkType()}.
 */
@Getter
public class DataTaskEvent extends ApplicationEvent {

    private final Long jobId;
    private final Long defId;
    private final String defName;
    private final DataTaskStatus status;
    private final long totalRows;
    private final String artifactUri;
    private final String errorMessage;
    private final String sinkType;

    public DataTaskEvent(Long jobId, Long defId, String defName, DataTaskStatus status,
                         long totalRows, String artifactUri, String errorMessage, String sinkType) {
        super(jobId);
        this.jobId = jobId;
        this.defId = defId;
        this.defName = defName;
        this.status = status;
        this.totalRows = totalRows;
        this.artifactUri = artifactUri;
        this.errorMessage = errorMessage;
        this.sinkType = sinkType;
    }
}
