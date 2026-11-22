// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.dto.PageResult;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.dto.*;
import com.cs.core.driver.DriverLoadService;
import com.cs.core.util.DataSourceUtils;
import com.cs.core.util.ParamMaskUtils;
import com.cs.persistence.dao.ApiAssignmentDao;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.entity.DataSourceEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link DataSourceService} without a Spring context: the DAO layer, the driver
 * registry and the hutool {@link SpringUtil} bean factory are replaced with hand-written fakes.
 * Real JDBC connections come from a fake {@link Driver} packaged into a runtime-built jar (the
 * jar classloader's parent cannot see the outer test class, so the fake must be self-contained).
 * The fake is steered through system properties ("dp.fake.*"), which both sides of the
 * classloader boundary can see; it records observed arguments back the same way.
 */
public class DataSourceServiceTest {

    // ---------------------------------------------------------------------
    // Self-contained fake JDBC driver (all referenced types are JDK or siblings)
    // ---------------------------------------------------------------------

    /** Driver answering from system properties; see {@link #setFake(String, String)}. */
    public static class FakeServiceDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if ("true".equals(System.getProperty("dp.fake.connect.fail"))) {
                throw new SQLException("dp.fake connect refused");
            }
            return FakeJdbcObjects.connection();
        }

        @Override
        public boolean acceptsURL(String url) {
            return true;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }
    }

    /** Proxy factory shared by the fake driver: connections, statements, metadata and result sets. */
    public static class FakeJdbcObjects {

        static Connection connection() {
            return (Connection) Proxy.newProxyInstance(FakeJdbcObjects.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        if ("toString".equals(name)) {
                            return "fake-connection";
                        }
                        if ("createStatement".equals(name)) {
                            return statement();
                        }
                        if ("getMetaData".equals(name)) {
                            if ("true".equals(System.getProperty("dp.fake.meta.fail"))) {
                                throw new SQLException("dp.fake metadata unavailable");
                            }
                            return metaData();
                        }
                        if ("getCatalog".equals(name)) {
                            String catalog = System.getProperty("dp.fake.catalog");
                            return null == catalog ? "CAT" : catalog;
                        }
                        if ("isValid".equals(name)) {
                            return !"true".equals(System.getProperty("dp.fake.invalid"));
                        }
                        if ("isClosed".equals(name)) {
                            return false;
                        }
                        if ("getAutoCommit".equals(name)) {
                            return true;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        static Statement statement() {
            return (Statement) Proxy.newProxyInstance(FakeJdbcObjects.class.getClassLoader(),
                    new Class<?>[]{Statement.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        switch (name) {
                            case "setQueryTimeout":
                                System.setProperty("dp.fake.saw.queryTimeout", String.valueOf(args[0]));
                                return null;
                            case "execute":
                                System.setProperty("dp.fake.saw.sql", String.valueOf(args[0]));
                                return true;
                            case "executeQuery":
                                System.setProperty("dp.fake.saw.query", String.valueOf(args[0]));
                                return singleColumn(prop("dp.fake.schemas"));
                            case "close":
                                return null;
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    });
        }

        static DatabaseMetaData metaData() {
            return (DatabaseMetaData) Proxy.newProxyInstance(FakeJdbcObjects.class.getClassLoader(),
                    new Class<?>[]{DatabaseMetaData.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        switch (name) {
                            case "getDatabaseProductName":
                                return "FakeDB";
                            case "getCatalogs":
                                if ("true".equals(System.getProperty("dp.fake.null.catalogs"))) {
                                    return null; // some drivers return null instead of an empty set
                                }
                                return rows(prop("dp.fake.catalogs"), "TABLE_CAT");
                            case "getSchemas":
                                if ("true".equals(System.getProperty("dp.fake.null.schemas"))) {
                                    return null;
                                }
                                return rows(prop("dp.fake.schemas"), "TABLE_SCHEM");
                            case "getTables": {
                                boolean view = null != args[3]
                                        && Arrays.asList((String[]) args[3]).contains("VIEW");
                                System.setProperty("dp.fake.saw.tables.catalog", String.valueOf(args[0]));
                                System.setProperty("dp.fake.saw.tables.schema", String.valueOf(args[1]));
                                System.setProperty("dp.fake.saw.tables.types", view ? "VIEW" : "TABLE");
                                return rows(view ? prop("dp.fake.views") : prop("dp.fake.tables"), "TABLE_NAME");
                            }
                            case "getColumns": {
                                System.setProperty("dp.fake.saw.columns.table", String.valueOf(args[2]));
                                List<Map<String, String>> result = new ArrayList<>();
                                String rawSpecs = System.getProperty("dp.fake.columns");
                                List<String> specs = (null == rawSpecs || rawSpecs.isEmpty())
                                        ? Collections.emptyList()
                                        : Arrays.asList(rawSpecs.split(";"));
                                for (String spec : specs) {
                                    String[] parts = spec.split("\\|", -1);
                                    Map<String, String> row = new HashMap<>();
                                    row.put("1", parts[0]);
                                    row.put("2", parts.length > 1 ? parts[1] : "");
                                    row.put("3", parts.length > 2 ? parts[2] : "");
                                    row.put("COLUMN_NAME", parts[0]);
                                    row.put("TYPE_NAME", parts.length > 1 ? parts[1] : "");
                                    row.put("REMARKS", parts.length > 2 ? parts[2] : "");
                                    result.add(row);
                                }
                                return proxy(result);
                            }
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    });
        }

        /** Single-column result set (index 1) used for schema list queries. */
        static ResultSet singleColumn(List<String> values) {
            List<Map<String, String>> data = new ArrayList<>();
            for (String value : values) {
                Map<String, String> row = new HashMap<>();
                row.put("1", value);
                data.add(row);
            }
            return proxy(data);
        }

        static ResultSet rows(List<String> values, String label) {
            List<Map<String, String>> data = new ArrayList<>();
            for (String value : values) {
                Map<String, String> row = new HashMap<>();
                row.put("1", value);
                row.put(label, value);
                data.add(row);
            }
            return proxy(data);
        }

        static ResultSet proxy(List<Map<String, String>> data) {
            Iterator<Map<String, String>> iterator = data.iterator();
            Map<String, String>[] current = new Map[]{null};
            return (ResultSet) Proxy.newProxyInstance(FakeJdbcObjects.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        switch (name) {
                            case "next":
                                if (iterator.hasNext()) {
                                    current[0] = iterator.next();
                                    return true;
                                }
                                return false;
                            case "getString":
                                return null == current[0] ? null
                                        : current[0].get(String.valueOf(args[0]));
                            case "close":
                                return null;
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    });
        }

        static List<String> prop(String key) {
            String value = System.getProperty(key);
            if (null == value || value.isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(Arrays.asList(value.split(",")));
        }

        static Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Fakes for the DAO layer
    // ---------------------------------------------------------------------

    private static class FakeDataSourceDao extends DataSourceDao {
        final List<DataSourceEntity> rows = new ArrayList<>();
        final List<DataSourceEntity> inserted = new ArrayList<>();
        final List<DataSourceEntity> updated = new ArrayList<>();
        final List<Long> deleted = new ArrayList<>();
        final AtomicInteger idGen = new AtomicInteger(1000);

        @Override
        public void insert(DataSourceEntity entity) {
            if (null == entity.getId()) {
                entity.setId((long) idGen.incrementAndGet());
            }
            rows.add(entity);
            inserted.add(entity);
        }

        @Override
        public DataSourceEntity getById(Long id) {
            for (DataSourceEntity row : rows) {
                if (row.getId().equals(id)) {
                    return row;
                }
            }
            return null;
        }

        @Override
        public DataSourceEntity getByName(String name) {
            for (DataSourceEntity row : rows) {
                if (row.getName().equals(name)) {
                    return row;
                }
            }
            return null;
        }

        @Override
        public List<DataSourceEntity> listAll(String searchText) {
            List<DataSourceEntity> result = new ArrayList<>();
            for (DataSourceEntity row : rows) {
                if (null == searchText || row.getName().contains(searchText)) {
                    result.add(row);
                }
            }
            return result;
        }

        @Override
        public void updateById(DataSourceEntity entity) {
            rows.removeIf(row -> row.getId().equals(entity.getId()));
            rows.add(entity);
            updated.add(entity);
        }

        @Override
        public void deleteById(Long id) {
            rows.removeIf(row -> row.getId().equals(id));
            deleted.add(id);
        }
    }

    // ---------------------------------------------------------------------
    // Test fixture
    // ---------------------------------------------------------------------

    private static final String DRIVER_VERSION = "v1";
    private static final String[] FAKE_CLASSES = {"FakeServiceDriver", "FakeJdbcObjects"};

    private DataSourceService service;
    private FakeDataSourceDao dao;
    private DriverLoadService driverLoadService;
    private final AtomicReference<Boolean> assignmentUsesDatasource = new AtomicReference<>(false);
    private final AtomicReference<Boolean> onlineUsesDatasource = new AtomicReference<>(false);
    private String driverDir;

    @Before
    public void setUp() throws Exception {
        dao = new FakeDataSourceDao();
        driverLoadService = new DriverLoadService();
        driverDir = writeDriverJar();
        // the drivers registry is package-visible in com.cs.core.driver; this test lives with the
        // service under test, so the registry is seeded reflectively instead
        Map<String, File> versions = new HashMap<>();
        versions.put(DRIVER_VERSION, new File(driverDir));
        Field driversField = DriverLoadService.class.getDeclaredField("drivers");
        driversField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ProductTypeEnum, Map<String, File>> drivers =
                (Map<ProductTypeEnum, Map<String, File>>) driversField.get(driverLoadService);
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            drivers.put(type, versions);
        }

        service = new DataSourceService();
        setField(service, "dataSourceDao", dao);
        installSpringUtil();
    }

    @After
    public void tearDown() throws Exception {
        uninstallSpringUtil();
        for (Long id : new ArrayList<>(DataSourceUtils.getAllDataSourceIdSet())) {
            DataSourceUtils.dropHikariDataSource(id);
        }
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("dp.fake.")) {
                System.clearProperty(key);
            }
        }
    }

    private static void setFake(String key, String value) {
        System.setProperty(key, value);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Reflectively injects a stub beanFactory into hutool SpringUtil (same pattern as DataSourceUtilsTest). */
    private void installSpringUtil() throws Exception {
        Environment environment = (Environment) Proxy.newProxyInstance(
                DataSourceServiceTest.class.getClassLoader(), new Class[]{Environment.class},
                (proxy, method, args) -> {
                    if ("getProperty".equals(method.getName())) {
                        // datasource credential encryption stays off: passwords pass through untouched
                        return null;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "stubEnvironment";
                    }
                    return null;
                });
        Object beanFactory = Proxy.newProxyInstance(DataSourceServiceTest.class.getClassLoader(),
                new Class[]{ConfigurableListableBeanFactory.class}, (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && args != null && args.length >= 1
                            && args[0] instanceof Class) {
                        Class<?> type = (Class<?>) args[0];
                        if (type == DriverLoadService.class) {
                            return driverLoadService;
                        }
                        if (type == ApiAssignmentDao.class) {
                            return assignmentDao();
                        }
                        if (type == ApiOnlineDao.class) {
                            return onlineDao();
                        }
                        return environment;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "stubBeanFactory";
                    }
                    return null;
                });
        Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
    }

    private void uninstallSpringUtil() throws Exception {
        Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    private ApiAssignmentDao assignmentDao() {
        return new ApiAssignmentDao() {
            @Override
            public boolean existsDataSourceById(Long dataSourceId) {
                return Boolean.TRUE.equals(assignmentUsesDatasource.get());
            }
        };
    }

    private ApiOnlineDao onlineDao() {
        return new ApiOnlineDao() {
            @Override
            public boolean existsDataSourceById(Long dataSourceId) {
                return Boolean.TRUE.equals(onlineUsesDatasource.get());
            }
        };
    }

    /** Packs the compiled fake driver classes into a fresh jar under a temp dir. */
    private static String writeDriverJar() throws Exception {
        Path dir = Files.createTempDirectory("ds-service-driver");
        Path jar = dir.resolve("fake-service-driver.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String fakeClass : FAKE_CLASSES) {
                String entry = DataSourceServiceTest.class.getName().replace('.', '/') + "$" + fakeClass + ".class";
                try (InputStream in = DataSourceServiceTest.class.getClassLoader().getResourceAsStream(entry)) {
                    assertNotNull("compiled fake driver class not found: " + entry, in);
                    out.putNextEntry(new JarEntry(entry));
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                    out.closeEntry();
                }
            }
        }
        return dir.toString();
    }

    private DataSourceEntity mysqlRow(long id) {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setId(id);
        entity.setName("ds-" + id);
        entity.setType(ProductTypeEnum.MYSQL);
        entity.setVersion(DRIVER_VERSION);
        entity.setDriver(FakeServiceDriver.class.getName());
        entity.setUrl("jdbc:mysql://fakehost:3306/db" + id);
        entity.setUsername("user");
        entity.setPassword("stored-pass");
        return entity;
    }

    private DataSourceEntity row(long id, ProductTypeEnum type, String url) {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setId(id);
        entity.setName("ds-" + id);
        entity.setType(type);
        entity.setVersion(DRIVER_VERSION);
        entity.setDriver(FakeServiceDriver.class.getName());
        entity.setUrl(url);
        entity.setUsername("user");
        entity.setPassword("stored-pass");
        return entity;
    }

    private DataSourceSaveRequest saveRequest(ProductTypeEnum type, String url) {
        DataSourceSaveRequest request = new DataSourceSaveRequest();
        request.setName("new-ds");
        request.setType(type);
        request.setVersion(DRIVER_VERSION);
        request.setDriver(FakeServiceDriver.class.getName());
        request.setUrl(url);
        request.setUsername("user");
        request.setPassword("plain-pass");
        return request;
    }

    // ---------------------------------------------------------------------
    // getTypes / list / detail
    // ---------------------------------------------------------------------

    @Test
    public void getTypesListsEveryProductType() {
        List<DatabaseTypeDetailResponse> types = service.getTypes();

        assertEquals(ProductTypeEnum.values().length, types.size());
        DatabaseTypeDetailResponse mysql = types.get(0);
        assertEquals(ProductTypeEnum.MYSQL.getId(), mysql.getId().intValue());
        assertEquals("MYSQL", mysql.getType());
        assertEquals(ProductTypeEnum.MYSQL.getDriver(), mysql.getDriver());
        assertEquals(ProductTypeEnum.MYSQL.getSample(), mysql.getSample());
    }

    @Test
    public void searchListMasksPasswordsAndFiltersByName() {
        dao.rows.add(mysqlRow(1));
        dao.rows.add(row(2, ProductTypeEnum.POSTGRESQL, "jdbc:postgresql://h:5432/pg"));

        EntitySearchRequest request = new EntitySearchRequest();
        request.setSearchText("ds-2");
        PageResult<DatasourceDetailResponse> result = service.searchList(request);

        assertEquals(1, result.getData().size());
        assertEquals("ds-2", result.getData().get(0).getName());
        assertEquals(ParamMaskUtils.MASK, result.getData().get(0).getPassword());
        assertEquals("user", result.getData().get(0).getUsername());
        assertNotNull(result.getPagination());
    }

    @Test
    public void getDetailMasksPassword() {
        dao.rows.add(mysqlRow(5));

        DatasourceDetailResponse detail = service.getDetailById(5L);

        assertEquals("ds-5", detail.getName());
        assertEquals(ProductTypeEnum.MYSQL, detail.getType());
        assertEquals(ParamMaskUtils.MASK, detail.getPassword());
    }

    @Test
    public void getDetailForMissingIdThrowsResourceNotExists() {
        try {
            service.getDetailById(404L);
            fail("missing id must fail");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    // ---------------------------------------------------------------------
    // preTest / testDataSource
    // ---------------------------------------------------------------------

    @Test
    public void preTestRunsTheConnectionTestQuery() {
        DataSourceBaseRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3306/test");
        service.preTest(request);
        assertEquals("/* ping */ SELECT 1", System.getProperty("dp.fake.saw.sql"));
    }

    @Test
    public void preTestForOdpForcesARealStatementAndLongTimeout() {
        // the ODPS driver authenticates lazily: the test query is replaced by a real statement with a 120s budget
        DataSourceBaseRequest request = saveRequest(ProductTypeEnum.ODPS,
                "jdbc:odps:http://fakehost/api");
        service.preTest(request);
        assertEquals("SELECT 1 FROM dual", System.getProperty("dp.fake.saw.sql"));
        assertEquals("120", System.getProperty("dp.fake.saw.queryTimeout"));
    }

    @Test
    public void preTestForTypeWithoutTestSqlFallsBackToIsValid() {
        // INCEPTOR has no test sql: the check is Connection.isValid(2), which the fake driver answers true
        service.preTest(saveRequest(ProductTypeEnum.INCEPTOR, "jdbc:hive2://fakehost:10000/default"));
        assertNull(System.getProperty("dp.fake.saw.sql"));
    }

    @Test
    public void preTestConnectFailureSurfacesAsBusinessError() {
        setFake("dp.fake.connect.fail", "true");
        try {
            service.preTest(saveRequest(ProductTypeEnum.MYSQL, "jdbc:mysql://fakehost:3306/test"));
            fail("connection failure must surface");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_CANNOT_CONNECT_REMOTE, e.getCode());
            Throwable cause = e;
            boolean sawDriverMessage = false;
            while (null != cause) {
                if (null != cause.getMessage() && cause.getMessage().contains("dp.fake connect refused")) {
                    sawDriverMessage = true;
                    break;
                }
                cause = cause.getCause();
            }
            assertTrue("the driver message must travel with the error", sawDriverMessage);
        }
    }

    @Test
    public void preTestPassesBusinessExceptionsThroughUntouched() {
        DataSourceBaseRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3306/test");
        request.setVersion("missing-version");
        try {
            service.preTest(request);
            fail("missing driver version must fail");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void testDataSourceReusesTheRowAndRunsTheTestQuery() {
        dao.rows.add(mysqlRow(21));
        service.testDataSource(21L);
        assertEquals("/* ping */ SELECT 1", System.getProperty("dp.fake.saw.sql"));
        assertTrue("the validated pool is cached for later callers",
                DataSourceUtils.getAllDataSourceIdSet().contains(21L));
    }

    @Test
    public void testDataSourceForMissingIdThrowsResourceNotExists() {
        try {
            service.testDataSource(404L);
            fail("missing id must fail");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void testDataSourceConnectFailureSurfacesAsBusinessError() {
        dao.rows.add(mysqlRow(22));
        setFake("dp.fake.connect.fail", "true");
        try {
            service.testDataSource(22L);
            fail("connection failure must surface");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_CANNOT_CONNECT_REMOTE, e.getCode());
        }
    }

    // ---------------------------------------------------------------------
    // create / update / delete
    // ---------------------------------------------------------------------

    @Test
    public void createDataSourcePersistsAllRequestFields() {
        service.createDataSource(saveRequest(ProductTypeEnum.POSTGRESQL,
                "jdbc:postgresql://fakehost:5432/newdb"));

        assertEquals(1, dao.inserted.size());
        DataSourceEntity stored = dao.inserted.get(0);
        assertEquals("new-ds", stored.getName());
        assertEquals(ProductTypeEnum.POSTGRESQL, stored.getType());
        assertEquals(DRIVER_VERSION, stored.getVersion());
        assertEquals("jdbc:postgresql://fakehost:5432/newdb", stored.getUrl());
        assertEquals("user", stored.getUsername());
        // credential encryption is off in tests: the password stays as submitted
        assertEquals("plain-pass", stored.getPassword());
    }

    @Test
    public void createDataSourceRejectsDuplicateName() {
        dao.rows.add(mysqlRow(1)); // name "ds-1"
        DataSourceSaveRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3306/test");
        request.setName("ds-1");
        try {
            service.createDataSource(request);
            fail("duplicate name must fail");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, e.getCode());
        }
        assertTrue(dao.inserted.isEmpty());
    }

    @Test
    public void createDataSourceRejectsForeignUrlPrefix() {
        // MYSQL type with a postgresql URL: prefix check alone must reject it
        try {
            service.createDataSource(saveRequest(ProductTypeEnum.MYSQL,
                    "jdbc:postgresql://fakehost:5432/db"));
            fail("wrong url prefix must fail");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_INVALID_JDBC_URL, e.getCode());
        }
    }

    @Test
    public void createDataSourceRejectsUrlNotMatchingTheTemplate() {
        try {
            service.createDataSource(saveRequest(ProductTypeEnum.MYSQL,
                    "jdbc:mysql://bad host:3306/db"));
            fail("template mismatch must fail");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_INVALID_JDBC_URL, e.getCode());
        }
    }

    @Test
    public void createDataSourceRejectsMissingDatabaseName() {
        // the "/" is mandatory but the database itself stays empty
        try {
            service.createDataSource(saveRequest(ProductTypeEnum.POSTGRESQL,
                    "jdbc:postgresql://fakehost:5432/"));
            fail("missing database name must fail");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_INVALID_JDBC_URL, e.getCode());
        }
    }

    @Test
    public void createDataSourceForHttpTypeOnlyChecksThePrefix() {
        // the documented HTTP sample embeds "http://" where the template wants host:port —
        // the HTTP type therefore skips template validation entirely
        service.createDataSource(saveRequest(ProductTypeEnum.HTTP,
                "jdbc:restful://http://fakehost:8080/anything"));
        assertEquals(1, dao.inserted.size());
    }

    @Test
    public void createDataSourceForSqliteUsesTheFileTemplate() {
        service.createDataSource(saveRequest(ProductTypeEnum.SQLITE3, "jdbc:sqlite:/tmp/x.db"));
        assertEquals(1, dao.inserted.size());
    }

    @Test
    public void createDataSourceAcceptsTheSecondOracleTemplate() {
        // SID form fails (":" vs "/"), service-name-less form matches the second template
        service.createDataSource(saveRequest(ProductTypeEnum.ORACLE,
                "jdbc:oracle:thin:@fakehost:1521/orcl"));
        assertEquals(1, dao.inserted.size());
    }

    @Test
    public void createDataSourceAcceptsTheThirdOracleTemplate() {
        service.createDataSource(saveRequest(ProductTypeEnum.ORACLE,
                "jdbc:oracle:thin:@//fakehost:1521/orcl.city.com"));
        assertEquals(1, dao.inserted.size());
    }

    @Test
    public void updateDataSourceRejectsMissingId() {
        DataSourceSaveRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3306/test");
        request.setId(null);
        try {
            service.updateDataSource(request);
            fail("null id must fail");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void updateDataSourceRejectsUnknownId() {
        DataSourceSaveRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3306/test");
        request.setId(404L);
        try {
            service.updateDataSource(request);
            fail("unknown id must fail");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void updateDataSourceRejectsNameOwnedByAnotherRow() {
        dao.rows.add(mysqlRow(1));
        dao.rows.add(mysqlRow(2));
        DataSourceSaveRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3306/test");
        request.setId(1L);
        request.setName("ds-2"); // owned by row 2
        try {
            service.updateDataSource(request);
            fail("name conflict must fail");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, e.getCode());
        }
    }

    @Test
    public void updateDataSourceKeepsSameOwnName() {
        dao.rows.add(mysqlRow(1));
        DataSourceSaveRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3306/test");
        request.setId(1L);
        request.setName("ds-1");
        service.updateDataSource(request);
        assertEquals(1, dao.updated.size());
    }

    @Test
    public void updateDataSourceWithMaskedPasswordKeepsTheStoredOne() {
        dao.rows.add(mysqlRow(3));
        DataSourceSaveRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3306/test");
        request.setId(3L);
        request.setName("ds-3");
        request.setPassword(ParamMaskUtils.MASK);
        service.updateDataSource(request);

        assertEquals(1, dao.updated.size());
        assertEquals("stored-pass", dao.updated.get(0).getPassword());
    }

    @Test
    public void updateDataSourceWithNewPasswordStoresIt() {
        dao.rows.add(mysqlRow(4));
        DataSourceSaveRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3306/other");
        request.setId(4L);
        request.setName("ds-4");
        request.setPassword("brand-new");
        service.updateDataSource(request);

        assertEquals("brand-new", dao.updated.get(0).getPassword());
        assertEquals("jdbc:mysql://fakehost:3306/other", dao.updated.get(0).getUrl());
    }

    @Test
    public void updateDataSourceInvalidUrlStillRejected() {
        dao.rows.add(mysqlRow(6));
        DataSourceSaveRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://bad host:3306/db");
        request.setId(6L);
        request.setName("ds-6");
        try {
            service.updateDataSource(request);
            fail("invalid url must fail on update too");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_INVALID_JDBC_URL, e.getCode());
        }
    }

    @Test
    public void updateDataSourceDropsTheCachedPool() {
        dao.rows.add(mysqlRow(7));
        service.testDataSource(7L);
        assertTrue(DataSourceUtils.getAllDataSourceIdSet().contains(7L));

        DataSourceSaveRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3307/db");
        request.setId(7L);
        request.setName("ds-7");
        service.updateDataSource(request);

        assertFalse("config change must drop the cached pool",
                DataSourceUtils.getAllDataSourceIdSet().contains(7L));
    }

    @Test
    public void deleteDataSourceRejectedWhileAssignedToAnApi() {
        dao.rows.add(mysqlRow(8));
        assignmentUsesDatasource.set(true);
        try {
            service.deleteDataSource(8L);
            fail("in-use datasource must not be deletable");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED, e.getCode());
        }
        assertEquals(1, dao.rows.size());
    }

    @Test
    public void deleteDataSourceRejectedWhileUsedByAnOnlineApi() {
        dao.rows.add(mysqlRow(9));
        onlineUsesDatasource.set(true);
        try {
            service.deleteDataSource(9L);
            fail("online datasource must not be deletable");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED, e.getCode());
        }
    }

    @Test
    public void deleteDataSourceRemovesRowAndDropsPool() {
        dao.rows.add(mysqlRow(10));
        service.testDataSource(10L);
        assertTrue(DataSourceUtils.getAllDataSourceIdSet().contains(10L));

        service.deleteDataSource(10L);

        assertTrue(dao.rows.isEmpty());
        assertEquals(Collections.singletonList(10L), dao.deleted);
        assertFalse(DataSourceUtils.getAllDataSourceIdSet().contains(10L));
    }

    // ---------------------------------------------------------------------
    // names / schemas / tables / views / columns
    // ---------------------------------------------------------------------

    @Test
    public void getDataSourceNamesMapsIdAndName() {
        dao.rows.add(mysqlRow(31));
        dao.rows.add(mysqlRow(32));

        PageResult<EntityIdNameResponse> result = service.getDataSourceNames(1, 10);

        assertEquals(2, result.getData().size());
        assertEquals(Long.valueOf(31L), result.getData().get(0).getId());
        assertEquals("ds-31", result.getData().get(0).getName());
        assertEquals("ds-32", result.getData().get(1).getName());
    }

    @Test
    public void getSchemasFromSqlSchemaListQuery() {
        dao.rows.add(mysqlRow(41));
        setFake("dp.fake.schemas", "public,information_schema");

        List<String> schemas = service.getDatasourceSchemas(41L);

        assertEquals(Arrays.asList("public", "information_schema"), schemas);
        assertTrue(System.getProperty("dp.fake.saw.query", "").contains("SCHEMA_NAME"));
    }

    @Test
    public void getSchemasForSqliteReturnsFixedListWithoutConnecting() {
        dao.rows.add(row(42, ProductTypeEnum.SQLITE3, "jdbc:sqlite:/tmp/x.db"));
        assertEquals(Collections.singletonList("sqlite_master"), service.getDatasourceSchemas(42L));
        assertFalse(DataSourceUtils.getAllDataSourceIdSet().contains(42L));
    }

    @Test
    public void getSchemasForHttpAndElasticsearchReturnFixedLists() {
        dao.rows.add(row(43, ProductTypeEnum.HTTP, "jdbc:restful://fakehost:8080"));
        dao.rows.add(row(44, ProductTypeEnum.ELASTICSEARCH, "jdbc:esdriver://fakehost:9200"));
        assertEquals(Collections.singletonList("http"), service.getDatasourceSchemas(43L));
        assertEquals(Collections.singletonList("elasticsearch"), service.getDatasourceSchemas(44L));
    }

    @Test
    public void getSchemasFromJdbcMetadataWhenNoSqlListConfigured() {
        // OCEANBASE has neither sqlSchemaList nor retSchemaList: metadata is the only source
        dao.rows.add(row(45, ProductTypeEnum.OCEANBASE, "jdbc:oceanbase://fakehost:2881/test"));
        setFake("dp.fake.schemas", "s1,s2");

        assertEquals(Arrays.asList("s1", "s2"), service.getDatasourceSchemas(45L));
    }

    @Test
    public void getSchemasFallsBackToCatalogsWhenMetadataSchemasEmpty() {
        dao.rows.add(row(46, ProductTypeEnum.OCEANBASE, "jdbc:oceanbase://fakehost:2881/test"));
        setFake("dp.fake.catalogs", "c1,c2");

        assertEquals(Arrays.asList("c1", "c2"), service.getDatasourceSchemas(46L));
    }

    @Test
    public void getSchemaTablesUsesTheTypeAdapterForCatalogAndSchema() {
        dao.rows.add(mysqlRow(51));
        setFake("dp.fake.tables", "t1,t2");

        List<String> tables = service.getSchemaTables(51L, "mydb");

        assertEquals(Arrays.asList("t1", "t2"), tables);
        // the MYSQL adapter maps the schema onto the catalog slot
        assertEquals("mydb", System.getProperty("dp.fake.saw.tables.catalog"));
        assertEquals("null", System.getProperty("dp.fake.saw.tables.schema"));
        assertEquals("TABLE", System.getProperty("dp.fake.saw.tables.types"));
    }

    @Test
    public void getSchemaTablesProbesCatalogAndSchemaLayersWhenNoAdapter() {
        dao.rows.add(row(52, ProductTypeEnum.OCEANBASE, "jdbc:oceanbase://fakehost:2881/test"));
        setFake("dp.fake.catalogs", "c1");
        setFake("dp.fake.schemas", "s1");
        setFake("dp.fake.tables", "t1");
        setFake("dp.fake.catalog", "CAT");

        assertEquals(Collections.singletonList("t1"), service.getSchemaTables(52L, "mydb"));
        // both layers detected: connection catalog as catalog, requested schema as schema
        assertEquals("CAT", System.getProperty("dp.fake.saw.tables.catalog"));
        assertEquals("mydb", System.getProperty("dp.fake.saw.tables.schema"));
    }

    @Test
    public void getSchemaTablesProbesSchemaOnlyWhenNoCatalogs() {
        dao.rows.add(row(53, ProductTypeEnum.OCEANBASE, "jdbc:oceanbase://fakehost:2881/test"));
        setFake("dp.fake.schemas", "s1");
        setFake("dp.fake.tables", "t1");

        assertEquals(Collections.singletonList("t1"), service.getSchemaTables(53L, "mydb"));
        assertEquals("null", System.getProperty("dp.fake.saw.tables.catalog"));
        assertEquals("mydb", System.getProperty("dp.fake.saw.tables.schema"));
    }

    @Test
    public void getSchemaTablesProbesCatalogOnlyWhenNoSchemas() {
        dao.rows.add(row(54, ProductTypeEnum.OCEANBASE, "jdbc:oceanbase://fakehost:2881/test"));
        setFake("dp.fake.catalogs", "c1");
        setFake("dp.fake.tables", "t1");

        assertEquals(Collections.singletonList("t1"), service.getSchemaTables(54L, "mydb"));
        // no schema layer: the requested schema itself becomes the catalog
        assertEquals("mydb", System.getProperty("dp.fake.saw.tables.catalog"));
        assertEquals("null", System.getProperty("dp.fake.saw.tables.schema"));
    }

    @Test
    public void getSchemaViewsForNoViewTypesReturnsEmptyList() {
        dao.rows.add(row(55, ProductTypeEnum.MONGODB, "jdbc:mongodb://fakehost:27017/admin"));
        assertTrue(service.getSchemaViews(55L, "any").isEmpty());
    }

    @Test
    public void getSchemaViewsAsksForViewTables() {
        dao.rows.add(mysqlRow(56));
        setFake("dp.fake.views", "v1,v2");

        List<String> views = service.getSchemaViews(56L, "mydb");

        assertEquals(Arrays.asList("v1", "v2"), views);
        assertEquals("VIEW", System.getProperty("dp.fake.saw.tables.types"));
    }

    @Test
    public void getTableColumnsMapsNameTypeAndRemarks() {
        dao.rows.add(mysqlRow(57));
        setFake("dp.fake.columns", "id|INT|primary key;name|VARCHAR(20)|");

        List<MetadataColumnResponse> columns = service.getTableColumns(57L, "mydb", "T_USER");

        assertEquals(2, columns.size());
        assertEquals("id", columns.get(0).getName());
        assertEquals("INT", columns.get(0).getType());
        assertEquals("primary key", columns.get(0).getRemarks());
        assertEquals("name", columns.get(1).getName());
        assertEquals("VARCHAR(20)", columns.get(1).getType());
        assertEquals("", columns.get(1).getRemarks());
        assertEquals("T_USER", System.getProperty("dp.fake.saw.columns.table"));
    }

    @Test
    public void metadataFailureSurfacesAsRuntimeException() {
        dao.rows.add(mysqlRow(58));
        setFake("dp.fake.meta.fail", "true");
        try {
            service.getSchemaTables(58L, "mydb");
            fail("metadata failure must surface");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage(), null == e.getMessage() || e.getMessage().contains("metadata"));
        }
    }

    // ---------------------------------------------------------------------
    // branch corners
    // ---------------------------------------------------------------------

    @Test
    public void invalidConnectionWithoutTestSqlSurfacesAsConnectFailure() {
        // INCEPTOR has no test SQL and the fake driver reports the connection invalid
        setFake("dp.fake.invalid", "true");
        try {
            service.preTest(saveRequest(ProductTypeEnum.INCEPTOR, "jdbc:hive2://fakehost:10000/default"));
            fail("an invalid connection must fail the test");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_CANNOT_CONNECT_REMOTE, e.getCode());
        }
    }

    @Test
    public void causeMessageStopsAtASelfCausedThrowableAndFallsBackToClassName() {
        // a throwable whose getCause() is itself: the walk must stop instead of looping
        Throwable selfCaused = new SQLException("self") {
            @Override
            public Throwable getCause() {
                return this;
            }
        };
        assertEquals("self", DataSourceService.causeMessage(selfCaused));

        // a chain with no message anywhere degrades to the root class name
        assertEquals("java.lang.IllegalStateException",
                DataSourceService.causeMessage(new IllegalStateException()));
    }

    @Test
    public void updateDataSourceWithANameOwnedByNobodyProceeds() {
        dao.rows.add(mysqlRow(61));
        DataSourceSaveRequest request = saveRequest(ProductTypeEnum.MYSQL,
                "jdbc:mysql://fakehost:3306/test");
        request.setId(61L);
        request.setName("totally-new-name"); // no row owns this name

        service.updateDataSource(request);

        assertEquals(1, dao.updated.size());
        assertEquals("totally-new-name", dao.updated.get(0).getName());
    }

    @Test
    public void sqlserverTypesUseTheConnectionCatalogDirectly() {
        // SQLSERVER declares hasCatalogAndSchema: no adapter probing, connection.getCatalog() wins
        dao.rows.add(row(62, ProductTypeEnum.SQLSERVER,
                "jdbc:sqlserver://fakehost:1433;DatabaseName=master"));
        setFake("dp.fake.tables", "t9");
        assertEquals(Collections.singletonList("t9"), service.getSchemaTables(62L, "mydb"));
        assertEquals("CAT", System.getProperty("dp.fake.saw.tables.catalog"));
        assertEquals("mydb", System.getProperty("dp.fake.saw.tables.schema"));
        assertEquals("TABLE", System.getProperty("dp.fake.saw.tables.types"));
    }

    @Test
    public void sqlserverViewsAndColumnsRunThroughTheCatalogLayerToo() {
        dao.rows.add(row(63, ProductTypeEnum.SQLSERVER,
                "jdbc:sqlserver://fakehost:1433;DatabaseName=master"));
        setFake("dp.fake.views", "v9");
        assertEquals(Collections.singletonList("v9"), service.getSchemaViews(63L, "mydb"));
        assertEquals("CAT", System.getProperty("dp.fake.saw.tables.catalog"));
        assertEquals("VIEW", System.getProperty("dp.fake.saw.tables.types"));

        setFake("dp.fake.columns", "id|INT|key");
        assertEquals(1, service.getTableColumns(63L, "mydb", "T9").size());
        assertEquals("T9", System.getProperty("dp.fake.saw.columns.table"));
    }

    @Test
    public void nullMetadataResultsSurfaceAsRuntimeExceptions() {
        // drivers that answer null from getSchemas(): the layer probe must fail loudly,
        // never silently assume a schema-less database
        dao.rows.add(row(64, ProductTypeEnum.OCEANBASE, "jdbc:oceanbase://fakehost:2881/test"));
        setFake("dp.fake.catalogs", "c1");
        setFake("dp.fake.null.schemas", "true");
        try {
            service.getSchemaTables(64L, "mydb");
            fail("a null schema result set must fail the probe");
        } catch (RuntimeException expected) {
            // expected: the NPE out of the probe is wrapped by the service
        }

        // drivers that answer null from getCatalogs(): same story on the catalog probe
        dao.rows.add(row(65, ProductTypeEnum.OCEANBASE, "jdbc:oceanbase://fakehost:2881/test"));
        setFake("dp.fake.null.catalogs", "true");
        try {
            service.getSchemaTables(65L, "mydb");
            fail("a null catalog result set must fail the probe");
        } catch (RuntimeException expected) {
            // expected
        }
    }
}
