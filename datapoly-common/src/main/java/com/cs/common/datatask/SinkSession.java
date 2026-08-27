// Use of this source code is governed by a BSD-style license
package com.cs.common.datatask;

import java.io.Closeable;
import java.util.List;

/**
 * One delivery channel instance per job. The engine feeds batches of positional row
 * values matching {@link SinkRequest#getColumns()}; implementations may buffer, chunk
 * (e.g. paginate worksheets) or stream onward. Rows must be consumed eagerly — the
 * iterator is only valid inside {@link #writeRows(Iterator)}.
 */
public interface SinkSession extends Closeable {

    /**
     * Write one batch; called repeatedly until the result set is exhausted.
     *
     * @return false to ask the engine to stop reading further rows (early completion)
     */
    boolean writeRows(Iterable<List<Object>> batch) throws Exception;

    /** Finish the delivery and produce the consumable outcome. */
    SinkOutcome complete() throws Exception;

    /** Best-effort cleanup when the job failed or was cancelled mid-flight. */
    default void abort(Throwable cause) {
        try {
            close();
        } catch (Exception ignore) {
            // abort must never mask the original failure
        }
    }
}
