// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.datatask.CellDecorator;
import com.cs.common.datatask.ColumnMetadata;
import com.cs.common.datatask.DataTaskSink;
import com.cs.common.datatask.SinkOutcome;
import com.cs.common.datatask.SinkRequest;
import com.cs.common.datatask.SinkSession;
import com.cs.common.dto.BaseParam;
import com.cs.common.dto.ItemParam;
import com.cs.common.enums.DataTaskStatus;
import com.cs.common.enums.NamingStrategyEnum;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.common.util.LambdaUtils;
import com.cs.core.driver.DriverLoadService;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.dao.DataTaskJobDao;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.entity.DataTaskDefEntity;
import com.cs.persistence.entity.DataTaskJobEntity;
import com.cs.persistence.util.JsonUtils;
import com.cs.template.SqlMeta;
import com.cs.template.XmlSqlTemplate;
import com.cs.core.util.DataSourceUtils;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

/**
 * Executes claimed data task jobs against the definition snapshot stored on the job
 * record: loads the datasource through the same pool/driver infrastructure used by
 * synchronous API execution, renders the SQL template, streams rows in bounded
 * batches through the reshaping plan into the delivery provider session, refreshes
 * progress/lease while scanning and finalizes the job row together with a Spring
 * {@link DataTaskEvent} carrying the terminal state.
 */
@Slf4j
@Service
public class DataTaskJobEngine {

    private static final int WRITE_BATCH_ROWS = 1000;
    private static final int MESSAGE_LIMIT = 2000;

    private static final com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> PARAMS_TYPE =
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            };

    @Resource
    private DataTaskJobDao dataTaskJobDao;
    @Resource
    private DataSourceDao dataSourceDao;
    @Resource
    private DriverLoadService driverLoadService;
    @Resource
    private DataTaskSinkRegistry sinkRegistry;

    @Setter
    @Autowired(required = false)
    private List<CellDecorator> cellDecorators = Collections.emptyList();

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Value("${datapoly.data-task.lease-seconds:600}")
    private int leaseSeconds;
    @Value("${datapoly.data-task.flush-interval-ms:5000}")
    private long flushIntervalMs;
    @Value("${datapoly.data-task.fetch-size:1000}")
    private int fetchSize;
    @Value("${datapoly.data-task.query-timeout-seconds:1800}")
    private int queryTimeoutSeconds;
    @Value("${datapoly.data-task.max-rows-default:1000000}")
    private long maxRowsDefault;

    /** Claim exactly one PENDING job for this worker; null once the queue is drained. */
    public Long claimNext(String workerAddr) {
        long now = System.currentTimeMillis();
        List<Long> ids = dataTaskJobDao.claimPending(1, workerAddr,
                new Timestamp(now), new Timestamp(now + leaseSeconds * 1000L));
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Reaper entry-point for jobs whose worker vanished; safe from any host instance. */
    public void reapLost(String message) {
        int reaped = dataTaskJobDao.reapExpired(message);
        if (reaped > 0) {
            log.warn("Reaped {} expired-lease data task jobs", reaped);
        }
    }

    /**
     * Datasource acquisition seam (shared pooled infrastructure from API execution);
     * overridable in tests to hand back an unconnected pool instance.
     */
    protected HikariDataSource loadDataSource(DataSourceEntity dsEntity) {
        File driverPath = driverLoadService.getVersionDriverFile(dsEntity.getType(), dsEntity.getVersion());
        return DataSourceUtils.getHikariDataSource(dsEntity, driverPath.getAbsolutePath());
    }

    public void run(Long jobId) {
        DataTaskJobEntity job = dataTaskJobDao.getById(jobId);
        if (null == job) {
            log.warn("Data task job {} disappeared before execution", jobId);
            return;
        }
        long delivered = 0L;
        SinkSession session = null;
        SessionChannel channel = null;
        DataTaskStatus terminal;
        String artifactUri = null;
        String failureMessage = null;
        String sinkType = null;
        Map<String, Object> artifactInfo = new LinkedHashMap<>();
        try {
            if (!isRunning(jobId)) {
                return; // canceled or reaped between claim and pickup
            }
            DataTaskDefEntity snapshot = normalize(parseSnapshot(job.getSnapshot()));
            sinkType = StringUtils.defaultString(snapshot.getSinkType());
            artifactInfo.put("sinkType", sinkType);

            DataSourceEntity dsEntity = dataSourceDao.getById(snapshot.getDatasourceId());
            if (null == dsEntity) {
                throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS,
                        "common.datasource.not.found", snapshot.getDatasourceId());
            }
            HikariDataSource dataSource = loadDataSource(dsEntity);

            Map<String, Object> params = StringUtils.isBlank(job.getParamsJson())
                    ? Collections.emptyMap()
                    : JsonUtils.toBeanType(job.getParamsJson(), PARAMS_TYPE);
            XmlSqlTemplate template = new XmlSqlTemplate(snapshot.getSqlText());
            SqlMeta sqlMeta = template.process(params, Boolean.TRUE.equals(snapshot.getDollarAllowed()));

            Optional<DataTaskSink> sinkHolder = sinkRegistry.get(snapshot.getSinkType());
            if (!sinkHolder.isPresent()) {
                throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                        "datatask.sink.unknown", snapshot.getSinkType());
            }

            channel = new SessionChannel(dataTaskJobDao, jobId, snapshot,
                    sinkHolder.get(), job);
            StreamSpec streamSpec = StreamSpec.builder()
                    .dataSource(dataSource)
                    .product(dsEntity.getType())
                    .sqlMeta(sqlMeta)
                    .naming(null == snapshot.getNamingStrategy()
                            ? NamingStrategyEnum.CAMEL_CASE : snapshot.getNamingStrategy())
                    .cancelSupplier(channel::isJobCancelled)
                    .rowLimit(effectiveMaxRows(snapshot.getMaxRows()))
                    .flushIntervalMs(flushIntervalMs)
                    .fetchSize(fetchSize)
                    .timeoutSeconds(queryTimeoutSeconds)
                    .progress(rows -> onProgress(jobId, rows))
                    .build();

            StreamResult result = streamQuery(streamSpec, channel);
            delivered = result.getRows();
            session = channel.session();
            artifactInfo.put("sinkStopped", channel.isSinkAskedStop());

            if (!result.isRowset()) {
                artifactInfo.put("updateCount", result.getUpdateCount());
                terminal = DataTaskStatus.SUCCESS;
            } else {
                SinkOutcome outcome = session.complete();
                session = null; // ownership moved into complete(); nothing left to abort
                if (null != outcome) {
                    artifactUri = outcome.getArtifactUri();
                    if (null != outcome.getInfo()) {
                        artifactInfo.putAll(outcome.getInfo());
                    }
                }
                artifactInfo.put("truncated", result.isTruncated());
                artifactInfo.put("deliveredRows", delivered);
                terminal = DataTaskStatus.SUCCESS;
            }

            dataTaskJobDao.finishSuccess(jobId, delivered, artifactUri,
                    JsonUtils.toJsonString(artifactInfo), now());
        } catch (Throwable t) {
            if (!(t instanceof CancelledException)) {
                log.error("Data task job {} failed", jobId, t);
            }
            SinkSession open = session;
            if (null == open && null != channel) {
                // an exception between session open and normal completion must not leak resources
                open = channel.session();
            }
            if (null != open) {
                open.abort(t);
            }
            boolean cancelled = t instanceof CancelledException || isRunningAndCancelled(jobId);
            terminal = cancelled ? DataTaskStatus.CANCELED : DataTaskStatus.FAILED;
            failureMessage = null == t.getMessage() ? t.getClass().getSimpleName() : t.getMessage();
            dataTaskJobDao.finishFailure(jobId, truncate(failureMessage), delivered, now());
            artifactUri = null;
        }
        publish(new DataTaskEvent(jobId, job.getDefId(), job.getDefName(), terminal,
                delivered, artifactUri, failureMessage, sinkType));
    }

    /** Manager-side synchronous preview: no job record, no sink involvement. */
    public Map<String, Object> debugPreview(DataTaskDefEntity def, Map<String, Object> boundParams, int limit)
            throws Exception {
        DataSourceEntity dsEntity = dataSourceDao.getById(def.getDatasourceId());
        if (null == dsEntity) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS,
                    "common.datasource.not.found", def.getDatasourceId());
        }
        File driverPath = driverLoadService.getVersionDriverFile(dsEntity.getType(), dsEntity.getVersion());
        HikariDataSource dataSource = DataSourceUtils.getHikariDataSource(dsEntity, driverPath.getAbsolutePath());

        XmlSqlTemplate template = new XmlSqlTemplate(def.getSqlText());
        SqlMeta sqlMeta = template.process(boundParams, Boolean.TRUE.equals(def.getDollarAllowed()));

        PreviewChannel channel = new PreviewChannel(def);
        StreamSpec spec = StreamSpec.builder()
                .dataSource(dataSource)
                .product(dsEntity.getType())
                .sqlMeta(sqlMeta)
                .naming(null == def.getNamingStrategy()
                        ? NamingStrategyEnum.CAMEL_CASE : def.getNamingStrategy())
                .cancelSupplier(() -> false)
                .rowLimit(Math.max(1, limit))
                .flushIntervalMs(flushIntervalMs)
                .fetchSize(Math.min(fetchSize, 200))
                .timeoutSeconds(queryTimeoutSeconds)
                .progress(null)
                .build();
        StreamResult result = streamQuery(spec, channel);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("columns", channel.outputColumns());
        view.put("rows", channel.rowsView());
        view.put("totalPreviewed", result.isRowset() ? result.getRows() : 0L);
        view.put("updateCount", result.isRowset() ? null : result.getUpdateCount());
        view.put("truncated", result.isRowset() && result.isTruncated());
        return view;
    }

    // ------------------------------------------------------------------ streaming

    /**
     * Drives one rendered statement and feeds rows to the two-phase channel: start()
     * receives naming-strategy-converted labels once, then positional value arrays in
     * bounded batches. Non-row statements short-circuit with update-count results.
     */
    protected StreamResult streamQuery(StreamSpec spec, ResultChannel channel) throws Exception {
        Connection connection = spec.getDataSource().getConnection();
        try {
            Consumer<Connection> executeBeforeQuery = spec.getProduct().getContext().getExecuteBeforeQuery();
            LambdaUtils.ifDo(null != executeBeforeQuery, () -> executeBeforeQuery.accept(connection));

            PreparedStatement statement = connection.prepareStatement(spec.getSqlMeta().getSql());
            try {
                statement.setQueryTimeout(spec.getTimeoutSeconds());
                statement.setFetchSize(isMySqlConnection(connection) ? Integer.MIN_VALUE : spec.getFetchSize());
                List<Object> paramValues = spec.getSqlMeta().getParameter();
                for (int i = 1; i <= paramValues.size(); i++) {
                    statement.setObject(i, paramValues.get(i - 1));
                }
                boolean hasResult = statement.execute();
                if (!hasResult) {
                    return StreamResult.update(statement.getUpdateCount());
                }
                return drainResultSet(statement.getResultSet(), spec, channel);
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    private StreamResult drainResultSet(ResultSet rs, StreamSpec spec, ResultChannel channel) throws Exception {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            Function<String, String> converter =
                    Objects.isNull(spec.getNaming()) ? Function.identity() : spec.getNaming().getFunction();
            List<String> columns = new ArrayList<>(columnCount);
            List<ColumnMetadata> metadata = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(converter.apply(meta.getColumnLabel(i)));
                metadata.add(readColumnMetadata(meta, i));
            }
            channel.start(columns, metadata);

            List<Object[]> buffer = new ArrayList<>(WRITE_BATCH_ROWS);
            long total = 0L;
            boolean truncated = false;
            boolean stoppedByChannel = false;
            boolean reachedEnd = false;
            long lastFlush = System.currentTimeMillis();
            while (!reachedEnd && !stoppedByChannel && !truncated) {
                if (!rs.next()) {
                    reachedEnd = true;
                    break;
                }
                buffer.add(readCells(rs, columnCount));
                total++;
                boolean capHit = total >= spec.getRowLimit();
                if (buffer.size() >= WRITE_BATCH_ROWS || capHit) {
                    if (!channel.batch(buffer)) {
                        stoppedByChannel = true;
                        break;
                    }
                    buffer.clear();
                    lastFlush = tickProgress(spec, total, lastFlush);
                    if (capHit) {
                        // one extra scan decides whether the cap actually cut data off
                        if (rs.next()) {
                            truncated = true;
                        } else {
                            reachedEnd = true;
                        }
                        break;
                    }
                }
            }
            if (!reachedEnd && !stoppedByChannel && !truncated && !buffer.isEmpty()) {
                // trailing partial batch smaller than WRITE_BATCH_ROWS
                if (channel.batch(buffer)) {
                    tickProgress(spec, total, lastFlush);
                }
                buffer.clear();
            }
            return StreamResult.rows(total, truncated);
        } finally {
            rs.close();
        }
    }

    private long tickProgress(StreamSpec spec, long total, long lastFlush) throws Exception {
        if (spec.getCancelSupplier().getAsBoolean()) {
            throw new CancelledException();
        }
        long now = System.currentTimeMillis();
        if (now - lastFlush >= spec.getFlushIntervalMs()) {
            if (null != spec.getProgress()) {
                spec.getProgress().accept(total);
            }
            return now;
        }
        return lastFlush;
    }

    private Object[] readCells(ResultSet rs, int columnCount) throws SQLException {
        Object[] cells = new Object[columnCount];
        for (int i = 0; i < columnCount; i++) {
            try {
                Object value = rs.getObject(i + 1);
                if (value instanceof java.sql.Clob) {
                    value = convertClob((java.sql.Clob) value);
                } else if (value instanceof java.sql.Blob) {
                    value = convertBlob((java.sql.Blob) value);
                } else if (value instanceof java.sql.Array) {
                    value = convertArray((java.sql.Array) value);
                } else if (value instanceof java.sql.Struct) {
                    value = convertStruct((java.sql.Struct) value);
                }
                cells[i] = value;
            } catch (SQLException se) {
                log.warn("Failed to call jdbc ResultSet::getObject(): {}", se.getMessage(), se);
                cells[i] = null;
            }
        }
        return cells;
    }

    private static ColumnMetadata readColumnMetadata(ResultSetMetaData meta, int column) {
        Integer jdbcType = null;
        String className = null;
        try {
            jdbcType = meta.getColumnType(column);
        } catch (SQLException ignore) {
            // driver-dependent metadata access may throw; the slot stays null instead of failing the job
        }
        try {
            className = meta.getColumnClassName(column);
        } catch (SQLException ignore) {
        }
        return ColumnMetadata.builder().jdbcType(jdbcType).className(className).build();
    }

    // ------------------------------------------------------------- collaborators

    public interface ResultChannel {
        void start(List<String> columns, List<ColumnMetadata> metadata) throws Exception;

        /** @return false to ask the reader to stop (sink request, capacity, cancellation) */
        boolean batch(List<Object[]> rows) throws Exception;
    }

    public static class CancelledException extends RuntimeException {
        public CancelledException() {
            super("data task cancelled");
        }
    }

    @Getter
    public static class StreamResult {
        private final boolean rowset;
        private final long rows;
        private final boolean truncated;
        private final int updateCount;

        private StreamResult(boolean rowset, long rows, boolean truncated, int updateCount) {
            this.rowset = rowset;
            this.rows = rows;
            this.truncated = truncated;
            this.updateCount = updateCount;
        }

        static StreamResult rows(long rows, boolean truncated) {
            return new StreamResult(true, rows, truncated, 0);
        }

        static StreamResult update(int updateCount) {
            return new StreamResult(false, 0, false, updateCount);
        }
    }

    @Builder
    @Getter
    public static class StreamSpec {
        private final HikariDataSource dataSource;
        private final ProductTypeEnum product;
        private final SqlMeta sqlMeta;
        private final NamingStrategyEnum naming;
        private final BooleanSupplier cancelSupplier;
        private final long rowLimit;
        private final long flushIntervalMs;
        private final int fetchSize;
        private final int timeoutSeconds;
        /** coarse progress callback; null for synchronous previews */
        private final LongConsumer progress;
    }

    private class SessionChannel implements ResultChannel {
        private final DataTaskJobDao jobDao;
        private final Long jobId;
        private final DataTaskDefEntity spec;
        private final DataTaskSink sink;
        private final DataTaskJobEntity job;
        private volatile DataTaskOutputPlan plan;
        private volatile SinkSession sessionRef;
        private volatile boolean sinkAskedStop;

        SessionChannel(DataTaskJobDao jobDao, Long jobId, DataTaskDefEntity spec,
                       DataTaskSink sink, DataTaskJobEntity job) {
            this.jobDao = jobDao;
            this.jobId = jobId;
            this.spec = spec;
            this.sink = sink;
            this.job = job;
        }

        SinkSession session() {
            return sessionRef;
        }

        boolean isSinkAskedStop() {
            return sinkAskedStop;
        }

        boolean isJobCancelled() {
            DataTaskJobEntity current = jobDao.getById(jobId);
            return null == current || !Objects.equals(current.getStatus(), DataTaskStatus.RUNNING)
                    || Boolean.TRUE.equals(current.getCancelRequested());
        }

        @Override
        public void start(List<String> columns, List<ColumnMetadata> metadata) throws Exception {
            plan = buildPlan(spec, columns);
            SinkRequest request = SinkRequest.builder()
                    .jobId(jobId)
                    .taskName(job.getDefName())
                    .sinkType(spec.getSinkType())
                    .sinkConfig(spec.getSinkConfig())
                    .columns(new ArrayList<>(plan.getOutputColumns()))
                    .columnMetadata(new ArrayList<>(plan.select(metadata)))
                    .outputFormats(spec.getResponseFormat())
                    .submittedBy(job.getSubmittedBy())
                    .build();
            sessionRef = sink.openSession(request);
        }

        @Override
        public boolean batch(List<Object[]> rows) throws Exception {
            if (null == plan || null == sessionRef) {
                return false;
            }
            List<List<Object>> shaped = new ArrayList<>(rows.size());
            for (Object[] raw : rows) {
                shaped.add(Arrays.asList(projectWithDecorators(raw)));
            }
            SinkSession active = sessionRef;
            Iterator<List<Object>> iterator = shaped.iterator();
            boolean proceed = active.writeRows(() -> iterator);
            if (!proceed) {
                sinkAskedStop = true;
            }
            return proceed;
        }

        private Object[] projectWithDecorators(Object[] raw) {
            Object[] shaped = plan.project(raw);
            if (cellDecorators.isEmpty()) {
                return shaped;
            }
            List<String> headers = plan.getOutputColumns();
            for (CellDecorator decorator : cellDecorators) {
                for (int i = 0; i < shaped.length; i++) {
                    String header = i < headers.size() ? headers.get(i) : "";
                    shaped[i] = decorator.decorate(header, i, shaped[i]);
                }
            }
            return shaped;
        }
    }

    private class PreviewChannel implements ResultChannel {
        private final DataTaskDefEntity def;
        private volatile DataTaskOutputPlan plan;
        private final List<Map<String, Object>> collected = new ArrayList<>();

        PreviewChannel(DataTaskDefEntity def) {
            this.def = def;
        }

        List<Object> outputColumns() {
            return null == plan ? Collections.emptyList() : new ArrayList<>(plan.getOutputColumns());
        }

        List<Map<String, Object>> rowsView() {
            return collected;
        }

        @Override
        public void start(List<String> columns, List<ColumnMetadata> metadata) {
            plan = buildPlan(def, columns);
        }

        @Override
        public boolean batch(List<Object[]> rows) {
            if (null == plan) {
                return false;
            }
            List<String> headers = plan.getOutputColumns();
            for (Object[] raw : rows) {
                Object[] shaped = plan.project(raw);
                Map<String, Object> view = new LinkedHashMap<>();
                for (int i = 0; i < headers.size() && i < shaped.length; i++) {
                    view.put(headers.get(i), shaped[i]);
                }
                collected.add(view);
            }
            // the reader enforces the preview cap via StreamSpec.rowLimit
            return true;
        }
    }

    // ------------------------------------------------------------------- helpers

    private DataTaskOutputPlan buildPlan(DataTaskDefEntity spec, List<String> sourceColumns) {
        return DataTaskOutputPlan.resolve(
                sourceColumns,
                spec.getColumnAlias(),
                spec.getColumnOrder(),
                Boolean.TRUE.equals(spec.getApplyFormatToString()),
                spec.getResponseFormat());
    }

    private boolean isRunning(Long jobId) {
        DataTaskJobEntity current = dataTaskJobDao.getById(jobId);
        return null != current && DataTaskStatus.RUNNING == current.getStatus();
    }

    private boolean isRunningAndCancelled(Long jobId) {
        DataTaskJobEntity current = dataTaskJobDao.getById(jobId);
        return null != current && Boolean.TRUE.equals(current.getCancelRequested());
    }

    protected void onProgress(Long jobId, long totalRead) {
        dataTaskJobDao.heartbeat(jobId, totalRead,
                new Timestamp(System.currentTimeMillis() + leaseSeconds * 1000L));
    }

    private static DataTaskDefEntity parseSnapshot(String snapshotJson) {
        DataTaskDefEntity entity = JsonUtils.toBeanObject(snapshotJson, DataTaskDefEntity.class);
        if (null == entity) {
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "datatask.snapshot.invalid");
        }
        return entity;
    }

    /**
     * Deserialized snapshots carry children erased to LinkedHashMap by Jackson;
     * restore concrete BaseParam instances so parameter binding stays type-safe.
     */
    static DataTaskDefEntity normalize(DataTaskDefEntity entity) {
        if (null == entity.getParams()) {
            return entity;
        }
            for (ItemParam param : entity.getParams()) {
                List<BaseParam> children = param.getChildren();
                if (null == children || children.isEmpty()) {
                    continue;
                }
                List<BaseParam> fixed = new ArrayList<>(children.size());
                for (BaseParam child : children) {
                    if (child instanceof BaseParam && !(child instanceof Map)) {
                        fixed.add(child);
                    } else {
                        BaseParam restored = new BaseParam();
                        cn.hutool.core.bean.BeanUtil.copyProperties(child, restored);
                        fixed.add(restored);
                    }
                }
                param.setChildren(fixed);
            }
        return entity;
    }

    private long effectiveMaxRows(Long configured) {
        if (null != configured && configured > 0) {
            return configured;
        }
        return maxRowsDefault;
    }

    private void publish(DataTaskEvent event) {
        if (null != eventPublisher) {
            try {
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.warn("Failed to publish DataTaskEvent for job {}: {}", event.getJobId(), e.getMessage());
            }
        }
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private static String truncate(String message) {
        String base = StringUtils.isBlank(message) ? "unknown error" : message;
        return base.length() > MESSAGE_LIMIT ? base.substring(0, MESSAGE_LIMIT) : base;
    }

    private static boolean isMySqlConnection(Connection connection) {
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName.contains("MySQL") || productName.contains("MariaDB");
        } catch (Exception e) {
            return false;
        }
    }

    private static Object convertClob(java.sql.Clob clob) {
        try {
            long length = clob.length();
            if (length < 0) {
                return null;
            }
            return clob.getSubString(1, (int) Math.min(length, Integer.MAX_VALUE));
        } catch (Exception e) {
            log.warn("Failed to convert Clob to String: {}", e.getMessage(), e);
            return null;
        }
    }

    private static Object convertBlob(java.sql.Blob blob) {
        try {
            long length = blob.length();
            if (length < 0) {
                return null;
            }
            return blob.getBytes(1, (int) Math.min(length, Integer.MAX_VALUE));
        } catch (Exception e) {
            log.warn("Failed to convert Blob to byte[]: {}", e.getMessage(), e);
            return null;
        }
    }

    private static Object convertArray(java.sql.Array array) {
        try {
            return JsonUtils.toBeanList(array.toString(), Object.class);
        } catch (Exception e) {
            return array;
        }
    }

    private static Object convertStruct(java.sql.Struct struct) {
        try {
            return JsonUtils.toBeanObject(struct.toString(), Map.class);
        } catch (Exception e) {
            return struct;
        }
    }
}
