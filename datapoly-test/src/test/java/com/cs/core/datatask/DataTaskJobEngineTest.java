// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.datatask.ColumnMetadata;
import com.cs.common.datatask.DataTaskSink;
import com.cs.common.datatask.SinkOutcome;
import com.cs.common.datatask.SinkRequest;
import com.cs.common.datatask.SinkSession;
import com.cs.common.enums.DataTaskStatus;
import com.cs.common.enums.NamingStrategyEnum;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.entity.DataTaskDefEntity;
import com.cs.persistence.entity.DataTaskJobEntity;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

public class DataTaskJobEngineTest {

    // ------------------------------------------------------------------ fakes

    private static class StubSession implements SinkSession {
        final List<List<Object>> rows = new ArrayList<>();
        boolean completed;
        Throwable abortedWith;

        @Override
        public boolean writeRows(Iterable<List<Object>> batch) {
            for (List<Object> row : batch) {
                rows.add(row);
            }
            return true;
        }

        @Override
        public SinkOutcome complete() {
            completed = true;
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("provider", "stub");
            return SinkOutcome.builder().artifactUri("stub://artifact").info(info).build();
        }

        @Override
        public void close() {
        }

        @Override
        public void abort(Throwable cause) {
            abortedWith = cause;
        }
    }

    private static class StubSink implements DataTaskSink {
        final List<SinkRequest> requests = new ArrayList<>();
        final List<StubSession> sessions = new ArrayList<>();

        @Override
        public String type() {
            return "stub";
        }

        @Override
        public SinkSession openSession(SinkRequest request) {
            requests.add(request);
            StubSession session = new StubSession();
            sessions.add(session);
            return session;
        }
    }

    private static class RecordingJobDao extends com.cs.persistence.dao.DataTaskJobDao {
        DataTaskJobEntity current;
        Long successRows;
        String successUri;
        String successInfoJson;
        String failureMessage;

        @Override
        public DataTaskJobEntity getById(Long id) {
            return current;
        }

        @Override
        public boolean heartbeat(Long id, long totalRows, java.sql.Timestamp leaseExpireAt) {
            current.setTotalRows(totalRows);
            return true;
        }

        @Override
        public boolean finishSuccess(Long id, long totalRows, String artifactUri,
                                     String artifactInfo, java.sql.Timestamp finishTime) {
            this.successRows = totalRows;
            this.successUri = artifactUri;
            this.successInfoJson = artifactInfo;
            current.setStatus(DataTaskStatus.SUCCESS);
            return true;
        }

        @Override
        public boolean finishFailure(Long id, String errorMessage, long totalRows,
                                     java.sql.Timestamp finishTime) {
            this.failureMessage = errorMessage;
            current.setStatus(DataTaskStatus.FAILED);
            return true;
        }
    }

    private static class RecordingDataSourceDao extends DataSourceDao {
        @Override
        public DataSourceEntity getById(Long id) {
            return DataSourceEntity.builder()
                    .id(id)
                    .type(ProductTypeEnum.MYSQL)
                    .version("8.0")
                    .build();
        }
    }

    private interface ChannelScript {
        void drive(DataTaskJobEngine.ResultChannel channel) throws Exception;
    }

    private static class EventCollector implements ApplicationEventPublisher {
        final List<DataTaskEvent> events = new ArrayList<>();

        // DataTaskEvent extends ApplicationEvent, so static dispatch lands on this overload
        @Override
        public void publishEvent(ApplicationEvent event) {
            collect(event);
        }

        @Override
        public void publishEvent(Object event) {
            collect(event);
        }

        private void collect(Object event) {
            if (event instanceof DataTaskEvent) {
                events.add((DataTaskEvent) event);
            }
        }
    }

    // ------------------------------------------------------------------ state

    private int cannedRows;
    private boolean cannedTruncated;
    private List<DataTaskEvent> events = new ArrayList<>();

    // ------------------------------------------------------------------ helpers

    private DataTaskDefEntity definition() {
        Map<String, String> alias = new LinkedHashMap<>();
        alias.put("raw_b", "B");
        DataTaskDefEntity def = DataTaskDefEntity.builder()
                .name("demo-task")
                .datasourceId(3L)
                .sqlText("SELECT raw_a, raw_b FROM demo WHERE x >= #{x}")
                .namingStrategy(NamingStrategyEnum.NONE)
                .columnAlias(alias)
                .columnOrder(Arrays.asList("raw_b", "raw_a"))
                .applyFormatToString(Boolean.FALSE)
                .dollarAllowed(Boolean.FALSE)
                .maxRows(100L)
                .sinkType("stub")
                .sinkConfig("{\"bucket\":\"reports\"}")
                .enabled(Boolean.TRUE)
                .build();
        def.setResponseFormat(Collections.emptyMap());
        return def;
    }

    private DataTaskJobEntity jobFor(DataTaskDefEntity def) {
        return DataTaskJobEntity.builder()
                .id(77L)
                .defId(9L)
                .defName(def.getName())
                .status(DataTaskStatus.RUNNING)
                .snapshot(DataTaskService.buildSnapshotJson(def))
                .cancelRequested(Boolean.FALSE)
                .totalRows(0L)
                .submittedBy("tester")
                .build();
    }

    /** Overridden streamQuery plays the JDBC layer role with canned batches/results. */
    private DataTaskJobEngine engine(RecordingJobDao jobDao, StubSink sink, final ChannelScript script) {
        DataTaskJobEngine engineInstance = new DataTaskJobEngine() {
            @Override
            protected HikariDataSource loadDataSource(DataSourceEntity dsEntity) {
                return new HikariDataSource();
            }

            @Override
            protected StreamResult streamQuery(StreamSpec spec, ResultChannel channel) throws Exception {
                script.drive(channel);
                return StreamResult.rows(cannedRows, cannedTruncated);
            }
        };
        DataTaskTestSupport.setField(engineInstance, "dataTaskJobDao", jobDao);
        DataTaskTestSupport.setField(engineInstance, "dataSourceDao", new RecordingDataSourceDao());
        DataTaskTestSupport.setField(engineInstance, "sinkRegistry", new DataTaskSinkRegistry(
                Collections.<DataTaskSink>singletonList(sink)));
        EventCollector collector = new EventCollector();
        DataTaskTestSupport.setField(engineInstance, "eventPublisher", collector);
        events = collector.events;
        return engineInstance;
    }

    // ------------------------------------------------------------------ cases

    @Test
    public void successRunsShapedDeliveryAndFinalizesRecord() throws Exception {
        final StubSink sink = new StubSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());

        cannedRows = 2;
        cannedTruncated = false;
        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) throws Exception {
                List<ColumnMetadata> metadata = new ArrayList<>();
                metadata.add(ColumnMetadata.builder().jdbcType(java.sql.Types.VARCHAR)
                        .className("java.lang.String").build());
                metadata.add(ColumnMetadata.builder().jdbcType(java.sql.Types.INTEGER)
                        .className("java.lang.Long").build());
                channel.start(new ArrayList<>(Arrays.asList("raw_a", "raw_b")), metadata);
                List<Object[]> first = new ArrayList<>();
                first.add(new Object[]{"a1", 10});
                channel.batch(first);
                List<Object[]> second = new ArrayList<>();
                second.add(new Object[]{"a2", 20});
                channel.batch(second);
            }
        });

        localEngine.run(77L);

        Assert.assertEquals(1, sink.requests.size());
        SinkRequest request = sink.requests.get(0);
        Assert.assertEquals("stub", request.getSinkType());
        Assert.assertEquals("{\"bucket\":\"reports\"}", request.getSinkConfig());
        Assert.assertEquals("tester", request.getSubmittedBy());
        // columns follow the declared order [raw_b, raw_a] with alias applied to raw_b
        Assert.assertEquals(Arrays.asList("B", "raw_a"), request.getColumns());
        // type metadata travels in parallel with the shaped columns
        Assert.assertEquals(Integer.valueOf(java.sql.Types.INTEGER),
                request.getColumnMetadata().get(0).getJdbcType());
        Assert.assertEquals("java.lang.Long", request.getColumnMetadata().get(0).getClassName());
        Assert.assertEquals(Integer.valueOf(java.sql.Types.VARCHAR),
                request.getColumnMetadata().get(1).getJdbcType());
        Assert.assertEquals("java.lang.String", request.getColumnMetadata().get(1).getClassName());

        StubSession session = sink.sessions.get(0);
        Assert.assertTrue(session.completed);
        Assert.assertNull(session.abortedWith);
        Assert.assertEquals(Arrays.asList(
                        Arrays.asList((Object) 10, "a1"),
                        Arrays.asList((Object) 20, "a2")),
                session.rows);

        Assert.assertEquals(Long.valueOf(2), jobDao.successRows);
        Assert.assertEquals("stub://artifact", jobDao.successUri);
        Assert.assertNotNull(jobDao.successInfoJson);
        Assert.assertTrue(jobDao.successInfoJson.contains("\"truncated\":false"));
        Assert.assertTrue(jobDao.successInfoJson.contains("\"provider\":\"stub\""));

        Assert.assertEquals(1, events.size());
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
        Assert.assertEquals(2L, events.get(0).getTotalRows());
        Assert.assertEquals("stub://artifact", events.get(0).getArtifactUri());
        Assert.assertEquals("stub", events.get(0).getSinkType());
    }

    @Test
    public void failureBeforeStartMarksFailedAndPublishesEvent() throws Exception {
        final StubSink sink = new StubSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());

        cannedRows = 0;
        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) {
                throw new RuntimeException("boom");
            }
        });

        localEngine.run(77L);

        Assert.assertTrue(sink.sessions.isEmpty());
        Assert.assertTrue(String.valueOf(jobDao.failureMessage).contains("boom"));
        Assert.assertNull(jobDao.successUri);
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(DataTaskStatus.FAILED, events.get(0).getStatus());
    }

    @Test
    public void cancellationMapsToCanceledTerminalState() throws Exception {
        final StubSink sink = new StubSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());
        jobDao.current.setCancelRequested(Boolean.TRUE); // cooperatively flagged beforehand

        cannedRows = 1;
        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) throws Exception {
                channel.start(new ArrayList<>(Arrays.asList("raw_a", "raw_b")), Collections.<ColumnMetadata>emptyList());
                throw new DataTaskJobEngine.CancelledException();
            }
        });

        localEngine.run(77L);

        // a session was opened before the cancellation hit and got aborted
        Assert.assertEquals(1, sink.sessions.size());
        Assert.assertNotNull(sink.sessions.get(0).abortedWith);

        Assert.assertTrue(String.valueOf(jobDao.failureMessage).toLowerCase().contains("cancel"));
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(DataTaskStatus.CANCELED, events.get(0).getStatus());
    }

    @Test
    public void truncationFlagIsStoredOnTheArtifactInfo() throws Exception {
        final StubSink sink = new StubSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());

        cannedRows = 5;
        cannedTruncated = true;
        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) throws Exception {
                channel.start(new ArrayList<>(Arrays.asList("raw_a", "raw_b")), Collections.<ColumnMetadata>emptyList());
                List<Object[]> all = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    all.add(new Object[]{"v" + i, i});
                }
                channel.batch(all);
            }
        });

        localEngine.run(77L);

        Assert.assertEquals(Long.valueOf(5), jobDao.successRows);
        Assert.assertNotNull(jobDao.successInfoJson);
        Assert.assertTrue(jobDao.successInfoJson.contains("\"truncated\":true"));
    }
}
