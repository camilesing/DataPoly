// Use of this source code is governed by a BSD-style license
package com.cs.common.datatask;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Result-set type information for one delivered column, parallel to
 * {@link SinkRequest#getColumns()} and shaped through the same alias/order
 * projection. Both members are best-effort hints: JDBC drivers that cannot
 * expose type metadata (or expose it only partially) yield null entries, and
 * engines on drivers without metadata support may hand back an empty list.
 */
@Data
@Builder
public class ColumnMetadata implements Serializable {

    /** {@link java.sql.Types} constant reported by the JDBC driver, if any */
    private Integer jdbcType;

    /** Class name returned by {@link java.sql.ResultSetMetaData#getColumnClassName(int)}, if any */
    private String className;
}