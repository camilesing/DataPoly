// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.datatask.CellDecorator;
import com.cs.common.datatask.ColumnMetadata;
import com.cs.common.dto.ItemParam;
import com.cs.common.datatask.DataTaskSink;
import com.cs.common.datatask.DataTaskStatementRequest;
import com.cs.common.datatask.DataTaskStatementSink;
import com.cs.common.datatask.SinkOutcome;
import com.cs.common.datatask.SinkRequest;
import com.cs.common.datatask.SinkSession;
import com.cs.common.enums.DataTaskStatus;
import com.cs.common.enums.NamingStrategyEnum;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.driver.DriverLoadService;
import com.cs.core.util.DataSourceUtils;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.entity.DataTaskDefEntity;
import com.cs.persistence.entity.DataTaskJobEntity;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.Callable;

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

    /** Same registration type as {@link StubSink}: one sink, two delivery modes. */
    private static class StubStatementSink extends StubSink implements DataTaskStatementSink {
        final List<DataTaskStatementRequest> consulted = new ArrayList<>();
        final List<DataTaskStatementRequest> executed = new ArrayList<>();
        boolean handles = true;
        boolean inlineParameters;
        boolean declineInlined;
        boolean throwOnExecute;
        boolean cancelledAtSubmission;
        boolean nullOutcome;
        boolean outcomeWithoutInfo;
        Runnable duringExecution;

        @Override
        public boolean handlesStatement(DataTaskStatementRequest request) {
            consulted.add(request);
            if (!handles || !request.isQuery()) {
                // mirrors the real contract: only queries can be wrapped by a server-side export
                return false;
            }
            // a sink that only accepts a statement it can run as-is declines the rewritten one
            return !(declineInlined && request.getSqlParameters().isEmpty());
        }

        @Override
        public boolean requiresInlinedParameters() {
            return inlineParameters;
        }

        @Override
        public SinkOutcome executeStatement(DataTaskStatementRequest request) throws Exception {
            executed.add(request);
            cancelledAtSubmission = request.getCancelled().getAsBoolean();
            if (cancelledAtSubmission) {
                // mirrors a well-behaved server-side sink: never submit once the job asks out
                throw new IllegalStateException("cancelled before submission");
            }
            if (null != duringExecution) {
                duringExecution.run();
            }
            if (throwOnExecute) {
                throw new IllegalStateException("unload rejected by MaxCompute");
            }
            if (nullOutcome) {
                return null;
            }
            if (outcomeWithoutInfo) {
                return SinkOutcome.builder().artifactUri(ARTIFACT_LOCATION).build();
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("location", ARTIFACT_LOCATION);
            return SinkOutcome.builder().artifactUri(ARTIFACT_LOCATION).info(info).build();
        }
    }

    private static class RecordingJobDao extends com.cs.persistence.dao.DataTaskJobDao {
        DataTaskJobEntity current;
        Long successRows;
        String successUri;
        String successInfoJson;
        String failureMessage;
        int heartbeats;
        List<Long> claimPendingResult = Collections.emptyList();
        int reapExpiredResult;
        String reapedMessage;
        /** Simulates the row vanishing between the pickup read and the RUNNING check. */
        boolean getByIdVanishesAfterFirst;
        private int getByIdCalls;
        /** Optional per-call script of Boolean / RuntimeException results for the lease keeper. */
        final java.util.Queue<Object> heartbeatScript = new java.util.ArrayDeque<>();

        @Override
        public DataTaskJobEntity getById(Long id) {
            if (getByIdVanishesAfterFirst && ++getByIdCalls > 1) {
                return null;
            }
            return current;
        }

        @Override
        public boolean heartbeat(Long id, long totalRows, java.sql.Timestamp leaseExpireAt) {
            heartbeats++;
            current.setTotalRows(totalRows);
            if (!heartbeatScript.isEmpty()) {
                Object next = heartbeatScript.poll();
                if (next instanceof RuntimeException) {
                    throw (RuntimeException) next;
                }
                return (Boolean) next;
            }
            return true;
        }

        @Override
        public List<Long> claimPending(int limit, String workerAddr, java.sql.Timestamp startTime,
                                        java.sql.Timestamp leaseExpireAt) {
            return claimPendingResult;
        }

        @Override
        public int reapExpired(String message) {
            reapedMessage = message;
            return reapExpiredResult;
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
            if (null != current) {
                current.setStatus(DataTaskStatus.FAILED);
            }
            return true;
        }

        @Override
        public boolean finishCanceled(Long id, String errorMessage, long totalRows,
                                      java.sql.Timestamp finishTime) {
            this.failureMessage = errorMessage;
            if (null != current) {
                current.setStatus(DataTaskStatus.CANCELED);
            }
            return true;
        }
    }

    private static class RecordingDataSourceDao extends DataSourceDao {
        boolean missing;

        @Override
        public DataSourceEntity getById(Long id) {
            if (missing) {
                return null;
            }
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

    private static final String ARTIFACT_LOCATION = "oss://oss-cn-hangzhou-internal.aliyuncs.com/reports/orders/";

    private int cannedRows;
    private boolean cannedTruncated;
    private boolean cannedRowset = true;
    private int cannedUpdate;
    private boolean rowPipelineRan;
    private RecordingDataSourceDao dataSourceDao = new RecordingDataSourceDao();
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
    private DataTaskJobEngine engine(RecordingJobDao jobDao, DataTaskSink sink, final ChannelScript script) {
        DataTaskJobEngine engineInstance = new DataTaskJobEngine() {
            @Override
            protected HikariDataSource loadDataSource(DataSourceEntity dsEntity) {
                return new HikariDataSource();
            }

            @Override
            protected StreamResult streamQuery(StreamSpec spec, ResultChannel channel) throws Exception {
                rowPipelineRan = true;
                script.drive(channel);
                return cannedRowset ? StreamResult.rows(cannedRows, cannedTruncated)
                        : StreamResult.update(cannedUpdate);
            }
        };
        DataTaskTestSupport.setField(engineInstance, "dataTaskJobDao", jobDao);
        DataTaskTestSupport.setField(engineInstance, "dataSourceDao", dataSourceDao);
        DataTaskTestSupport.setField(engineInstance, "sinkRegistry", new DataTaskSinkRegistry(
                Collections.<DataTaskSink>singletonList(sink)));
        EventCollector collector = new EventCollector();
        DataTaskTestSupport.setField(engineInstance, "eventPublisher", collector);
        events = collector.events;
        return engineInstance;
    }

    /** Definition authored for server-side delivery: one query, ${} inlined parameters. */
    private DataTaskDefEntity statementDefinition() {
        DataTaskDefEntity def = DataTaskDefEntity.builder()
                .name("odps-export")
                .datasourceId(3L)
                .sqlText("SELECT shop_name FROM sale_detail WHERE sale_date = '${sale_date}'")
                .namingStrategy(NamingStrategyEnum.NONE)
                .dollarAllowed(Boolean.TRUE)
                .maxRows(100L)
                .sinkType("stub")
                .sinkConfig("{\"format\":\"PARQUET\"}")
                .enabled(Boolean.TRUE)
                .build();
        def.setResponseFormat(Collections.emptyMap());
        return def;
    }

    private DataTaskJobEntity statementJob(DataTaskDefEntity def) {
        DataTaskJobEntity job = jobFor(def);
        job.setParamsJson("{\"sale_date\":\"2013\"}");
        return job;
    }

    /** Definition in the parameterized style: one query, one #{} bind value. */
    private DataTaskJobEntity boundStatementJob() {
        DataTaskJobEntity job = jobFor(definition());
        job.setParamsJson("{\"x\":10}");
        return job;
    }

    private static ChannelScript mustNotStream() {
        return new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) {
                Assert.fail("the row pipeline must not run for a delegated statement");
            }
        };
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
        // the record must tell the same story as the event (the state machine: RUNNING → CANCELED)
        Assert.assertEquals(DataTaskStatus.CANCELED, jobDao.current.getStatus());
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

    // -------------------------------------------------- delegated (statement) delivery

    @Test
    public void delegatedStatementSkipsTheRowPipelineAndRecordsItsOutcome() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = statementJob(statementDefinition());

        DataTaskJobEngine localEngine = engine(jobDao, sink, mustNotStream());

        localEngine.run(77L);

        Assert.assertFalse(rowPipelineRan);
        Assert.assertTrue(sink.sessions.isEmpty());
        Assert.assertEquals(1, sink.executed.size());
        DataTaskStatementRequest request = sink.executed.get(0);
        Assert.assertEquals(Long.valueOf(77L), request.getJobId());
        Assert.assertEquals("odps-export", request.getTaskName());
        Assert.assertEquals("stub", request.getSinkType());
        Assert.assertEquals("{\"format\":\"PARQUET\"}", request.getSinkConfig());
        // ${} inlining leaves a complete statement: no placeholders for the engine to bind
        Assert.assertEquals("SELECT shop_name FROM sale_detail WHERE sale_date = '2013'", request.getSql());
        Assert.assertTrue(request.getSqlParameters().isEmpty());
        Assert.assertEquals("2013", request.getParams().get("sale_date"));
        Assert.assertEquals(Long.valueOf(3L), request.getDatasourceId());
        Assert.assertEquals(ProductTypeEnum.MYSQL, request.getProduct());
        Assert.assertNotNull(request.getDataSource());
        Assert.assertEquals("tester", request.getSubmittedBy());
        Assert.assertTrue("a SELECT is a query the sink may export server-side", request.isQuery());
        // the cancel probe is read while the job is still RUNNING
        Assert.assertFalse(sink.cancelledAtSubmission);

        Assert.assertEquals(Long.valueOf(0L), jobDao.successRows);
        Assert.assertEquals(ARTIFACT_LOCATION, jobDao.successUri);
        Assert.assertTrue(jobDao.successInfoJson.contains("\"statementDelegated\":true"));
        Assert.assertTrue(jobDao.successInfoJson.contains("\"sinkType\":\"stub\""));

        Assert.assertEquals(1, events.size());
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
        Assert.assertEquals(ARTIFACT_LOCATION, events.get(0).getArtifactUri());
    }

    @Test
    public void statementSinkDecliningKeepsTheDefinitionOnTheRowPipeline() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        sink.handles = false;
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = statementJob(statementDefinition());

        cannedRows = 1;
        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) throws Exception {
                channel.start(new ArrayList<>(Arrays.asList("shop_name")),
                        Collections.<ColumnMetadata>emptyList());
                List<Object[]> batch = new ArrayList<>();
                batch.add(new Object[]{"s1"});
                channel.batch(batch);
            }
        });

        localEngine.run(77L);

        Assert.assertTrue(rowPipelineRan);
        Assert.assertTrue(sink.executed.isEmpty());
        Assert.assertEquals(1, sink.sessions.size());
        Assert.assertTrue(sink.sessions.get(0).completed);
        Assert.assertEquals(Long.valueOf(1L), jobDao.successRows);
    }

    @Test
    public void declaredInliningHandsTheStatementOverWithLiterals() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        sink.inlineParameters = true;
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = boundStatementJob();

        DataTaskJobEngine localEngine = engine(jobDao, sink, mustNotStream());

        localEngine.run(77L);

        Assert.assertFalse(rowPipelineRan);
        Assert.assertEquals(1, sink.executed.size());
        DataTaskStatementRequest request = sink.executed.get(0);
        // the #{} value arrives as a literal and nothing is left for the sink to bind
        Assert.assertEquals("SELECT raw_a, raw_b FROM demo WHERE x >= 10", request.getSql());
        Assert.assertTrue(request.getSqlParameters().isEmpty());
        Assert.assertTrue(request.isQuery());
        // the sink judged the statement it would actually execute, not just the bound rendering
        Assert.assertEquals(2, sink.consulted.size());
        Assert.assertEquals("SELECT raw_a, raw_b FROM demo WHERE x >= ?", sink.consulted.get(0).getSql());
        Assert.assertTrue(jobDao.successInfoJson.contains("\"parameterInlining\":true"));
        Assert.assertTrue(jobDao.successInfoJson.contains("\"statementDelegated\":true"));
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void sinksThatDoNotDeclareInliningKeepTheirBoundRendering() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = boundStatementJob();

        DataTaskJobEngine localEngine = engine(jobDao, sink, mustNotStream());

        localEngine.run(77L);

        DataTaskStatementRequest request = sink.executed.get(0);
        Assert.assertEquals("SELECT raw_a, raw_b FROM demo WHERE x >= ?", request.getSql());
        Assert.assertEquals(1, request.getSqlParameters().size());
        Assert.assertEquals(1, sink.consulted.size());
        Assert.assertFalse(jobDao.successInfoJson.contains("parameterInlining"));
    }

    @Test
    public void decliningTheRewrittenStatementFallsBackToTheRowPipeline() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        sink.inlineParameters = true;
        sink.declineInlined = true;
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = boundStatementJob();

        cannedRows = 1;
        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) throws Exception {
                channel.start(new ArrayList<>(Arrays.asList("raw_a", "raw_b")),
                        Collections.<ColumnMetadata>emptyList());
                List<Object[]> batch = new ArrayList<>();
                batch.add(new Object[]{"a1", 10});
                channel.batch(batch);
            }
        });

        localEngine.run(77L);

        // the row pipeline still needs the bound rendering, so the rewrite is discarded entirely
        Assert.assertTrue(rowPipelineRan);
        Assert.assertTrue(sink.executed.isEmpty());
        Assert.assertEquals(2, sink.consulted.size());
        Assert.assertEquals(Long.valueOf(1L), jobDao.successRows);
        Assert.assertFalse(jobDao.successInfoJson.contains("parameterInlining"));
    }

    @Test
    public void statementsThatCannotBeInlinedFailWithTheReasonOnTheRecord() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        sink.inlineParameters = true;
        final RecordingJobDao jobDao = new RecordingJobDao();
        DataTaskJobEntity job = jobFor(definition());
        job.setParamsJson("{\"x\":[1,2]}"); // an array bound as one placeholder has no literal form
        jobDao.current = job;

        DataTaskJobEngine localEngine = engine(jobDao, sink, mustNotStream());

        localEngine.run(77L);

        Assert.assertTrue(String.valueOf(jobDao.failureMessage), jobDao.failureMessage.contains("x"));
        Assert.assertTrue(sink.executed.isEmpty());
        Assert.assertEquals(DataTaskStatus.FAILED, events.get(0).getStatus());
    }

    @Test
    public void nonQueryDefinitionsStayOnThePathTheyHadBefore() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        DataTaskDefEntity def = statementDefinition();
        // DML/DDL cannot be wrapped by a server-side export command
        def.setSqlText("DELETE FROM sale_detail WHERE sale_date = '${sale_date}'");
        jobDao.current = statementJob(def);

        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) throws Exception {
                channel.start(new ArrayList<>(Arrays.asList("cnt")),
                        Collections.<ColumnMetadata>emptyList());
            }
        });

        localEngine.run(77L);

        Assert.assertTrue(rowPipelineRan);
        Assert.assertTrue(sink.executed.isEmpty());
        Assert.assertEquals("stub://artifact", jobDao.successUri);
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void delegatedStatementFailureMarksTheJobFailed() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        sink.throwOnExecute = true;
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = statementJob(statementDefinition());

        DataTaskJobEngine localEngine = engine(jobDao, sink, mustNotStream());

        localEngine.run(77L);

        Assert.assertTrue(String.valueOf(jobDao.failureMessage).contains("unload rejected"));
        Assert.assertNull(jobDao.successUri);
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(DataTaskStatus.FAILED, events.get(0).getStatus());
    }

    @Test
    public void cancelRequestedBeforeSubmissionMarksTheDelegatedJobCanceled() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = statementJob(statementDefinition());
        jobDao.current.setCancelRequested(Boolean.TRUE); // cooperatively flagged beforehand

        DataTaskJobEngine localEngine = engine(jobDao, sink, mustNotStream());

        localEngine.run(77L);

        Assert.assertTrue(sink.cancelledAtSubmission);
        Assert.assertNull(jobDao.successUri);
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(DataTaskStatus.CANCELED, events.get(0).getStatus());
    }

    @Test
    public void lateCancelKeepsTheArtifactAndIsRecordedOnIt() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = statementJob(statementDefinition());
        // the export is already accepted by the source engine when the cancel arrives
        sink.duringExecution = new Runnable() {
            @Override
            public void run() {
                jobDao.current.setCancelRequested(Boolean.TRUE);
            }
        };

        DataTaskJobEngine localEngine = engine(jobDao, sink, mustNotStream());

        localEngine.run(77L);

        Assert.assertEquals(ARTIFACT_LOCATION, jobDao.successUri);
        Assert.assertTrue(jobDao.successInfoJson.contains("\"cancelRequested\":true"));
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void delegatedStatementKeepsItsLeaseAliveWhileItBlocks() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        sink.duringExecution = new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = statementJob(statementDefinition());

        DataTaskJobEngine localEngine = engine(jobDao, sink, mustNotStream());

        localEngine.run(77L);

        // without the keeper the lease would expire mid-export and the reaper would fail the job
        Assert.assertTrue("expected periodic lease refreshes, saw " + jobDao.heartbeats,
                jobDao.heartbeats >= 2);
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void leaseKeeperToleratesLostAndBrokenHeartbeats() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        sink.duringExecution = new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2600L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = statementJob(statementDefinition());
        jobDao.heartbeatScript.add(Boolean.FALSE); // job row moved on: refresh refused, only logged
        jobDao.heartbeatScript.add(new RuntimeException("db gone")); // dao outage: only logged

        DataTaskJobEngine localEngine = engine(jobDao, sink, mustNotStream());

        localEngine.run(77L);

        Assert.assertTrue("the delegated statement must survive refused/broken heartbeats",
                jobDao.heartbeats >= 2);
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
        // shutdown is idempotent and safe once the keeper exists
        localEngine.shutdownLeaseKeeper();
        localEngine.shutdownLeaseKeeper();
    }

    @Test
    public void shutdownLeaseKeeperIsSafeWithoutAKeeper() {
        DataTaskJobEngine localEngine = engine(new RecordingJobDao(), new StubSink(), mustNotStream());
        localEngine.shutdownLeaseKeeper(); // leaseKeeper still null: nothing to stop
    }

    // ------------------------------------------------------------- claiming / reaping

    @Test
    public void claimNextReturnsTheClaimedIdOrNull() {
        RecordingJobDao jobDao = new RecordingJobDao();
        DataTaskJobEngine localEngine = engine(jobDao, new StubSink(), mustNotStream());

        jobDao.claimPendingResult = Collections.emptyList();
        Assert.assertNull(localEngine.claimNext("worker-1"));

        jobDao.claimPendingResult = Collections.singletonList(5L);
        Assert.assertEquals(Long.valueOf(5L), localEngine.claimNext("worker-1"));
    }

    @Test
    public void reapLostDelegatesToTheDao() {
        RecordingJobDao jobDao = new RecordingJobDao();
        DataTaskJobEngine localEngine = engine(jobDao, new StubSink(), mustNotStream());

        jobDao.reapExpiredResult = 0;
        localEngine.reapLost("worker died");
        Assert.assertEquals("worker died", jobDao.reapedMessage);

        jobDao.reapExpiredResult = 3;
        localEngine.reapLost("worker died");
        Assert.assertEquals("worker died", jobDao.reapedMessage);
    }

    // ------------------------------------------------------------- run() early exits and failures

    @Test
    public void runIgnoresJobsThatDisappeared() {
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = null; // the row vanished between claim and pickup

        engine(jobDao, new StubSink(), mustNotStream()).run(77L);

        Assert.assertTrue(events.isEmpty());
        Assert.assertNull(jobDao.failureMessage);
        Assert.assertNull(jobDao.successRows);
    }

    @Test
    public void runIgnoresJobsNoLongerRunningAtPickup() {
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());
        jobDao.current.setStatus(DataTaskStatus.CANCELED); // canceled/reaped between claim and pickup

        engine(jobDao, new StubSink(), mustNotStream()).run(77L);

        Assert.assertTrue(events.isEmpty());
        Assert.assertNull(jobDao.failureMessage);
    }

    @Test
    public void runFailsWhenTheDatasourceVanished() {
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());
        dataSourceDao.missing = true;

        engine(jobDao, new StubSink(), mustNotStream()).run(77L);

        Assert.assertNotNull(jobDao.failureMessage);
        Assert.assertNull(jobDao.successUri);
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(DataTaskStatus.FAILED, events.get(0).getStatus());
    }

    @Test
    public void runFailsForUnregisteredSinkType() {
        final RecordingJobDao jobDao = new RecordingJobDao();
        DataTaskDefEntity def = definition();
        def.setSinkType("no-such-sink");
        jobDao.current = jobFor(def);

        engine(jobDao, new StubSink(), mustNotStream()).run(77L);

        Assert.assertNotNull(jobDao.failureMessage);
        Assert.assertNull(jobDao.successUri);
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(DataTaskStatus.FAILED, events.get(0).getStatus());
        Assert.assertEquals("no-such-sink", events.get(0).getSinkType());
    }

    @Test
    public void runFailsOnUnparsableSnapshot() {
        final RecordingJobDao jobDao = new RecordingJobDao();
        DataTaskJobEntity job = jobFor(definition());
        job.setSnapshot("null"); // parses to a null definition
        jobDao.current = job;

        engine(jobDao, new StubSink(), mustNotStream()).run(77L);

        Assert.assertNotNull(jobDao.failureMessage);
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(DataTaskStatus.FAILED, events.get(0).getStatus());
    }

    @Test
    public void updateCountResultsRecordTheCountInsteadOfADelivery() {
        final StubSink sink = new StubSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());

        cannedRowset = false;
        cannedUpdate = 3;
        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) throws Exception {
                channel.start(new ArrayList<>(Arrays.asList("updated")),
                        Collections.<ColumnMetadata>emptyList());
            }
        });

        localEngine.run(77L);

        // a session was opened (start ran) but never completed: nothing was delivered
        Assert.assertEquals(1, sink.sessions.size());
        Assert.assertFalse(sink.sessions.get(0).completed);
        Assert.assertEquals(Long.valueOf(0L), jobDao.successRows);
        Assert.assertNull(jobDao.successUri);
        Assert.assertTrue(String.valueOf(jobDao.successInfoJson),
                jobDao.successInfoJson.contains("\"updateCount\":3"));
        Assert.assertTrue(String.valueOf(jobDao.successInfoJson),
                jobDao.successInfoJson.contains("\"sinkStopped\":false"));
    }

    @Test
    public void failureMessagesAreCappedAndBlankOnesBecomeUnknown() {
        RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());
        char[] big = new char[2500];
        Arrays.fill(big, 'x');
        engine(jobDao, new StubSink(), boom(new String(big))).run(77L);
        Assert.assertEquals(2000, jobDao.failureMessage.length());

        RecordingJobDao blankDao = new RecordingJobDao();
        blankDao.current = jobFor(definition());
        engine(blankDao, new StubSink(), boom("   ")).run(77L);
        Assert.assertEquals("unknown error", blankDao.failureMessage);
    }

    private static ChannelScript boom(final String message) {
        return new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) {
                throw new RuntimeException(message);
            }
        };
    }

    @Test
    public void cellDecoratorsRunAfterProjectionBeforeDelivery() {
        final StubSink sink = new StubSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());

        cannedRows = 1;
        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) throws Exception {
                channel.start(new ArrayList<>(Arrays.asList("raw_a", "raw_b")),
                        Collections.<ColumnMetadata>emptyList());
                List<Object[]> batch = new ArrayList<>();
                batch.add(new Object[]{"secret", 10});
                channel.batch(batch);
            }
        });
        localEngine.setCellDecorators(Collections.<CellDecorator>singletonList(new CellDecorator() {
            @Override
            public Object decorate(String column, int columnIndex, Object value) {
                return "secret".equals(value) ? "masked" : value;
            }
        }));

        localEngine.run(77L);

        // shaped order is [B, raw_a]; the decorator replaced the sensitive cell in place
        Assert.assertEquals(Arrays.asList(
                Arrays.asList((Object) 10, "masked")), sink.sessions.get(0).rows);
    }

    @Test
    public void eventPublishingFailureNeverFailsTheJob() {
        final StubSink sink = new StubSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());

        cannedRows = 0;
        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) throws Exception {
                channel.start(new ArrayList<>(Arrays.asList("raw_a", "raw_b")),
                        Collections.<ColumnMetadata>emptyList());
            }
        });
        DataTaskTestSupport.setField(localEngine, "eventPublisher", new ApplicationEventPublisher() {
            @Override
            public void publishEvent(ApplicationEvent event) {
                throw new IllegalStateException("bus down");
            }

            @Override
            public void publishEvent(Object event) {
                throw new IllegalStateException("bus down");
            }
        });

        localEngine.run(77L);

        Assert.assertEquals(DataTaskStatus.SUCCESS, jobDao.current.getStatus());
        Assert.assertNotNull(jobDao.successRows);
    }

    // ------------------------------------------------------------- snapshot normalization

    @Test
    public void normalizeLeavesParamsWithoutChildrenUntouched() {
        Assert.assertNull(DataTaskJobEngine.normalize(DataTaskDefEntity.builder().build()).getParams());

        ItemParam noChildren = new ItemParam();
        noChildren.setName("a");
        noChildren.setChildren(null);
        ItemParam emptyChildren = new ItemParam();
        emptyChildren.setName("b");
        emptyChildren.setChildren(Collections.<com.cs.common.dto.BaseParam>emptyList());
        DataTaskDefEntity entity = DataTaskDefEntity.builder()
                .params(new ArrayList<ItemParam>(Arrays.asList(noChildren, emptyChildren)))
                .build();

        Assert.assertSame(entity, DataTaskJobEngine.normalize(entity));
    }

    /**
     * A child that is both a BaseParam and a Map reaches the restoration branch: the loop's
     * element type is {@code BaseParam}, so a plain LinkedHashMap cannot travel through it,
     * while a Map-flavored subclass can and still exercises the "erased to a map" path.
     */
    private static class ErasedChild extends com.cs.common.dto.BaseParam implements Map<String, Object> {
        private final Map<String, Object> values = new LinkedHashMap<>();

        ErasedChild() {
            setName("restored");
            setRequired(Boolean.TRUE);
            values.put("name", "restored");
            values.put("required", Boolean.TRUE);
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public boolean isEmpty() {
            return values.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return values.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return values.containsValue(value);
        }

        @Override
        public Object get(Object key) {
            return values.get(key);
        }

        @Override
        public Object put(String key, Object value) {
            return values.put(key, value);
        }

        @Override
        public Object remove(Object key) {
            return values.remove(key);
        }

        @Override
        public void putAll(Map<? extends String, ?> m) {
            values.putAll(m);
        }

        @Override
        public void clear() {
            values.clear();
        }

        @Override
        public Set<String> keySet() {
            return values.keySet();
        }

        @Override
        public Collection<Object> values() {
            return values.values();
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return values.entrySet();
        }
    }

    @Test
    public void normalizeRestoresChildrenErasedToMaps() {
        DataTaskDefEntity entity = DataTaskDefEntity.builder().build();
        ItemParam parent = new ItemParam();
        parent.setName("p");
        List<com.cs.common.dto.BaseParam> children = new ArrayList<>();
        com.cs.common.dto.BaseParam real = new com.cs.common.dto.BaseParam();
        real.setName("kept");
        children.add(real);
        children.add(new ErasedChild());
        parent.setChildren(children);
        entity.setParams(new ArrayList<ItemParam>(Collections.singletonList(parent)));

        DataTaskDefEntity normalized = DataTaskJobEngine.normalize(entity);

        List<com.cs.common.dto.BaseParam> fixed = normalized.getParams().get(0).getChildren();
        Assert.assertEquals(2, fixed.size());
        Assert.assertSame("concrete children are kept as-is", real, fixed.get(0));
        Assert.assertEquals("restored", fixed.get(1).getName());
        Assert.assertEquals(Boolean.TRUE, fixed.get(1).getRequired());
        Assert.assertNotSame("the map-flavored child is copied to a plain BaseParam",
                ErasedChild.class, fixed.get(1).getClass());
    }

    // ------------------------------------------------------------- debugPreview over real H2

    private static String h2DriverDir;

    private static synchronized String h2DriverDir() throws Exception {
        if (null == h2DriverDir) {
            java.net.URL jar = org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation();
            Assert.assertNotNull("h2 jar not resolvable on the test classpath", jar);
            java.nio.file.Path dir = Files.createTempDirectory("dt-h2-driver");
            Files.copy(Paths.get(jar.toURI()), dir.resolve("h2.jar"));
            h2DriverDir = dir.toString();
        }
        return h2DriverDir;
    }

    private static class FixedDriverLoadService extends DriverLoadService {
        @Override
        public File getVersionDriverFile(ProductTypeEnum dbTypeEnum, String driverVersion) {
            try {
                return new File(h2DriverDir());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static String h2Url(String db) {
        return "jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1";
    }

    /**
     * Initializes schema through the same driver classloader the pools use: H2's in-memory
     * database registry is per-classloader, so a plain DriverManager connection from the test
     * classpath would create a database the JarFileClassLoader-loaded driver cannot see.
     */
    private static void h2Exec(DataSourceEntity entity, String... statements) throws Exception {
        HikariDataSource pool = DataSourceUtils.createDataSource(entity, h2DriverDir());
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } finally {
            pool.close();
        }
    }

    private static void h2InsertRows(DataSourceEntity entity, int rows) throws Exception {
        HikariDataSource pool = DataSourceUtils.createDataSource(entity, h2DriverDir());
        try (Connection connection = pool.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO t_batch (n, raw_a, raw_b) VALUES (?, ?, ?)")) {
            for (int i = 1; i <= rows; i++) {
                insert.setLong(1, i);
                insert.setString(2, "v" + i);
                insert.setLong(3, i);
                insert.addBatch();
            }
            insert.executeBatch();
        } finally {
            pool.close();
        }
    }

    private static DataSourceEntity h2Entity(long id, String db) {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setId(id);
        entity.setType(ProductTypeEnum.MYSQL);
        entity.setVersion("8.0");
        entity.setName("h2-" + db);
        entity.setUrl(h2Url(db));
        entity.setDriver("org.h2.Driver");
        entity.setUsername("sa");
        entity.setPassword("");
        return entity;
    }

    private final List<Long> previewPoolIds = new ArrayList<>();
    private final List<HikariDataSource> openPools = new ArrayList<>();

    /**
     * Installs a stub beanFactory into hutool SpringUtil so DataSourceUtils can resolve its
     * environment switches without a container (same pattern as DataSourceUtilsTest); the
     * datasource encryption switch answers "false", so plain-text H2 credentials pass through.
     */
    @Before
    public void installStubEnvironment() throws Exception {
        final Object environment = Proxy.newProxyInstance(DataTaskJobEngineTest.class.getClassLoader(),
                new Class<?>[]{Environment.class}, (p, m, a) -> {
                    if ("getProperty".equals(m.getName()) && a != null && a.length >= 1) {
                        if ("datapoly.datasource.encrypt".equals(a[0])) {
                            return "false";
                        }
                        return a.length > 1 ? a[a.length - 1] : null;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
        Object beanFactory = Proxy.newProxyInstance(DataTaskJobEngineTest.class.getClassLoader(),
                new Class<?>[]{ConfigurableListableBeanFactory.class}, (p, m, a) -> {
                    if ("getBean".equals(m.getName()) && a != null && a.length == 1 && a[0] instanceof Class) {
                        return environment;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
        Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
    }

    @After
    public void tearDownH2AndEnvironment() throws Exception {
        Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
        for (Long id : previewPoolIds) {
            DataSourceUtils.dropHikariDataSource(id);
        }
        previewPoolIds.clear();
        for (HikariDataSource pool : openPools) {
            pool.close();
        }
        openPools.clear();
    }

    private DataTaskJobEngine previewEngine(final DataSourceEntity entity) {
        DataTaskJobEngine engineInstance = new DataTaskJobEngine();
        DataSourceDao dao = new DataSourceDao() {
            @Override
            public DataSourceEntity getById(Long id) {
                return entity;
            }
        };
        DataTaskTestSupport.setField(engineInstance, "dataSourceDao", dao);
        DataTaskTestSupport.setField(engineInstance, "driverLoadService", new FixedDriverLoadService());
        if (null != entity) {
            previewPoolIds.add(entity.getId());
        }
        return engineInstance;
    }

    private DataTaskDefEntity previewDefinition(long datasourceId, String sql) {
        Map<String, String> alias = new LinkedHashMap<>();
        alias.put("raw_b", "B");
        DataTaskDefEntity def = DataTaskDefEntity.builder()
                .name("preview-task")
                .datasourceId(datasourceId)
                .sqlText(sql)
                .namingStrategy(NamingStrategyEnum.NONE)
                .columnAlias(alias)
                .columnOrder(Arrays.asList("raw_b", "raw_a"))
                .applyFormatToString(Boolean.FALSE)
                .dollarAllowed(Boolean.FALSE)
                .sinkType("stub")
                .enabled(Boolean.TRUE)
                .build();
        def.setResponseFormat(Collections.emptyMap());
        return def;
    }

    @Test
    public void debugPreviewShapingTruncationAndBinding() throws Exception {
        String db = "dtPreviewShaping";
        DataSourceEntity entity = h2Entity(9001L, db);
        h2Exec(entity,
                "CREATE TABLE t_preview (\"raw_a\" VARCHAR(64), \"raw_b\" INT, \"n\" INT)",
                "INSERT INTO t_preview VALUES ('a1', 1, 1), ('a2', 2, 2), ('a3', 3, 3)");
        DataTaskDefEntity def = previewDefinition(9001L,
                "SELECT \"raw_a\", \"raw_b\" FROM t_preview WHERE \"n\" >= #{n} ORDER BY \"raw_b\"");

        Map<String, Object> params = new HashMap<>();
        params.put("n", 1);
        Map<String, Object> view = previewEngine(entity).debugPreview(def, params, 2);

        // declared order [raw_b, raw_a] with the alias applied; parameter bound through #{}
        Assert.assertEquals(Arrays.asList("B", "raw_a"), view.get("columns"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) view.get("rows");
        Assert.assertEquals(2, rows.size());
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("B", 1);
        expected.put("raw_a", "a1");
        Assert.assertEquals(expected, rows.get(0));
        Assert.assertEquals(2L, view.get("totalPreviewed"));
        Assert.assertNull(view.get("updateCount"));
        Assert.assertEquals(Boolean.TRUE, view.get("truncated")); // a third row existed past the cap
    }

    @Test
    public void debugPreviewCapReachedExactlyIsNotTruncated() throws Exception {
        String db = "dtPreviewExact";
        DataSourceEntity entity = h2Entity(9002L, db);
        h2Exec(entity,
                "CREATE TABLE t_preview (\"raw_a\" VARCHAR(64), \"raw_b\" INT)",
                "INSERT INTO t_preview VALUES ('a1', 1), ('a2', 2), ('a3', 3)");
        DataTaskDefEntity def = previewDefinition(9002L, "SELECT \"raw_a\", \"raw_b\" FROM t_preview");

        Map<String, Object> view = previewEngine(entity).debugPreview(
                def, Collections.emptyMap(), 3);

        Assert.assertEquals(3L, view.get("totalPreviewed"));
        Assert.assertEquals(Boolean.FALSE, view.get("truncated"));
        Assert.assertEquals(3, ((List<?>) view.get("rows")).size());
    }

    @Test
    public void debugPreviewOfAnUpdateReportsUpdateCount() throws Exception {
        String db = "dtPreviewUpdate";
        DataSourceEntity entity = h2Entity(9003L, db);
        h2Exec(entity,
                "CREATE TABLE t_preview (\"raw_a\" VARCHAR(64), \"raw_b\" INT)",
                "INSERT INTO t_preview VALUES ('a1', 1), ('a2', 2), ('a3', 3)");
        DataTaskDefEntity def = previewDefinition(9003L, "UPDATE t_preview SET \"raw_b\" = \"raw_b\"");

        Map<String, Object> view = previewEngine(entity).debugPreview(
                def, Collections.emptyMap(), 10);

        Assert.assertEquals(Integer.valueOf(3), view.get("updateCount"));
        Assert.assertEquals(0L, view.get("totalPreviewed"));
        Assert.assertTrue(((List<?>) view.get("rows")).isEmpty());
        Assert.assertTrue(((List<?>) view.get("columns")).isEmpty());
        Assert.assertEquals(Boolean.FALSE, view.get("truncated"));
    }

    @Test
    public void debugPreviewCamelCasesColumnsByDefault() throws Exception {
        String db = "dtPreviewCamel";
        DataSourceEntity entity = h2Entity(9004L, db);
        h2Exec(entity, "CREATE TABLE t_preview (unused INT)");
        DataTaskDefEntity def = previewDefinition(9004L, "SELECT 1 AS \"mixed_Case_x\"");
        def.setNamingStrategy(null); // falls back to CAMEL_CASE inside the preview spec
        // the projected column is neither ordered nor aliased: keep the natural column list
        def.setColumnOrder(Collections.<String>emptyList());

        Map<String, Object> view = previewEngine(entity).debugPreview(
                def, Collections.emptyMap(), 5);

        Assert.assertEquals(Collections.singletonList("mixedCaseX"), view.get("columns"));
        Assert.assertEquals(1L, view.get("totalPreviewed"));
    }

    @Test
    public void debugPreviewWithoutDatasourceFails() {
        DataTaskJobEngine localEngine = previewEngine(null);
        try {
            localEngine.debugPreview(previewDefinition(1L, "SELECT 1"), Collections.emptyMap(), 10);
            Assert.fail("expected CommonException");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        } catch (Exception e) {
            Assert.fail("unexpected " + e);
        }
    }

    // ------------------------------------------------------------- run() over real H2

    /** Row sink whose session script can refuse further rows or flip job state mid-write. */
    private static class ScriptedSink implements DataTaskSink {
        final List<SinkRequest> requests = new ArrayList<>();
        final List<List<Object>> received = new ArrayList<>();
        boolean proceedAfterWrite = true;
        boolean nullCompleteOutcome;
        volatile boolean failComplete;
        volatile boolean completeBeenCalled;
        boolean completed;
        Throwable abortedWith;
        Runnable onWrite;

        @Override
        public String type() {
            return "stub";
        }

        @Override
        public SinkSession openSession(SinkRequest request) {
            requests.add(request);
            return new SinkSession() {
                @Override
                public boolean writeRows(Iterable<List<Object>> batch) {
                    for (List<Object> row : batch) {
                        received.add(row);
                    }
                    if (null != onWrite) {
                        onWrite.run();
                    }
                    return proceedAfterWrite;
                }

                @Override
                public SinkOutcome complete() {
                    completeBeenCalled = true;
                    if (failComplete) {
                        throw new IllegalStateException("sink complete rejected the payload");
                    }
                    completed = true;
                    if (nullCompleteOutcome) {
                        return null;
                    }
                    return SinkOutcome.builder().artifactUri("stub://artifact").build();
                }

                @Override
                public void close() {
                }

                @Override
                public void abort(Throwable cause) {
                    abortedWith = cause;
                }
            };
        }
    }

    private DataTaskDefEntity h2Def(long datasourceId, String sql, Long maxRows) {
        DataTaskDefEntity def = DataTaskDefEntity.builder()
                .name("h2-task")
                .datasourceId(datasourceId)
                .sqlText(sql)
                .namingStrategy(NamingStrategyEnum.NONE)
                .columnAlias(Collections.emptyMap())
                .columnOrder(Collections.emptyList())
                .applyFormatToString(Boolean.FALSE)
                .dollarAllowed(Boolean.FALSE)
                .maxRows(maxRows)
                .sinkType("stub")
                .sinkConfig("{}")
                .enabled(Boolean.TRUE)
                .build();
        def.setResponseFormat(Collections.emptyMap());
        return def;
    }

    /** Engine whose datasource seam returns a real H2 pool built through DataSourceUtils. */
    private DataTaskJobEngine realSqlEngine(RecordingJobDao jobDao, DataTaskSink sink,
                                            final DataSourceEntity entity) {
        DataTaskJobEngine engineInstance = new DataTaskJobEngine() {
            @Override
            protected HikariDataSource loadDataSource(DataSourceEntity dsEntity) {
                try {
                    HikariDataSource pool = DataSourceUtils.createDataSource(entity, h2DriverDir());
                    openPools.add(pool);
                    return pool;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        DataTaskTestSupport.setField(engineInstance, "dataTaskJobDao", jobDao);
        DataTaskTestSupport.setField(engineInstance, "dataSourceDao", dataSourceDao);
        DataTaskTestSupport.setField(engineInstance, "sinkRegistry", new DataTaskSinkRegistry(
                Collections.<DataTaskSink>singletonList(sink)));
        EventCollector collector = new EventCollector();
        DataTaskTestSupport.setField(engineInstance, "eventPublisher", collector);
        events = collector.events;
        return engineInstance;
    }

    @Test
    public void runStreamsBatchesWithProgressAndHeartbeats() throws Exception {
        String db = "dtRunBatches";
        DataSourceEntity entity = h2Entity(9101L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 1001);

        DataTaskDefEntity def = h2Def(9101L, "SELECT n AS raw_a, n AS raw_b FROM t_batch WHERE n >= #{x}", 5000L);
        DataTaskJobEntity job = jobFor(def);
        job.setParamsJson("{\"x\":1}");
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = job;
        final ScriptedSink sink = new ScriptedSink();
        DataTaskJobEngine localEngine = realSqlEngine(jobDao, sink, entity);
        DataTaskTestSupport.setField(localEngine, "flushIntervalMs", 0L); // progress on every tick

        localEngine.run(77L);

        // 1001 rows = one full 1000-row batch + the trailing partial batch
        Assert.assertEquals(1001, sink.received.size());
        Assert.assertEquals(Long.valueOf(1001L), jobDao.successRows);
        Assert.assertTrue(String.valueOf(jobDao.successInfoJson),
                jobDao.successInfoJson.contains("\"deliveredRows\":1001"));
        Assert.assertTrue(String.valueOf(jobDao.successInfoJson),
                jobDao.successInfoJson.contains("\"truncated\":false"));
        Assert.assertTrue("progress ticks must refresh the job row", jobDao.heartbeats >= 1);
        Assert.assertTrue(sink.completed);
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void runStopsReadingWhenTheSinkDeclinesMoreRows() throws Exception {
        String db = "dtRunStop";
        DataSourceEntity entity = h2Entity(9102L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 3);

        final RecordingJobDao jobDao = new RecordingJobDao();
        // the per-definition cap makes the refusal land on the in-loop batch flush, not the trailing one
        jobDao.current = jobFor(h2Def(9102L, "SELECT n AS raw_a FROM t_batch", 2L));
        final ScriptedSink sink = new ScriptedSink();
        sink.proceedAfterWrite = false;

        realSqlEngine(jobDao, sink, entity).run(77L);

        Assert.assertEquals(2, sink.received.size());
        Assert.assertTrue(sink.completed);
        Assert.assertEquals(Long.valueOf(2L), jobDao.successRows);
        Assert.assertTrue(String.valueOf(jobDao.successInfoJson),
                jobDao.successInfoJson.contains("\"sinkStopped\":true"));
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void runCancelsWhenTheFlagFlipsMidScan() throws Exception {
        String db = "dtRunCancel";
        DataSourceEntity entity = h2Entity(9103L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 3);

        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(h2Def(9103L, "SELECT n AS raw_a FROM t_batch", 100L));
        final ScriptedSink sink = new ScriptedSink();
        sink.onWrite = new Runnable() {
            @Override
            public void run() {
                jobDao.current.setCancelRequested(Boolean.TRUE);
            }
        };

        realSqlEngine(jobDao, sink, entity).run(77L);

        Assert.assertNotNull("the open session must be aborted on cooperative cancel",
                sink.abortedWith);
        Assert.assertFalse(sink.completed);
        Assert.assertTrue(String.valueOf(jobDao.failureMessage).toLowerCase().contains("cancel"));
        Assert.assertEquals(DataTaskStatus.CANCELED, events.get(0).getStatus());
        Assert.assertEquals("a cooperatively cancelled job is recorded as CANCELED, not FAILED",
                DataTaskStatus.CANCELED, jobDao.current.getStatus());
    }

    @Test
    public void runTruncatesAtTheEngineDefaultCap() throws Exception {
        String db = "dtRunCap";
        DataSourceEntity entity = h2Entity(9104L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 3);

        final RecordingJobDao jobDao = new RecordingJobDao();
        // no per-definition cap: the engine-wide default decides
        jobDao.current = jobFor(h2Def(9104L, "SELECT n AS raw_a FROM t_batch", null));
        final ScriptedSink sink = new ScriptedSink();
        DataTaskJobEngine localEngine = realSqlEngine(jobDao, sink, entity);
        DataTaskTestSupport.setField(localEngine, "maxRowsDefault", 2L);

        localEngine.run(77L);

        Assert.assertEquals(2, sink.received.size());
        Assert.assertEquals(Long.valueOf(2L), jobDao.successRows);
        Assert.assertTrue(String.valueOf(jobDao.successInfoJson),
                jobDao.successInfoJson.contains("\"truncated\":true")); // the third row was cut off
    }

    @Test
    public void runUsesTheProductionDatasourceSeam() throws Exception {
        String db = "dtRunSeam";
        final DataSourceEntity entity = h2Entity(9105L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 2);

        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(h2Def(9105L, "SELECT n AS raw_a FROM t_batch", 100L));
        final ScriptedSink sink = new ScriptedSink();
        previewPoolIds.add(entity.getId()); // the seam caches its pool in DataSourceUtils

        // no loadDataSource override: driver resolution and pooling run the production code path
        DataTaskJobEngine localEngine = new DataTaskJobEngine();
        DataTaskTestSupport.setField(localEngine, "dataTaskJobDao", jobDao);
        DataTaskTestSupport.setField(localEngine, "dataSourceDao", new DataSourceDao() {
            @Override
            public DataSourceEntity getById(Long id) {
                return entity;
            }
        });
        DataTaskTestSupport.setField(localEngine, "driverLoadService", new FixedDriverLoadService());
        DataTaskTestSupport.setField(localEngine, "sinkRegistry", new DataTaskSinkRegistry(
                Collections.<DataTaskSink>singletonList((DataTaskSink) sink)));
        EventCollector collector = new EventCollector();
        DataTaskTestSupport.setField(localEngine, "eventPublisher", collector);
        events = collector.events;

        localEngine.run(77L);

        Assert.assertEquals(2, sink.received.size());
        Assert.assertEquals(Long.valueOf(2L), jobDao.successRows);
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    // ------------------------------------------------------------- JDBC-level conversions (proxy doubles)

    private static class CaptureChannel implements DataTaskJobEngine.ResultChannel {
        List<String> columns;
        List<ColumnMetadata> metadata;
        final List<Object[]> rows = new ArrayList<>();

        @Override
        public void start(List<String> columns, List<ColumnMetadata> metadata) {
            this.columns = new ArrayList<>(columns);
            this.metadata = new ArrayList<>(metadata);
        }

        @Override
        public boolean batch(List<Object[]> batch) {
            rows.addAll(batch);
            return true;
        }
    }

    private static Object scalarDefault(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static Map<String, Object> script(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /** Proxy whose methods answer from a name→result script; Callable results are invoked. */
    private static Object scriptedProxy(Class<?> type, final Map<String, Object> script) {
        return Proxy.newProxyInstance(DataTaskJobEngineTest.class.getClassLoader(),
                new Class<?>[]{type}, (p, m, a) -> {
                    String name = m.getName();
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(name)) {
                        return p == a[0];
                    }
                    Object result = script.get(name);
                    if (result instanceof Callable) {
                        return ((Callable<?>) result).call();
                    }
                    if (null != result || script.containsKey(name)) {
                        return result;
                    }
                    return scalarDefault(m.getReturnType());
                });
    }

    private static ResultSetMetaData metaProxy(final String[] labels,
                                               final java.util.function.IntFunction<Object> types,
                                               final java.util.function.IntFunction<Object> classNames) {
        return (ResultSetMetaData) Proxy.newProxyInstance(DataTaskJobEngineTest.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class}, (p, m, a) -> {
                    String name = m.getName();
                    if ("getColumnCount".equals(name)) {
                        return labels.length;
                    }
                    int column = (Integer) a[0];
                    if ("getColumnLabel".equals(name)) {
                        return labels[column - 1];
                    }
                    if ("getColumnType".equals(name)) {
                        Object value = types.apply(column - 1);
                        return value instanceof Callable ? ((Callable<?>) value).call() : value;
                    }
                    if ("getColumnClassName".equals(name)) {
                        Object value = classNames.apply(column - 1);
                        return value instanceof Callable ? ((Callable<?>) value).call() : value;
                    }
                    return scalarDefault(m.getReturnType());
                });
    }

    private static ResultSet rowSetProxy(final int rows, final String[] labels,
                                         final java.util.function.IntFunction<Object> types,
                                         final java.util.function.IntFunction<Object> classNames,
                                         final java.util.function.IntFunction<Object> cells) {
        final int[] remaining = {rows};
        return (ResultSet) Proxy.newProxyInstance(DataTaskJobEngineTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (p, m, a) -> {
                    String name = m.getName();
                    if ("next".equals(name)) {
                        return remaining[0]-- > 0;
                    }
                    if ("getObject".equals(name)) {
                        Object cell = cells.apply((Integer) a[0] - 1);
                        if (cell == READ_FAILURE) {
                            throw new SQLException("getObject failed");
                        }
                        return cell;
                    }
                    if ("getMetaData".equals(name)) {
                        return metaProxy(labels, types, classNames);
                    }
                    return scalarDefault(m.getReturnType());
                });
    }

    private static PreparedStatement statementProxy(final ResultSet rs, final List<Integer> fetchSizes) {
        return statementProxy(rs, fetchSizes, true);
    }

    private static PreparedStatement statementProxy(final ResultSet rs, final List<Integer> fetchSizes,
                                                    final boolean rowset) {
        return (PreparedStatement) Proxy.newProxyInstance(DataTaskJobEngineTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                    String name = m.getName();
                    if ("setFetchSize".equals(name)) {
                        fetchSizes.add((Integer) a[0]);
                        return null;
                    }
                    if ("execute".equals(name)) {
                        return rowset;
                    }
                    if ("getResultSet".equals(name)) {
                        return rs;
                    }
                    return scalarDefault(m.getReturnType());
                });
    }

    private static Connection connectionProxy(final ResultSet rs, final List<Integer> fetchSizes,
                                              final String productName) {
        return connectionProxy(rs, fetchSizes, productName, false, null);
    }

    private static Connection connectionProxy(final ResultSet rs, final List<Integer> fetchSizes,
                                              final String productName, final boolean failOnRestore,
                                              final boolean[] closed) {
        final int[] autoCommitCalls = {0};
        return (Connection) Proxy.newProxyInstance(DataTaskJobEngineTest.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (p, m, a) -> {
                    String name = m.getName();
                    if ("getAutoCommit".equals(name)) {
                        return true;
                    }
                    if ("setAutoCommit".equals(name)) {
                        if (failOnRestore && ++autoCommitCalls[0] > 1) {
                            // the pool-bound restore fails; the engine must swallow it and still close
                            throw new SQLException("restore autocommit failed");
                        }
                        return null;
                    }
                    if ("close".equals(name)) {
                        if (null != closed) {
                            closed[0] = true;
                        }
                        return null;
                    }
                    if ("prepareStatement".equals(name)) {
                        return statementProxy(rs, fetchSizes, !failOnRestore);
                    }
                    if ("createStatement".equals(name)) {
                        // HIVE-family executeBeforeQuery runs "set ..." through a plain statement
                        return statementProxy(rs, fetchSizes);
                    }
                    if ("getMetaData".equals(name)) {
                        if (null == productName) {
                            throw new SQLException("metadata unavailable");
                        }
                        return scriptedProxy(DatabaseMetaData.class,
                                script("getDatabaseProductName", productName));
                    }
                    return scalarDefault(m.getReturnType());
                });
    }

    private static HikariDataSource pooled(final Connection connection) {
        return new HikariDataSource() {
            @Override
            public Connection getConnection() {
                return connection;
            }
        };
    }

    private DataTaskJobEngine.StreamSpec proxySpec(HikariDataSource dataSource, int fetchSize) {
        return DataTaskJobEngine.StreamSpec.builder()
                .dataSource(dataSource)
                .product(ProductTypeEnum.MYSQL)
                .sqlMeta(new com.cs.template.SqlMeta("SELECT c1 FROM t",
                        Collections.<Object>emptyList()))
                .naming(null) // exercises the identity converter branch
                .cancelSupplier(() -> false)
                .rowLimit(5L)
                .flushIntervalMs(5000L)
                .fetchSize(fetchSize)
                .timeoutSeconds(30)
                .progress(null)
                .build();
    }

    private static final Object READ_FAILURE = new Object();

    @Test
    public void cellConversionHandlesLobArrayStructAndReadErrors() throws Exception {
        Object clobOk = scriptedProxy(java.sql.Clob.class,
                script("length", 4L, "getSubString", "clob"));
        Object clobNegative = scriptedProxy(java.sql.Clob.class, script("length", -1L));
        Object clobBroken = scriptedProxy(java.sql.Clob.class,
                script("length", (Callable<Object>) () -> {
                    throw new SQLException("clob broken");
                }));
        Object blobOk = scriptedProxy(java.sql.Blob.class,
                script("length", 3L, "getBytes", new byte[]{1, 2, 3}));
        Object blobBroken = scriptedProxy(java.sql.Blob.class,
                script("length", (Callable<Object>) () -> {
                    throw new SQLException("blob broken");
                }));
        Object blobNegative = scriptedProxy(java.sql.Blob.class, script("length", -1L));
        final Object arrayJson = scriptedProxy(java.sql.Array.class,
                script("toString", "[1,2]", "getArray", new Object[]{1, 2}));
        // what a real driver hands out: PostgreSQL's PgArray prints the {1,2} literal from toString()
        final Object arrayPg = scriptedProxy(java.sql.Array.class,
                script("toString", "{1,2}", "getArray", new Integer[]{1, 2}));
        final Object arrayPrimitive = scriptedProxy(java.sql.Array.class,
                script("getArray", new int[]{3, 4}));
        final Object arrayBroken = scriptedProxy(java.sql.Array.class, script("getArray", (Callable<Object>) () -> {
            throw new SQLException("array broken");
        }));
        final Object structMap = scriptedProxy(java.sql.Struct.class, script("toString", "{\"k\":1}"));
        final Object structPg = scriptedProxy(java.sql.Struct.class,
                script("toString", "(1,abc)", "getAttributes", new Object[]{1, "abc"}));
        final Object structRaw = scriptedProxy(java.sql.Struct.class, script("toString", "no-json"));

        final Object[] cells = {clobOk, clobNegative, blobOk, blobBroken, arrayJson, arrayPg, arrayPrimitive,
                arrayBroken, structMap, structPg, structRaw, clobBroken, READ_FAILURE, blobNegative};
        final String[] labels = new String[cells.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = "c" + (i + 1);
        }

        ResultSet rs = rowSetProxy(1, labels,
                index -> index == 0 ? sqlFailure("type unavailable") : java.sql.Types.VARCHAR,
                index -> index == 1 ? sqlFailure("class unavailable") : "java.lang.String",
                index -> cells[index]);

        CaptureChannel channel = new CaptureChannel();
        List<Integer> fetchSizes = new ArrayList<>();
        HikariDataSource pool = pooled(connectionProxy(rs, fetchSizes, "H2"));

        DataTaskJobEngine.StreamResult result = new DataTaskJobEngine().streamQuery(
                proxySpec(pool, 55), channel);

        Assert.assertTrue(result.isRowset());
        Assert.assertEquals(1L, result.getRows());
        Object[] row = channel.rows.get(0);
        Assert.assertEquals("clob", row[0]);                       // Clob → String
        Assert.assertNull(row[1]);                                 // negative Clob length → null
        Assert.assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) row[2]); // Blob → byte[]
        Assert.assertNull(row[3]);                                 // broken Blob → null
        Assert.assertEquals(Arrays.asList(1, 2), row[4]);          // Array → its elements
        Assert.assertEquals("a driver whose toString() is the {1,2} literal still yields the elements",
                Arrays.asList(1, 2), row[5]);
        Assert.assertEquals(Arrays.asList(3, 4), row[6]);          // primitive arrays box
        Assert.assertSame("an Array whose elements cannot be read stays the JDBC object", arrayBroken, row[7]);
        Assert.assertEquals(Collections.singletonMap("k", 1), row[8]); // Struct → parsed JSON map
        Assert.assertEquals(Arrays.asList(1, "abc"), row[9]);      // Struct → its attribute values
        Assert.assertSame("a Struct that yields nothing stays the JDBC object", structRaw, row[10]);
        Assert.assertNull(row[11]);                                // broken Clob → null
        Assert.assertNull("a failing getObject leaves the cell null", row[12]);
        Assert.assertNull("negative Blob length → null", row[13]);

        Assert.assertNull("failing getColumnType leaves jdbcType null",
                channel.metadata.get(0).getJdbcType());
        Assert.assertEquals("java.lang.String", channel.metadata.get(0).getClassName());
        Assert.assertEquals(Integer.valueOf(java.sql.Types.VARCHAR),
                channel.metadata.get(1).getJdbcType());
        Assert.assertNull("failing getColumnClassName leaves className null",
                channel.metadata.get(1).getClassName());
    }

    private static Object sqlFailure(String message) {
        // metadata lambdas may only return values, so failures travel as throwing Callables
        return (Callable<Object>) () -> {
            throw new SQLException(message);
        };
    }

    @Test
    public void mysqlConnectionsUseStreamingFetchAndBrokenMetaDataFallsBack() throws Exception {
        List<Integer> fetchSizes = new ArrayList<>();
        ResultSet rs = rowSetProxy(1, new String[]{"c1"},
                index -> java.sql.Types.VARCHAR,
                index -> "java.lang.String",
                index -> "v1");
        HikariDataSource mysql = pooled(connectionProxy(rs, fetchSizes, "MySQL"));
        new DataTaskJobEngine().streamQuery(proxySpec(mysql, 77), new CaptureChannel());
        Assert.assertEquals("MySQL/MariaDB stream row-by-row with Integer.MIN_VALUE",
                Collections.singletonList(Integer.MIN_VALUE), fetchSizes);

        List<Integer> fallback = new ArrayList<>();
        ResultSet rs2 = rowSetProxy(1, new String[]{"c1"},
                index -> java.sql.Types.VARCHAR,
                index -> "java.lang.String",
                index -> "v1");
        HikariDataSource brokenMeta = pooled(connectionProxy(rs2, fallback, null));
        new DataTaskJobEngine().streamQuery(proxySpec(brokenMeta, 77), new CaptureChannel());
        Assert.assertEquals("unusable metadata falls back to the configured fetch size",
                Collections.singletonList(Integer.valueOf(77)), fallback);
    }

    @Test
    public void failingAutocommitRestoreIsSwallowedAndTheConnectionStillCloses() throws Exception {
        List<Integer> fetchSizes = new ArrayList<>();
        ResultSet rs = rowSetProxy(1, new String[]{"c1"},
                index -> java.sql.Types.VARCHAR,
                index -> "java.lang.String",
                index -> "v1");
        final boolean[] closed = {false};
        // update-style execution (no rowset) with a restore that blows up on the way out
        HikariDataSource pool = pooled(connectionProxy(rs, fetchSizes, "H2", true, closed));

        DataTaskJobEngine.StreamResult result = new DataTaskJobEngine().streamQuery(
                proxySpec(pool, 50), new CaptureChannel());

        Assert.assertFalse(result.isRowset());
        Assert.assertTrue("the failing restore must not leak the connection", closed[0]);
    }

    // ------------------------------------------------------------- remaining branch corners

    @Test
    public void leaseKeeperIsCreatedOnceAndReusedAcrossInvocations() throws Exception {
        Method leaseKeeper = DataTaskJobEngine.class.getDeclaredMethod("leaseKeeper");
        leaseKeeper.setAccessible(true);
        DataTaskJobEngine engineInstance = engine(new RecordingJobDao(), new StubSink(), mustNotStream());

        Object first = leaseKeeper.invoke(engineInstance);
        Assert.assertNotNull(first);
        // the outer null check of the double-checked lock sees the cached keeper
        Object second = leaseKeeper.invoke(engineInstance);
        Assert.assertSame("the keeper must be a process-wide singleton per engine", first, second);

        java.util.concurrent.ScheduledExecutorService keeper =
                (java.util.concurrent.ScheduledExecutorService) first;
        keeper.shutdownNow();
    }

    @Test
    public void leaseKeeperSecondLockCheckReusesTheCachedKeeper() throws Exception {
        Method leaseKeeper = DataTaskJobEngine.class.getDeclaredMethod("leaseKeeper");
        leaseKeeper.setAccessible(true);
        final DataTaskJobEngine engineInstance = engine(new RecordingJobDao(), new StubSink(), mustNotStream());

        // a keeper built by a first caller: parked into the field from outside while the
        // worker is blocked on the engine monitor, so the inner null check must see it
        java.util.concurrent.ScheduledExecutorService preinstalled =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        Field keeperField = DataTaskJobEngine.class.getDeclaredField("leaseKeeper");
        keeperField.setAccessible(true);
        keeperField.set(engineInstance, null);

        final java.util.concurrent.atomic.AtomicReference<Object> seen = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                seen.set(leaseKeeper.invoke(engineInstance));
            } catch (Throwable t) {
                seen.set(t);
            }
        }, "lease-keeper-dcl-worker");
        synchronized (engineInstance) {
            worker.start();
            long deadline = System.currentTimeMillis() + 5000L;
            while (worker.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
            Assert.assertEquals("worker must be parked on the engine monitor",
                    Thread.State.BLOCKED, worker.getState());
            keeperField.set(engineInstance, preinstalled);
        }
        worker.join(5000L);
        Assert.assertSame("the inner check must return the pre-cached keeper",
                preinstalled, seen.get());
        preinstalled.shutdownNow();
    }

    @Test
    public void declaredInliningWithoutBindParametersKeepsTheSingleConsultation() throws Exception {
        // the statement is fully inlined through ${}: nothing to re-render, no second judgement
        final StubStatementSink sink = new StubStatementSink();
        sink.inlineParameters = true;
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = statementJob(statementDefinition());

        DataTaskJobEngine localEngine = engine(jobDao, sink, mustNotStream());

        localEngine.run(77L);

        Assert.assertEquals(1, sink.consulted.size());
        Assert.assertEquals(1, sink.executed.size());
        Assert.assertTrue(sink.executed.get(0).getSqlParameters().isEmpty());
        Assert.assertTrue(jobDao.successInfoJson.contains("\"statementDelegated\":true"));
        Assert.assertFalse("no rewrite happened, the inlining flag must not appear",
                jobDao.successInfoJson.contains("parameterInlining"));
    }

    @Test
    public void delegatedStatementWithoutOutcomeStillSucceedsWithoutAnArtifact() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        sink.nullOutcome = true;
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = statementJob(statementDefinition());

        engine(jobDao, sink, mustNotStream()).run(77L);

        Assert.assertNull(jobDao.successUri);
        Assert.assertTrue(jobDao.successInfoJson.contains("\"statementDelegated\":true"));
        Assert.assertFalse("no info map arrived from the sink",
                jobDao.successInfoJson.contains("location"));
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void delegatedStatementOutcomeWithoutInfoSkipsTheInfoMerge() throws Exception {
        final StubStatementSink sink = new StubStatementSink();
        sink.outcomeWithoutInfo = true;
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = statementJob(statementDefinition());

        engine(jobDao, sink, mustNotStream()).run(77L);

        Assert.assertEquals(ARTIFACT_LOCATION, jobDao.successUri);
        Assert.assertFalse(jobDao.successInfoJson.contains("location"));
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void runIgnoresTheJobWhenTheRowVanishesBeforeTheRunningCheck() {
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());
        jobDao.getByIdVanishesAfterFirst = true; // the pickup read succeeds, the check misses

        engine(jobDao, new StubSink(), mustNotStream()).run(77L);

        Assert.assertTrue("no event may be published for a vanished job", events.isEmpty());
        Assert.assertNull(jobDao.failureMessage);
        Assert.assertNull(jobDao.successRows);
    }

    @Test
    public void runWithoutAnEventPublisherStaysSilentButSucceeds() throws Exception {
        final StubSink sink = new StubSink();
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());
        cannedRows = 1;
        DataTaskJobEngine localEngine = engine(jobDao, sink, new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) throws Exception {
                channel.start(new ArrayList<>(Arrays.asList("raw_a")),
                        Collections.<ColumnMetadata>emptyList());
                List<Object[]> batch = new ArrayList<>();
                batch.add(new Object[]{"v"});
                channel.batch(batch);
            }
        });
        DataTaskTestSupport.setField(localEngine, "eventPublisher", null);

        localEngine.run(77L);

        Assert.assertEquals(Long.valueOf(1L), jobDao.successRows);
        Assert.assertTrue("no publisher wired: nothing may be published", events.isEmpty());
    }

    @Test
    public void messagelessFailuresRecordTheExceptionClassName() {
        RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());
        engine(jobDao, new StubSink(), new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) {
                throw new RuntimeException(); // no message at all
            }
        }).run(77L);

        Assert.assertEquals("RuntimeException", jobDao.failureMessage);
        Assert.assertEquals(DataTaskStatus.FAILED, events.get(0).getStatus());
    }

    @Test
    public void runCancelsWhenTheJobRowVanishesMidScan() throws Exception {
        String db = "dtRunVanish";
        DataSourceEntity entity = h2Entity(9111L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 3);

        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(h2Def(9111L, "SELECT n AS raw_a FROM t_batch", 100L));
        final ScriptedSink sink = new ScriptedSink();
        sink.onWrite = new Runnable() {
            @Override
            public void run() {
                jobDao.current = null; // the row disappears while rows stream
            }
        };

        realSqlEngine(jobDao, sink, entity).run(77L);

        Assert.assertNotNull(sink.abortedWith);
        Assert.assertFalse(sink.completed);
        Assert.assertEquals(DataTaskStatus.CANCELED, events.get(0).getStatus());
    }

    @Test
    public void runCancelsWhenTheJobLeavesTheRunningStateMidScan() throws Exception {
        String db = "dtRunNotRunning";
        DataSourceEntity entity = h2Entity(9112L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 3);

        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(h2Def(9112L, "SELECT n AS raw_a FROM t_batch", 100L));
        final ScriptedSink sink = new ScriptedSink();
        sink.onWrite = new Runnable() {
            @Override
            public void run() {
                // the reaper failed the job while the scan is in flight
                jobDao.current.setStatus(DataTaskStatus.FAILED);
            }
        };

        realSqlEngine(jobDao, sink, entity).run(77L);

        Assert.assertNotNull(sink.abortedWith);
        Assert.assertEquals(DataTaskStatus.CANCELED, events.get(0).getStatus());
    }

    @Test
    public void snapshotWithoutANamingStrategyFallsBackToCamelCase() throws Exception {
        String db = "dtRunNaming";
        DataSourceEntity entity = h2Entity(9113L, db);
        h2Exec(entity, "CREATE TABLE t_naming (col_one INT)");
        h2Exec(entity, "INSERT INTO t_naming VALUES (1)");

        final RecordingJobDao jobDao = new RecordingJobDao();
        DataTaskDefEntity def = h2Def(9113L, "SELECT col_one FROM t_naming", 100L);
        def.setNamingStrategy(null); // the defaulting branch of the stream spec
        jobDao.current = jobFor(def);
        final ScriptedSink sink = new ScriptedSink();

        realSqlEngine(jobDao, sink, entity).run(77L);

        Assert.assertEquals(Collections.singletonList("colOne"),
                sink.requests.get(0).getColumns());
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void nonPositiveConfiguredCapFallsBackToTheEngineDefault() throws Exception {
        String db = "dtRunZeroCap";
        DataSourceEntity entity = h2Entity(9114L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 3);

        final RecordingJobDao jobDao = new RecordingJobDao();
        // maxRows=0 is not a cap: the engine-wide default decides
        jobDao.current = jobFor(h2Def(9114L, "SELECT n AS raw_a FROM t_batch", 0L));
        final ScriptedSink sink = new ScriptedSink();
        DataTaskJobEngine localEngine = realSqlEngine(jobDao, sink, entity);
        DataTaskTestSupport.setField(localEngine, "maxRowsDefault", 2L);

        localEngine.run(77L);

        Assert.assertEquals(2, sink.received.size());
        Assert.assertTrue(jobDao.successInfoJson.contains("\"truncated\":true"));
    }

    @Test
    public void sinkRefusingTheTrailingPartialBatchStillCompletesTheSession() throws Exception {
        String db = "dtRunTrailRefuse";
        DataSourceEntity entity = h2Entity(9115L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 3);

        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(h2Def(9115L, "SELECT n AS raw_a FROM t_batch", 100L));
        final ScriptedSink sink = new ScriptedSink();
        sink.proceedAfterWrite = false; // the only batch is the trailing partial one

        realSqlEngine(jobDao, sink, entity).run(77L);

        // rows were written once, then the sink asked to stop: completion still runs
        Assert.assertEquals(3, sink.received.size());
        Assert.assertTrue(sink.completed);
        Assert.assertTrue(jobDao.successInfoJson.contains("\"sinkStopped\":true"));
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void rowPipelineCompleteWithoutOutcomeKeepsTheJobArtifactless() throws Exception {
        String db = "dtRunNoOutcome";
        DataSourceEntity entity = h2Entity(9116L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 1);

        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(h2Def(9116L, "SELECT n AS raw_a FROM t_batch", 100L));
        final ScriptedSink sink = new ScriptedSink();
        sink.nullCompleteOutcome = true; // complete() returns null: no artifact

        realSqlEngine(jobDao, sink, entity).run(77L);

        Assert.assertEquals(Long.valueOf(1L), jobDao.successRows);
        Assert.assertNull(jobDao.successUri);
        Assert.assertTrue(jobDao.successInfoJson.contains("\"deliveredRows\":1"));
        Assert.assertEquals(DataTaskStatus.SUCCESS, events.get(0).getStatus());
    }

    @Test
    public void mariadbConnectionsAlsoUseTheStreamingFetchSize() throws Exception {
        List<Integer> fetchSizes = new ArrayList<>();
        ResultSet rs = rowSetProxy(1, new String[]{"c1"},
                index -> java.sql.Types.VARCHAR,
                index -> "java.lang.String",
                index -> "v1");
        HikariDataSource mariadb = pooled(connectionProxy(rs, fetchSizes, "MariaDB"));
        new DataTaskJobEngine().streamQuery(proxySpec(mariadb, 88), new CaptureChannel());
        Assert.assertEquals(Collections.singletonList(Integer.MIN_VALUE), fetchSizes);
    }

    @Test
    public void failingSessionCompletionAbortsTheOpenSession() throws Exception {
        String db = "dtRunCompleteThrows";
        DataSourceEntity entity = h2Entity(9117L, db);
        h2Exec(entity, "CREATE TABLE t_batch (n BIGINT PRIMARY KEY, raw_a VARCHAR(16), raw_b BIGINT)");
        h2InsertRows(entity, 1);

        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(h2Def(9117L, "SELECT n AS raw_a FROM t_batch", 100L));
        final ScriptedSink sink = new ScriptedSink();
        sink.onWrite = new Runnable() {
            @Override
            public void run() {
                // flip a switch so the session's complete() explodes after the last batch
                sink.failComplete = true;
            }
        };

        realSqlEngine(jobDao, sink, entity).run(77L);

        // the session variable is live when completion fails: abort runs instead of a leak
        Assert.assertNotNull(sink.abortedWith);
        Assert.assertTrue("complete() must have been attempted", sink.completeBeenCalled);
        Assert.assertFalse(sink.completed);
        Assert.assertNull(jobDao.successRows);
        Assert.assertNotNull(jobDao.failureMessage);
        Assert.assertEquals(DataTaskStatus.FAILED, events.get(0).getStatus());
    }

    @Test
    public void failureAfterTheRowVanishesIsFailedNotCanceled() {        // a plain failure while the job row is gone: the cancel probe finds nothing, so the
        // job must terminate FAILED (the CancelledException arm is not taken)
        final RecordingJobDao jobDao = new RecordingJobDao();
        jobDao.current = jobFor(definition());
        engine(jobDao, new StubSink(), new ChannelScript() {
            @Override
            public void drive(DataTaskJobEngine.ResultChannel channel) {
                jobDao.current = null;
                throw new RuntimeException("boom with the row gone");
            }
        }).run(77L);

        Assert.assertEquals(DataTaskStatus.FAILED, events.get(0).getStatus());
        Assert.assertTrue(String.valueOf(jobDao.failureMessage), jobDao.failureMessage.contains("boom"));
    }

    @Test
    public void connectionAlreadyInManualModeNeedsNoAutocommitJuggling() throws Exception {
        List<Integer> fetchSizes = new ArrayList<>();
        ResultSet rs = rowSetProxy(1, new String[]{"c1"},
                index -> java.sql.Types.VARCHAR,
                index -> "java.lang.String",
                index -> "v1");
        final java.util.concurrent.atomic.AtomicInteger autoCommitSets =
                new java.util.concurrent.atomic.AtomicInteger();
        Connection manual = (Connection) Proxy.newProxyInstance(
                DataTaskJobEngineTest.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (p, m, a) -> {
                    String name = m.getName();
                    if ("getAutoCommit".equals(name)) {
                        return false; // already manual: no setAutoCommit calls expected
                    }
                    if ("setAutoCommit".equals(name)) {
                        autoCommitSets.incrementAndGet();
                        return null;
                    }
                    if ("prepareStatement".equals(name)) {
                        return statementProxy(rs, fetchSizes, true);
                    }
                    return scalarDefault(m.getReturnType());
                });

        DataTaskJobEngine.StreamResult result = new DataTaskJobEngine().streamQuery(
                proxySpec(pooled(manual), 50), new CaptureChannel());

        Assert.assertTrue(result.isRowset());
        Assert.assertEquals(1L, result.getRows());
        Assert.assertEquals("manual connections must not be toggled", 0, autoCommitSets.get());
    }

    @Test
    public void hiveFamilyRunsItsExecuteBeforeQueryHook() throws Exception {        List<Integer> fetchSizes = new ArrayList<>();
        final List<String> statements = Collections.synchronizedList(new ArrayList<String>());
        ResultSet rs = rowSetProxy(1, new String[]{"c1"},
                index -> java.sql.Types.VARCHAR,
                index -> "java.lang.String",
                index -> "v1");
        // record the statement the HIVE pre-query hook executes
        java.util.concurrent.atomic.AtomicBoolean hookRan = new java.util.concurrent.atomic.AtomicBoolean(false);
        Statement preQuery = (Statement) Proxy.newProxyInstance(DataTaskJobEngineTest.class.getClassLoader(),
                new Class<?>[]{Statement.class}, (p, m, a) -> {
                    if ("execute".equals(m.getName())) {
                        statements.add(String.valueOf(a[0]));
                        return true;
                    }
                    return scalarDefault(m.getReturnType());
                });
        Connection connection = (Connection) Proxy.newProxyInstance(
                DataTaskJobEngineTest.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (p, m, a) -> {
                    String name = m.getName();
                    if ("createStatement".equals(name)) {
                        hookRan.set(true);
                        return preQuery;
                    }
                    if ("prepareStatement".equals(name)) {
                        return statementProxy(rs, fetchSizes, true);
                    }
                    if ("getAutoCommit".equals(name)) {
                        return true;
                    }
                    return scalarDefault(m.getReturnType());
                });

        DataTaskJobEngine.StreamSpec spec = DataTaskJobEngine.StreamSpec.builder()
                .dataSource(pooled(connection))
                .product(ProductTypeEnum.HIVE)
                .sqlMeta(new com.cs.template.SqlMeta("SELECT c1 FROM t", Collections.emptyList()))
                .naming(null)
                .cancelSupplier(() -> false)
                .rowLimit(5L)
                .flushIntervalMs(5000L)
                .fetchSize(33)
                .timeoutSeconds(30)
                .progress(null)
                .build();
        DataTaskJobEngine.StreamResult result = new DataTaskJobEngine().streamQuery(spec, new CaptureChannel());

        Assert.assertTrue(result.isRowset());
        Assert.assertEquals(1L, result.getRows());
        Assert.assertTrue("the pre-query hook must run before the statement", hookRan.get());
        Assert.assertEquals("set hive.resultset.use.unique.column.names=false", statements.get(0));
    }
}
