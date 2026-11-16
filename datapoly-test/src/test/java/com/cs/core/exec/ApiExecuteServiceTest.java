// Use of this source code is governed by a BSD-style license
package com.cs.core.exec;

import cn.hutool.crypto.digest.DigestUtil;
import com.cs.cache.CacheFactory;
import com.cs.cache.DistributedCache;
import com.cs.common.consts.Constants;
import com.cs.common.dto.ItemParam;
import com.cs.common.dto.ResultEntity;
import com.cs.common.enums.*;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.driver.DriverLoadService;
import com.cs.core.exec.engine.ApiExecutorEngine;
import com.cs.core.exec.engine.ApiExecutorEngineFactory;
import com.cs.core.exec.extractor.FormHttpRequestBodyExtractor;
import com.cs.core.exec.extractor.JsonHttpRequestBodyExtractor;
import com.cs.core.exec.logger.RequestParamLogger;
import com.cs.core.util.DataSourceUtils;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.cs.persistence.entity.ApiContextEntity;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.util.JsonUtils;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.*;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * Main request execution flow of {@link ApiExecuteService} without a Spring container:
 * request parameter merging (header/query/body locations, arrays, objects, defaults,
 * required-missing rejection), the response cache (AUTO/SpEL keys, hit/miss, commitId
 * versioning, hit skips the datasource), datasource resolution errors, ${} policy
 * propagation to the engine, and body extraction dispatch by content type. Collaborators
 * are hand-written fakes; the engine is swapped through the factory's engine map and the
 * H2 driver directory stands in for a JDBC driver directory (same style as the other
 * centralized tests in this module).
 */
public class ApiExecuteServiceTest {

    // ------------------------------------------------------------------ fakes

    /** In-memory distributed cache with JSON round-trips like the real redis one. */
    static class FakeDistributedCache implements DistributedCache {
        final Map<String, Object> store = new LinkedHashMap<>();
        final Map<String, Long> expires = new LinkedHashMap<>();
        final Map<String, TimeUnit> units = new LinkedHashMap<>();
        final AtomicInteger hits = new AtomicInteger();

        @Override
        public String getName() {
            return "fake";
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            Object value = store.get(key);
            if (null == value) {
                return null;
            }
            hits.incrementAndGet();
            return (T) value;
        }

        @Override
        public void put(String key, Object value, long expire, TimeUnit unit) {
            store.put(key, value);
            expires.put(key, expire);
            units.put(key, unit);
        }

        @Override
        public void evict(String key) {
            store.remove(key);
            expires.remove(key);
            units.remove(key);
        }
    }

    static class FakeCacheFactory implements CacheFactory {
        final Map<String, FakeDistributedCache> caches = new LinkedHashMap<>();

        @Override
        public <T> Map<String, T> getCacheMap(String key, Class<T> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DistributedCache getDistributedCache(String name) {
            return caches.computeIfAbsent(name, n -> new FakeDistributedCache());
        }
    }

    /** Counts getById calls and serves a configurable entity (null = not found). */
    static class RecordingDataSourceDao extends DataSourceDao {
        final AtomicInteger getByIdCalls = new AtomicInteger();
        volatile DataSourceEntity entity;

        RecordingDataSourceDao(DataSourceEntity entity) {
            this.entity = entity;
        }

        @Override
        public DataSourceEntity getById(Long id) {
            getByIdCalls.incrementAndGet();
            return entity;
        }
    }

    /** Replaces the SQL engine in the factory's engine map; records what doExecute passed down. */
    static class RecordingEngine implements ApiExecutorEngine {
        final List<Map<String, Object>> paramCalls = new ArrayList<>();
        volatile NamingStrategyEnum strategy;
        volatile Boolean dollarAllowed;
        volatile List<Object> response = Collections.singletonList("from-engine");

        @Override
        public void setPrintSqlLog(boolean printSqlLog) {
            // not asserted
        }

        @Override
        public List<Object> execute(List<ApiContextEntity> scripts, Map<String, Object> params,
                                    NamingStrategyEnum strategy, boolean dollarSubstitutionAllowed) {
            paramCalls.add(new LinkedHashMap<>(params));
            this.strategy = strategy;
            this.dollarAllowed = dollarSubstitutionAllowed;
            return response;
        }
    }

    static class StubServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream buffer;

        StubServletInputStream(byte[] bytes) {
            this.buffer = new ByteArrayInputStream(bytes);
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener readListener) {
            // no async support needed
        }

        @Override
        public int read() {
            return buffer.read();
        }
    }

    // ------------------------------------------------------------------ fixtures

    private static final long DS_ID = 91002L;
    private static final List<Long> USED_DS_IDS = new ArrayList<>();

    private ApiExecuteService service;
    private FakeCacheFactory cacheFactory;
    private RecordingDataSourceDao dao;
    private ExecutorMetadataCache metadataCache;
    private RecordingEngine recordingEngine;
    private Map<ExecuteEngineEnum, BiFunction<HikariDataSource, ProductTypeEnum, ApiExecutorEngine>> originalEngineMap;
    private boolean engineSwapped;

    @Before
    public void setUp() throws Exception {
        service = new ApiExecuteService();
        cacheFactory = new FakeCacheFactory();
        dao = new RecordingDataSourceDao(dataSourceEntity());
        metadataCache = new ExecutorMetadataCache();
        metadataCache.init();

        DriverLoadService driverLoadService = new DriverLoadService();
        Map<ProductTypeEnum, Map<String, File>> drivers = new HashMap<>();
        drivers.put(ProductTypeEnum.MYSQL, Collections.singletonMap("test", h2DriverDir()));
        injectField(driverLoadService, "drivers", drivers);

        injectField(service, "cacheFactory", cacheFactory);
        injectField(service, "dataSourceDao", dao);
        injectField(service, "executorMetadataCache", metadataCache);
        injectField(service, "driverLoadService", driverLoadService);
        injectField(service, "requestBodyExtractors", Arrays.asList(
                new JsonHttpRequestBodyExtractor(), new FormHttpRequestBodyExtractor()));
        // DataSourceUtils.decrypt asks the Spring Environment whether credentials are AES-encrypted:
        // stub it (returns no properties, so encryption stays off) — no Spring container in this module's tests
        installStubEnvironment();
    }

    @After
    public void tearDown() throws Exception {
        RequestParamLogger.getAndClear();
        clearSpringUtil();
        if (engineSwapped) {
            Field field = ApiExecutorEngineFactory.class.getDeclaredField("engineMap");
            field.setAccessible(true);
            field.set(null, originalEngineMap);
        }
        for (Long id : USED_DS_IDS) {
            DataSourceUtils.dropHikariDataSource(id);
        }
        USED_DS_IDS.clear();
    }

    private static void installStubEnvironment() throws Exception {
        Environment environment = (Environment) Proxy.newProxyInstance(
                ApiExecuteServiceTest.class.getClassLoader(), new Class[]{Environment.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getProperty".equals(name) && null != args && args.length > 1) {
                        return args[1];
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(name)) {
                        return "stubEnvironment";
                    }
                    return null;
                });
        Object beanFactory = Proxy.newProxyInstance(ApiExecuteServiceTest.class.getClassLoader(),
                new Class[]{ConfigurableListableBeanFactory.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getBean".equals(name) && null != args && 1 == args.length && args[0] instanceof Class) {
                        return Environment.class.isAssignableFrom((Class<?>) args[0]) ? environment : null;
                    }
                    if ("getBeansOfType".equals(name)) {
                        return Collections.emptyMap();
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(name)) {
                        return "stubBeanFactory";
                    }
                    return null;
                });
        Field field = cn.hutool.extra.spring.SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
    }

    private static void clearSpringUtil() throws Exception {
        Field field = cn.hutool.extra.spring.SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    private static void injectField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (null != type) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new IllegalStateException("no field " + name);
    }

    private static DataSourceEntity dataSourceEntity() {
        USED_DS_IDS.add(DS_ID);
        return DataSourceEntity.builder()
                .id(DS_ID)
                .name("h2")
                .type(ProductTypeEnum.MYSQL)
                .version("test")
                .driver("org.h2.Driver")
                .url("jdbc:h2:mem:apiExecuteDb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .build();
    }

    private static File h2DriverDir() throws Exception {
        File jar = new File(org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Assert.assertTrue("h2 must be on the test classpath as a jar: " + jar, jar.isFile());
        return jar.getParentFile();
    }

    private void useRecordingEngine() throws Exception {
        recordingEngine = new RecordingEngine();
        Field field = ApiExecutorEngineFactory.class.getDeclaredField("engineMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ExecuteEngineEnum, BiFunction<HikariDataSource, ProductTypeEnum, ApiExecutorEngine>> original =
                (Map<ExecuteEngineEnum, BiFunction<HikariDataSource, ProductTypeEnum, ApiExecutorEngine>>) field.get(null);
        originalEngineMap = original;
        Map<ExecuteEngineEnum, BiFunction<HikariDataSource, ProductTypeEnum, ApiExecutorEngine>> replacement =
                new HashMap<>(original);
        replacement.put(ExecuteEngineEnum.SQL, (ds, type) -> recordingEngine);
        field.set(null, replacement);
        engineSwapped = true;
    }

    private static ItemParam param(String name, ParamTypeEnum type, ParamLocationEnum location,
                                   boolean isArray, boolean required, String defaultValue) {
        ItemParam item = new ItemParam();
        item.setName(name);
        item.setType(type);
        item.setLocation(location);
        item.setIsArray(isArray);
        item.setRequired(required);
        item.setDefaultValue(defaultValue);
        return item;
    }

    private ApiAssignmentEntity config(CacheKeyTypeEnum cacheKeyType) {
        return ApiAssignmentEntity.builder()
                .id(1L)
                .datasourceId(DS_ID)
                .method(HttpMethodEnum.POST)
                .path("x")
                .engine(ExecuteEngineEnum.SQL)
                .cacheKeyType(cacheKeyType)
                .cacheExpireSeconds(120L)
                .open(false)
                .commitId(5L)
                .namingStrategy(NamingStrategyEnum.CAMEL_CASE)
                .contextList(Collections.singletonList(new ApiContextEntity("select 1")))
                .build();
    }

    private static HttpServletRequest request(String method, String contentType, byte[] body,
                                              Map<String, String[]> queryParams, Map<String, String[]> headers,
                                              boolean failBodyRead) {
        return (HttpServletRequest) Proxy.newProxyInstance(ApiExecuteServiceTest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class}, (proxy, proxyMethod, args) -> {
                    String name = proxyMethod.getName();
                    if ("getMethod".equals(name)) {
                        return method;
                    }
                    if ("getContentType".equals(name)) {
                        return contentType;
                    }
                    if ("getInputStream".equals(name)) {
                        if (failBodyRead) {
                            throw new IOException("broken body stream");
                        }
                        return new StubServletInputStream(null == body ? new byte[0] : body);
                    }
                    if ("getParameterValues".equals(name)) {
                        return queryParams.get((String) args[0]);
                    }
                    if ("getParameter".equals(name)) {
                        String[] values = queryParams.get((String) args[0]);
                        return (null == values || 0 == values.length) ? null : values[0];
                    }
                    if ("getHeaders".equals(name)) {
                        String[] values = headers.get((String) args[0]);
                        return Collections.enumeration(null == values ? Collections.emptyList() : Arrays.asList(values));
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(name)) {
                        return "stubHttpServletRequest";
                    }
                    return null;
                });
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ cache key (A5)

    @Test
    public void cacheKeyChangesWithCommitId() {
        String v11 = ApiExecuteService.buildCacheKey("GET:/api/user", 11L, "abc");
        String v12 = ApiExecuteService.buildCacheKey("GET:/api/user", 12L, "abc");
        Assert.assertEquals("GET:/api/user:11:abc", v11);
        Assert.assertNotEquals(v11, v12);
    }

    @Test
    public void cacheKeyStableForSameCommit() {
        Assert.assertEquals(
                ApiExecuteService.buildCacheKey("GET:/api/user", 11L, "abc"),
                ApiExecuteService.buildCacheKey("GET:/api/user", 11L, "abc"));
    }

    @Test
    public void cacheKeyDefensiveForNullCommitId() {
        // Falls back to 0 on non-online paths (no commitId overlay), without throwing
        Assert.assertEquals("GET:/api/user:0:abc", ApiExecuteService.buildCacheKey("GET:/api/user", null, "abc"));
    }

    // ------------------------------------------------------------------ execution flow

    @Test
    public void noCacheConfigDelegatesToEngineWithMergedParams() throws Exception {
        useRecordingEngine();
        Map<String, Object> paramValues = new HashMap<>();
        paramValues.put("q", "v");
        ResultEntity<Object> result = service.execute(config(CacheKeyTypeEnum.NONE), paramValues, false);
        Assert.assertEquals(Integer.valueOf(ResponseErrorCode.SUCCESS.getCode()), result.getCode());
        Assert.assertEquals("from-engine", result.getData());
        Assert.assertEquals(1, recordingEngine.paramCalls.size());
        Assert.assertEquals("v", recordingEngine.paramCalls.get(0).get("q"));
        Assert.assertEquals(NamingStrategyEnum.CAMEL_CASE, recordingEngine.strategy);
    }

    @Test
    public void openApiForbidsDollarSubstitutionForEngine() throws Exception {
        useRecordingEngine();
        ApiAssignmentEntity openConfig = config(CacheKeyTypeEnum.NONE);
        openConfig.setOpen(true);
        service.execute(openConfig, Collections.emptyMap(), false);
        Assert.assertEquals("open=true public API must pass dollarAllowed=false",
                Boolean.FALSE, recordingEngine.dollarAllowed);
        ApiAssignmentEntity closedConfig = config(CacheKeyTypeEnum.NONE);
        closedConfig.setOpen(false);
        service.execute(closedConfig, Collections.emptyMap(), false);
        Assert.assertEquals("non-public API keeps ${} enabled by default",
                Boolean.TRUE, recordingEngine.dollarAllowed);
    }

    @Test
    public void multipleEngineResultsAreReturnedAsListSingleUnwrapped() throws Exception {
        useRecordingEngine();
        recordingEngine.response = Arrays.asList("a", "b");
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.NONE);
        Assert.assertEquals(Arrays.asList("a", "b"), service.execute(config, Collections.emptyMap(), false).getData());
        recordingEngine.response = Collections.singletonList("only");
        Assert.assertEquals("only", service.execute(config, Collections.emptyMap(), false).getData());
    }

    @Test
    public void datasourceNotFoundRejectedWithoutEngineInvocation() throws Exception {
        useRecordingEngine();
        dao.entity = null;
        try {
            service.execute(config(CacheKeyTypeEnum.NONE), Collections.emptyMap(), false);
            Assert.fail("missing datasource must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
        Assert.assertEquals(0, recordingEngine.paramCalls.size());
    }

    @Test
    public void executeEndToEndWithRealSqlEngineOverH2() throws Exception {
        // real engine: the factory creates a genuine SqlExecutorService on the pooled H2 connection
        try (Connection connection = ((HikariDataSource) poolOf(dataSourceEntity())).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_probe");
            statement.execute("CREATE TABLE t_probe (id INT PRIMARY KEY, name VARCHAR(64))");
            statement.execute("INSERT INTO t_probe(id, name) VALUES (1, 'alice')");
        }
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.NONE);
        config.setContextList(Collections.singletonList(
                new ApiContextEntity("SELECT name FROM t_probe WHERE id = #{pid}")));
        Map<String, Object> params = new HashMap<>();
        params.put("pid", 1);
        ResultEntity<Object> result = service.execute(config, params, true);
        Assert.assertEquals(Integer.valueOf(ResponseErrorCode.SUCCESS.getCode()), result.getCode());
        List<?> rows = (List<?>) result.getData();
        Assert.assertEquals(1, rows.size());
        Assert.assertEquals("alice", ((Map<?, ?>) rows.get(0)).get("name"));
    }

    private static HikariDataSource poolOf(DataSourceEntity entity) {
        try {
            File driverDir = h2DriverDir();
            return DataSourceUtils.getHikariDataSource(entity, driverDir.getAbsolutePath());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ response cache

    @Test
    public void autoCacheKeyMissesThenHits() throws Exception {
        useRecordingEngine();
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.AUTO);
        Map<String, Object> params = new HashMap<>();
        params.put("uid", 7L);
        ResultEntity<Object> first = service.execute(config, params, false);
        Assert.assertEquals("from-engine", first.getData());

        FakeDistributedCache cache = cacheFactory.caches.get(Constants.CACHE_NAME_API_RESPONSE);
        Assert.assertNotNull(cache);
        String rawKey = DigestUtil.sha256Hex(JsonUtils.toJsonString(new TreeMap<>(params)));
        String expectedKey = ApiExecuteService.buildCacheKey(
                Constants.getResourceName(config.getMethod().name(), config.getPath()),
                config.getCommitId(), rawKey);
        Assert.assertTrue("cache must be primed with the versioned key: " + cache.store.keySet(),
                cache.store.containsKey(expectedKey));
        Assert.assertEquals(Long.valueOf(120L), cache.expires.get(expectedKey));
        Assert.assertEquals(TimeUnit.SECONDS, cache.units.get(expectedKey));

        recordingEngine.response = Collections.singletonList("second-engine-run");
        ResultEntity<Object> second = service.execute(config, params, false);
        Assert.assertEquals("cached response must be returned, engine must not run again",
                "from-engine", second.getData());
        Assert.assertEquals(1, recordingEngine.paramCalls.size());
        Assert.assertEquals(1, cache.hits.get());
    }

    @Test
    public void spelCacheKeyUsesExpressionResult() throws Exception {
        useRecordingEngine();
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.SPEL);
        config.setCacheKeyExpr("#uid");
        Map<String, Object> params = new HashMap<>();
        params.put("uid", 42L);
        service.execute(config, params, false);
        FakeDistributedCache cache = cacheFactory.caches.get(Constants.CACHE_NAME_API_RESPONSE);
        Assert.assertEquals(1, cache.store.size());
        String key = cache.store.keySet().iterator().next();
        Assert.assertTrue("SpEL key must embed the evaluated value: " + key, key.endsWith(":42"));
    }

    @Test
    public void cacheHitSkipsDatasourceAndEngine() throws Exception {
        useRecordingEngine();
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.AUTO);
        Map<String, Object> params = new HashMap<>();
        params.put("uid", 1L);
        String rawKey = DigestUtil.sha256Hex(JsonUtils.toJsonString(new TreeMap<>(params)));
        String key = ApiExecuteService.buildCacheKey(
                Constants.getResourceName(config.getMethod().name(), config.getPath()),
                config.getCommitId(), rawKey);
        FakeDistributedCache cache = (FakeDistributedCache) cacheFactory.getDistributedCache(Constants.CACHE_NAME_API_RESPONSE);
        cache.put(key, ResultEntity.success("pre-cached"), 60L, TimeUnit.SECONDS);
        ResultEntity<Object> result = service.execute(config, params, false);
        Assert.assertEquals("pre-cached", result.getData());
        Assert.assertEquals("datasource must not be touched on a hit", 0, dao.getByIdCalls.get());
        Assert.assertEquals("engine must not run on a hit", 0, recordingEngine.paramCalls.size());
    }

    // ------------------------------------------------------------------ request parameter merging

    @Test
    public void mergedParametersCoverHeaderQueryAndBodyLocations() throws Exception {
        useRecordingEngine();
        List<ItemParam> params = new ArrayList<>();
        params.add(param("h1", ParamTypeEnum.LONG, ParamLocationEnum.REQUEST_HEADER, false, true, null));
        params.add(param("tags", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_HEADER, true, false, null));
        params.add(param("h3", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_HEADER, false, false, "dh3"));
        params.add(param("num", ParamTypeEnum.LONG, ParamLocationEnum.REQUEST_BODY, false, true, null));
        params.add(param("names", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_BODY, true, false, null));
        params.add(param("obj", ParamTypeEnum.OBJECT, ParamLocationEnum.REQUEST_BODY, false, true, null));
        params.add(param("obj2", ParamTypeEnum.OBJECT, ParamLocationEnum.REQUEST_BODY, false, false, null));
        params.add(param("objs", ParamTypeEnum.OBJECT, ParamLocationEnum.REQUEST_BODY, true, false, null));
        params.add(param("optArr", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_BODY, true, false, null));
        params.add(param("q1", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_FORM, false, true, null));
        params.add(param("qa", ParamTypeEnum.LONG, ParamLocationEnum.REQUEST_FORM, true, false, null));
        params.add(param("q2", ParamTypeEnum.LONG, ParamLocationEnum.REQUEST_FORM, false, false, "7"));
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.NONE);
        config.setParams(params);

        Map<String, String[]> headers = new HashMap<>();
        headers.put("h1", new String[]{"5"});
        headers.put("tags", new String[]{"a", "b"});
        Map<String, String[]> query = new HashMap<>();
        query.put("q1", new String[]{"v"});
        query.put("qa", new String[]{"1", "2"});
        String body = "{\"num\": 5, \"names\": [\"x\", \"y\"], \"obj\": {\"k\": \"v\"}, "
                + "\"obj2\": \"scalar\", \"objs\": [{\"a\": 1}, {\"b\": 2}]}";
        HttpServletRequest request = request("POST", "application/json", utf8(body), query, headers, false);

        ResultEntity<Object> result = service.execute(config, request, false);
        Assert.assertEquals(Integer.valueOf(ResponseErrorCode.SUCCESS.getCode()), result.getCode());
        Map<String, Object> merged = recordingEngine.paramCalls.get(0);

        Assert.assertEquals(Long.valueOf(5L), merged.get("h1"));                // header single, converted
        Assert.assertEquals(Arrays.asList("a", "b"), merged.get("tags"));       // header array
        Assert.assertEquals("dh3", merged.get("h3"));                           // header optional default
        Assert.assertEquals(Long.valueOf(5L), merged.get("num"));               // body single number
        Assert.assertEquals(Arrays.asList("x", "y"), merged.get("names"));      // body string array
        Map<?, ?> obj = (Map<?, ?>) merged.get("obj");                          // body object stays a map
        Assert.assertEquals("v", obj.get("k"));
        Assert.assertEquals(Collections.emptyMap(), merged.get("obj2"));        // object from scalar -> empty map
        List<?> objs = (List<?>) merged.get("objs");                            // object array passed as list
        Assert.assertEquals(2, objs.size());
        Assert.assertFalse("missing optional body array is simply absent", merged.containsKey("optArr"));
        Assert.assertEquals("v", merged.get("q1"));                             // query single
        Assert.assertEquals(Arrays.asList(1L, 2L), merged.get("qa"));           // query array converted
        Assert.assertEquals(Long.valueOf(7L), merged.get("q2"));                // query optional default
    }

    @Test
    public void bodyScalarForArrayParamBecomesSingleElementList() throws Exception {
        useRecordingEngine();
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.NONE);
        config.setParams(Collections.singletonList(
                param("names", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_BODY, true, false, null)));
        HttpServletRequest request = request("POST", "application/json", utf8("{\"names\": \"solo\"}"),
                Collections.emptyMap(), Collections.emptyMap(), false);
        service.execute(config, request, false);
        Assert.assertEquals(Collections.singletonList("solo"), recordingEngine.paramCalls.get(0).get("names"));
    }

    @Test
    public void bodyListForSingleParamTakesFirstElement() throws Exception {
        useRecordingEngine();
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.NONE);
        config.setParams(Collections.singletonList(
                param("num", ParamTypeEnum.LONG, ParamLocationEnum.REQUEST_BODY, false, true, null)));
        HttpServletRequest request = request("POST", "application/json", utf8("{\"num\": [9, 8]}"),
                Collections.emptyMap(), Collections.emptyMap(), false);
        service.execute(config, request, false);
        Assert.assertEquals(Long.valueOf(9L), recordingEngine.paramCalls.get(0).get("num"));
    }

    @Test
    public void missingRequiredParametersAreRejectedPerLocation() throws Exception {
        useRecordingEngine();
        assertRequiredRejected(param("rh", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_HEADER, false, true, null),
                "POST", "application/json", Collections.emptyMap(), Collections.emptyMap());
        assertRequiredRejected(param("rha", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_HEADER, true, true, null),
                "POST", "application/json", Collections.emptyMap(), Collections.emptyMap());
        assertRequiredRejected(param("rb", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_BODY, false, true, null),
                "POST", "application/json", Collections.emptyMap(), Collections.emptyMap());
        assertRequiredRejected(param("rq", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_FORM, false, true, null),
                "POST", null, Collections.emptyMap(), Collections.emptyMap());
        assertRequiredRejected(param("rqa", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_FORM, true, true, null),
                "POST", null, Collections.emptyMap(), Collections.emptyMap());
        // a GET request has no body: body params of GET APIs are declared as required and fail the same way
        assertRequiredRejected(param("rb", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_BODY, false, true, null),
                "GET", "application/json", Collections.emptyMap(), Collections.emptyMap());
        Assert.assertEquals("engine must never run for invalid arguments",
                0, recordingEngine.paramCalls.size());
    }

    @Test
    public void optionalMissingBodyScalarAppliesConverterOnDefault() throws Exception {
        useRecordingEngine();
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.NONE);
        config.setParams(Collections.singletonList(
                param("bd", ParamTypeEnum.LONG, ParamLocationEnum.REQUEST_BODY, false, false, "42")));
        HttpServletRequest request = request("POST", "application/json", utf8("{}"),
                Collections.emptyMap(), Collections.emptyMap(), false);
        service.execute(config, request, false);
        Assert.assertEquals(Long.valueOf(42L), recordingEngine.paramCalls.get(0).get("bd"));
    }

    private void assertRequiredRejected(ItemParam required, String method, String contentType,
                                        Map<String, String[]> query, Map<String, String[]> headers) {
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.NONE);
        config.setParams(Collections.singletonList(required));
        try {
            service.execute(config, request(method, contentType, utf8("{}"), query, headers, false), false);
            Assert.fail("missing required param " + required.getName() + " must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void noDeclaredParamsYieldEmptyMerge() throws Exception {
        useRecordingEngine();
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.NONE);
        config.setParams(Collections.emptyList());
        HttpServletRequest request = request("POST", "application/json", utf8("{\"ignored\": 1}"),
                Collections.emptyMap(), Collections.emptyMap(), false);
        service.execute(config, request, false);
        Assert.assertEquals(Collections.emptyMap(), recordingEngine.paramCalls.get(0));
    }

    @Test
    public void brokenBodyStreamMapsToInternalError() throws Exception {
        useRecordingEngine();
        ApiAssignmentEntity config = config(CacheKeyTypeEnum.NONE);
        config.setParams(Collections.singletonList(
                param("b", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_BODY, false, true, null)));
        HttpServletRequest request = request("POST", "application/json", utf8("{}"),
                Collections.emptyMap(), Collections.emptyMap(), true);
        try {
            service.execute(config, request, false);
            Assert.fail("body read failure must surface as internal error");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INTERNAL_ERROR, e.getCode());
        }
    }

    // ------------------------------------------------------------------ body extraction

    @Test
    public void requestBodyMapDispatchesByContentType() throws Exception {
        // no body methods never read the stream
        Assert.assertEquals(Collections.emptyMap(),
                service.getRequestBodyMap(request("GET", "application/json", utf8("{\"a\":1}"),
                        Collections.emptyMap(), Collections.emptyMap(), false)));
        // null content type on a body method
        Assert.assertEquals(Collections.emptyMap(),
                service.getRequestBodyMap(request("POST", null, utf8("{\"a\":1}"),
                        Collections.emptyMap(), Collections.emptyMap(), false)));
        // json
        Map<String, Object> json = service.getRequestBodyMap(request("POST", "application/json",
                utf8("{\"a\": 1, \"s\": \"文\"}"), Collections.emptyMap(), Collections.emptyMap(), false));
        Assert.assertEquals(1L, json.get("a"));
        Assert.assertEquals("文", json.get("s"));
        // json with explicit charset
        Map<String, Object> jsonUtf8 = service.getRequestBodyMap(request("POST", "application/json;charset=UTF-8",
                utf8("{\"s\": \"文\"}"), Collections.emptyMap(), Collections.emptyMap(), false));
        Assert.assertEquals("文", jsonUtf8.get("s"));
        // form-urlencoded (comma-separated value becomes a list)
        Map<String, Object> form = service.getRequestBodyMap(request("POST",
                "application/x-www-form-urlencoded", utf8("a=1&tags=x,y"),
                Collections.emptyMap(), Collections.emptyMap(), false));
        Assert.assertEquals(Collections.singletonList("1"), form.get("a"));
        Assert.assertEquals(Arrays.asList("x", "y"), form.get("tags"));
        // unsupported content type: no extractor claims it
        Assert.assertEquals(Collections.emptyMap(),
                service.getRequestBodyMap(request("POST", "text/csv", utf8("a,b"),
                        Collections.emptyMap(), Collections.emptyMap(), false)));
    }

    @Test
    public void jsonScalarBodyYieldsEmptyMap() throws Exception {
        // the JSON extractor only expands container nodes
        Assert.assertEquals(Collections.emptyMap(),
                service.getRequestBodyMap(request("POST", "application/json", utf8("42"),
                        Collections.emptyMap(), Collections.emptyMap(), false)));
    }
}
