// Use of this source code is governed by a BSD-style license
package com.cs.common.datatask;

import com.cs.common.enums.DataTypeFormatEnum;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Everything a {@link DataTaskSink} needs to open a delivery session.
 * Columns are already reshaped (renamed/reordered) by the engine; cell values arrive
 * as raw JDBC-normalized Java objects formatted according to outputFormats only when
 * the task asks for stringified cells — sinks rendering typed content should prefer
 * applying these patterns themselves.
 */
@Data
@Builder
public class SinkRequest implements Serializable {

    private Long jobId;

    private String taskName;

    /** Registration type of the sink that opened the session */
    private String sinkType;

    /**
     * Opaque JSON configuration stored on the task definition; its schema belongs to
     * the sink implementation. Secrets placed here are persisted in plain text in the
     * meta store — providers should reference server-side credentials instead.
     */
    private String sinkConfig;

    /** Resolved (post naming-strategy/alias/order) column headers */
    private List<String> columns;

    /** Per-type display patterns declared on the task (DATE/TIMESTAMP/BIG_DECIMAL ...) */
    private Map<DataTypeFormatEnum, String> outputFormats;

    private String submittedBy;
}
