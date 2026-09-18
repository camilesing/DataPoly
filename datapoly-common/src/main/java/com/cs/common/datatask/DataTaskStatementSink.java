// Use of this source code is governed by a BSD-style license
package com.cs.common.datatask;

/**
 * Optional capability of a {@link DataTaskSink}: some deliveries must not be produced by
 * this JVM at all. Server-side exports (MaxCompute {@code UNLOAD}, warehouse
 * {@code INSERT OVERWRITE DIRECTORY}, ...) complete inside the source engine and write
 * straight to the target store, so the engine must hand over the statement instead of
 * executing it and pushing rows.
 *
 * <p>{@link #handlesStatement(DataTaskStatementRequest)} is consulted once per job, right
 * after the template is rendered and before any JDBC execution. Returning {@code true}
 * makes the engine skip the row pipeline entirely — no {@link SinkSession}, no row limit,
 * no column reshaping — and call {@link #executeStatement(DataTaskStatementRequest)}
 * instead; the outcome is recorded on the job like any other sink outcome. Returning
 * {@code false} leaves the definition on the ordinary path, so one sink can serve both
 * modes (e.g. stream to object storage for most engines, delegate to the engine's own
 * export command for one of them).</p>
 *
 * <p>Implementations own resource cleanup for the delegated path: the engine has no
 * session to abort when the statement fails or the job is cancelled mid-flight.</p>
 */
public interface DataTaskStatementSink extends DataTaskSink {

    /**
     * Non-statement implementations never reach this method — a sink that claims a
     * definition via {@link #handlesStatement} is driven through
     * {@link #executeStatement} only. Sinks serving both modes override it normally.
     */
    @Override
    default SinkSession openSession(SinkRequest request) throws Exception {
        throw new UnsupportedOperationException(
                type() + ": this sink is driven through executeStatement, not openSession");
    }

    /** @return true to have the engine delegate this definition's statement to the sink */
    boolean handlesStatement(DataTaskStatementRequest request);

    SinkOutcome executeStatement(DataTaskStatementRequest request) throws Exception;
}