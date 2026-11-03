// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.service;

/**
 * Handle returned by {@link FlowControlManger#checkFlowControl} when a request passed the
 * flow-control gate. Close it only after the guarded work finishes so thread-grade rules
 * track real concurrency and response times are recorded; null means blocked.
 */
public interface FlowControlLease extends AutoCloseable {

    @Override
    void close();
}
