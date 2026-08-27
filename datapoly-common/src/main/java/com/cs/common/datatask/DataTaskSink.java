// Use of this source code is governed by a BSD-style license
package com.cs.common.datatask;

/**
 * Extension point delivering the reshaped query result of an asynchronous data task
 * to an arbitrary destination (object storage, message broker, filesystem, ...).
 *
 * <p>Providers register either as Spring beans (host application classpath) or via
 * {@code META-INF/services/com.cs.common.datatask.DataTaskSink}. A task definition
 * selects one provider by {@link #type()}.</p>
 */
public interface DataTaskSink {

    /** Unique registration identifier referenced by task definitions. */
    String type();

    /**
     * Open a fresh session for one job invocation.
     *
     * @param request immutable delivery context (identity, config, resolved headers)
     */
    SinkSession openSession(SinkRequest request) throws Exception;
}
