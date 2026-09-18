// Use of this source code is governed by a BSD-style license
package com.cs.common.datatask;

import com.cs.common.enums.ProductTypeEnum;
import lombok.Builder;
import lombok.Data;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Everything a {@link DataTaskStatementSink} needs to decide about, and then run, one
 * definition's statement outside the engine's row pipeline: the rendered SQL, the
 * definition's delivery configuration, the datasource it was authored against and the
 * cooperative cancellation probe.
 *
 * <p>The engine builds this object right after rendering the template and before any
 * JDBC work, so a sink can inspect it in
 * {@link DataTaskStatementSink#handlesStatement(DataTaskStatementRequest)} without
 * paying for a connection.</p>
 */
@Data
@Builder
public class DataTaskStatementRequest {

    private Long jobId;

    private String taskName;

    /** Registration type of the sink this definition selected. */
    private String sinkType;

    /**
     * Opaque JSON configuration stored on the task definition; its schema belongs to
     * the sink implementation. Secrets placed here are persisted in plain text in the
     * meta store — providers should reference server-side credentials instead.
     */
    private String sinkConfig;

    /**
     * Statement rendered from the definition template. For server-side execution this
     * text must be complete: drivers of these engines generally accept no bind
     * parameters, so callers author such definitions with {@code ${param}} inlining
     * ({@code dollarAllowed}) rather than {@code #{param}}.
     */
    private String sql;

    /**
     * Bind values the renderer left behind as placeholders; a non-empty list means
     * {@link #sql} is not executable as-is and the definition must be rewritten with
     * inlined parameters.
     */
    private List<Object> sqlParameters;

    /**
     * Whether {@link #sql} is a query (SELECT/WITH, ignoring leading whitespace, parens and
     * comments). Server-side exports wrap the statement in their own command — MaxCompute's
     * {@code UNLOAD FROM (<sql>)}, for instance — which only accepts a query, so a sink
     * claiming such definitions must leave everything else (DML, DDL, engine-specific
     * statements) on the ordinary path.
     */
    private boolean query;

    /** Boundary values that produced {@link #sql}, for logging and diagnostics. */
    private Map<String, Object> params;

    private Long datasourceId;

    private ProductTypeEnum product;

    /** Pooled datasource of the definition, shared with the synchronous API path. */
    private DataSource dataSource;

    /** Account id of the submitter, as carried by the job record. */
    private String submittedBy;

    /**
     * True once the job row left RUNNING or a cancel was requested — the same predicate
     * the row pipeline uses. Long-running statements should poll it before submitting;
     * an already-submitted server-side job will finish regardless.
     */
    private BooleanSupplier cancelled;
}