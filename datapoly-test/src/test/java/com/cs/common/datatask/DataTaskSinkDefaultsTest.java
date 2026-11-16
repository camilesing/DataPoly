// Use of this source code is governed by a BSD-style license
package com.cs.common.datatask;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Covers the default methods on the sink contracts: {@link SinkSession#abort(Throwable)}
 * (close-once, never throws), {@link SinkOutcome#empty()}, and the statement-sink
 * defaults ({@code openSession} unsupported, inline-parameters off).
 */
public class DataTaskSinkDefaultsTest {

    @Test
    public void testAbortClosesSessionExactlyOnceAndSwallowsCloseFailure() {
        final AtomicInteger closes = new AtomicInteger();
        SinkSession session = new SinkSession() {
            @Override
            public boolean writeRows(Iterable<List<Object>> batch) {
                return true;
            }

            @Override
            public SinkOutcome complete() {
                return SinkOutcome.empty();
            }

            @Override
            public void close() {
                closes.incrementAndGet();
                throw new IllegalStateException("close must not propagate");
            }
        };

        session.abort(new RuntimeException("job failed"));
        session.abort(null);
        assertEquals("abort delegates to close every time", 2, closes.get());
    }

    @Test
    public void testEmptyOutcomeHasNoArtifactAndNoInfo() {
        SinkOutcome outcome = SinkOutcome.empty();
        assertNull(outcome.getArtifactUri());
        assertNull(outcome.getInfo());
        // lombok value semantics: two empty outcomes are equal
        assertEquals(SinkOutcome.builder().build(), outcome);
    }

    @Test
    public void testStatementSinkDefaults() throws Exception {
        DataTaskStatementSink sink = new DataTaskStatementSink() {
            @Override
            public String type() {
                return "demo-sink";
            }

            @Override
            public boolean handlesStatement(DataTaskStatementRequest request) {
                return false;
            }

            @Override
            public SinkOutcome executeStatement(DataTaskStatementRequest request) {
                return SinkOutcome.empty();
            }
        };

        assertFalse("bind parameters stay the default", sink.requiresInlinedParameters());
        try {
            sink.openSession(SinkRequest.builder().build());
            fail("statement sinks must not be driven through openSession");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("demo-sink"));
            assertTrue(expected.getMessage().contains("executeStatement"));
        }
        // the request passed along stays a plain data carrier
        SinkRequest request = SinkRequest.builder().jobId(42L).sinkType("demo-sink")
                .columns(Collections.singletonList("c1")).build();
        assertEquals(Long.valueOf(42L), request.getJobId());
        assertEquals(Collections.singletonList("c1"), request.getColumns());
    }
}
