// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.enums.*;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.ApiAssignmentSaveRequest;
import com.cs.core.dto.DataTypeFormatMapValue;
import com.cs.core.dto.ExpImpAssignmentModel;
import com.cs.core.dto.ExpImpDataSourceModel;
import com.cs.persistence.dao.ApiAssignmentDao;
import com.cs.persistence.dao.ApiGroupDao;
import com.cs.persistence.dao.ApiModuleDao;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.entity.*;
import com.cs.persistence.util.JsonUtils;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Export/import of API assignments:
 * the export payload must embed the assignment plus its referenced datasource/group/module
 * names, must never carry a plaintext datasource password while {@code datapoly.datasource.encrypt}
 * is disabled, and the import must reuse existing datasource/group/module rows (matching by
 * type+driver+url / by name) before creating new ones.
 */
public class ExportImportServiceTest {

    private static void restoreBeanFactory(Object previous) throws Exception {
        Field field = cn.hutool.extra.spring.SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, previous);
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // export
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void exportWritesJsonAttachmentWithoutPlaintextPassword() throws Exception {
        Harness harness = new Harness();
        harness.assignment.byId = assignment();
        harness.dataSource.all = Collections.singletonList(dataSource());
        harness.group.all = Collections.singletonList(ApiGroupEntity.builder().id(10L).name("core").build());
        harness.module.all = Collections.singletonList(ApiModuleEntity.builder().id(20L).name("orders").build());

        RecordingResponse response = new RecordingResponse();
        harness.service.exportAssignments(Collections.singletonList(1L), response.proxy);

        assertEquals("application/json;charset=utf-8", response.contentType);
        assertEquals("attachment; filename=datapoly_assignments.json", response.headers.get("Content-Disposition"));
        String json = new String(response.bodyBytes.toByteArray(), StandardCharsets.UTF_8);
        assertTrue("exported payload embeds the assignment", json.contains("\"name\":\"demo\""));
        assertTrue("group name travels for import-side matching", json.contains("\"groupName\":\"core\""));
        assertTrue("module name travels for import-side matching", json.contains("\"moduleName\":\"orders\""));
        assertTrue("datasource coordinates travel for import-side matching",
                json.contains("\"url\":\"jdbc:mysql://localhost:3306/x\""));
        assertTrue("SQL text travels with the assignment", json.contains("\"contextList\":[\"select 1\"]"));
        assertFalse("plaintext password must never leave the box while encryption is disabled",
                json.contains("secret-pw"));
    }

    @Test
    public void exportCarriesPasswordOnlyWhenEncryptionIsEnabled() throws Exception {
        Harness harness = new Harness();
        harness.assignment.byId = assignment();
        harness.dataSource.all = Collections.singletonList(dataSource());
        harness.group.all = Collections.singletonList(ApiGroupEntity.builder().id(10L).name("core").build());
        harness.module.all = Collections.singletonList(ApiModuleEntity.builder().id(20L).name("orders").build());
        DataTaskTestSupport.setField(harness.service, "dsEncryptEnabled", true);

        RecordingResponse response = new RecordingResponse();
        harness.service.exportAssignments(Collections.singletonList(1L), response.proxy);

        String json = new String(response.bodyBytes.toByteArray(), StandardCharsets.UTF_8);
        assertTrue("with DATAPOLY_DS_ENCRYPT on, the stored ciphertext is exported verbatim",
                json.contains("\"password\":\"secret-pw\""));
    }

    @Test
    public void exportSkipsUnknownIds() throws Exception {
        Harness harness = new Harness();
        harness.assignment.byId = null;

        RecordingResponse response = new RecordingResponse();
        harness.service.exportAssignments(Arrays.asList(1L, 2L), response.proxy);

        String json = new String(response.bodyBytes.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("a null lookup must not produce a row", "[]", json.replaceAll("\\s+", ""));
    }

    @Test
    public void exportWrapsOutputStreamFailures() throws Exception {
        Harness harness = new Harness();
        harness.assignment.byId = assignment();
        harness.dataSource.all = new ArrayList<>(Collections.singletonList(dataSource()));
        harness.group.all = new ArrayList<>(Collections.singletonList(ApiGroupEntity.builder().id(10L).name("core").build()));
        harness.module.all = new ArrayList<>(Collections.singletonList(ApiModuleEntity.builder().id(20L).name("orders").build()));

        HttpServletResponse broken = (HttpServletResponse) Proxy.newProxyInstance(
                ExportImportServiceTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (p, m, a) -> {
                    if ("getOutputStream".equals(m.getName())) {
                        throw new java.io.IOException("pipe closed");
                    }
                    if ("setContentType".equals(m.getName()) || "setHeader".equals(m.getName())) {
                        return null;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    Class<?> rt = m.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    return null;
                });
        try {
            harness.service.exportAssignments(Collections.singletonList(1L), broken);
            fail("an output stream failure must surface as a runtime exception");
        } catch (RuntimeException e) {
            assertTrue(String.valueOf(e.getCause()), String.valueOf(e.getCause()).contains("pipe closed"));
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // import (transactional core)
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void importAllReusesExistingDatasourceGroupAndModuleRows() {
        Harness harness = new Harness();
        ExpImpAssignmentModel model = importModel();

        harness.service.importAllInTransaction(
                Collections.singletonList(model),
                new ArrayList<>(Collections.singletonList(dataSource())),
                new ArrayList<>(Collections.singletonList(ApiGroupEntity.builder().id(10L).name("core").build())),
                new ArrayList<>(Collections.singletonList(ApiModuleEntity.builder().id(20L).name("orders").build())));

        assertTrue("an exactly matching datasource must be reused, not duplicated",
                harness.dataSource.inserted.isEmpty());
        assertTrue("a group with the same name must be reused", harness.group.inserted.isEmpty());
        assertTrue("a module with the same name must be reused", harness.module.inserted.isEmpty());
        assertEquals(1, harness.createdRequests.size());
        ApiAssignmentSaveRequest request = harness.createdRequests.get(0);
        assertEquals(Long.valueOf(2L), request.getDatasourceId());
        assertEquals(Long.valueOf(10L), request.getGroupId());
        assertEquals(Long.valueOf(20L), request.getModuleId());
        assertEquals("demo", request.getName());
        assertEquals(HttpMethodEnum.POST, request.getMethod());
        assertEquals("demo", request.getPath());
        assertEquals(ExecuteEngineEnum.SQL, request.getEngine());
        assertEquals(Collections.singletonList("select 1"), request.getContextList());
        assertEquals(NamingStrategyEnum.CAMEL_CASE, request.getNamingStrategy());
        assertEquals(CacheKeyTypeEnum.NONE, request.getCacheKeyType());
        assertEquals(1, request.getFormatMap().size());
        assertEquals(DataTypeFormatEnum.DATE, request.getFormatMap().get(0).getKey());
        assertEquals("yyyy-MM-dd", request.getFormatMap().get(0).getValue());
    }

    @Test
    public void importAllCreatesMissingDatasourceGroupAndModuleRows() {
        Harness harness = new Harness();
        ExpImpAssignmentModel model = importModel();

        harness.service.importAllInTransaction(
                Collections.singletonList(model),
                new ArrayList<DataSourceEntity>(),
                new ArrayList<ApiGroupEntity>(),
                new ArrayList<ApiModuleEntity>());

        assertEquals(1, harness.dataSource.inserted.size());
        DataSourceEntity created = harness.dataSource.inserted.get(0);
        assertEquals(ProductTypeEnum.MYSQL, created.getType());
        assertEquals("jdbc:mysql://localhost:3306/x", created.getUrl());
        assertEquals("root", created.getUsername());
        assertEquals("an imported datasource without a password stays credential-free", null, created.getPassword());
        assertEquals(1, harness.group.inserted.size());
        assertEquals("core", harness.group.inserted.get(0).getName());
        assertEquals(1, harness.module.inserted.size());
        assertEquals("orders", harness.module.inserted.get(0).getName());
        assertEquals(1, harness.createdRequests.size());
    }

    @Test
    public void importAllDoesNotReuseDatasourceWhenUrlDiffers() {
        Harness harness = new Harness();
        ExpImpAssignmentModel model = importModel();
        DataSourceEntity other = dataSource();
        other.setUrl("jdbc:mysql://elsewhere:3306/y");

        harness.service.importAllInTransaction(Collections.singletonList(model),
                new ArrayList<>(Collections.singletonList(other)),
                new ArrayList<ApiGroupEntity>(), new ArrayList<ApiModuleEntity>());

        assertEquals("type+driver+url must all match for reuse", 1, harness.dataSource.inserted.size());
        assertEquals(1, harness.createdRequests.size());
    }

    @Test
    public void importAllKeepsImportedPasswordWhenPresent() {
        Harness harness = new Harness();
        ExpImpAssignmentModel model = importModel();
        model.getDataSourceModel().setPassword("cipher-text");

        harness.service.importAllInTransaction(Collections.singletonList(model),
                new ArrayList<DataSourceEntity>(), new ArrayList<ApiGroupEntity>(), new ArrayList<ApiModuleEntity>());

        assertEquals("cipher-text", harness.dataSource.inserted.get(0).getPassword());
    }

    @Test
    public void importAllProcessesEachModelWithSharedCreatedRows() {
        Harness harness = new Harness();
        ExpImpAssignmentModel first = importModel();
        ExpImpAssignmentModel second = importModel();
        second.setName("demo-2");
        second.setPath("demo-2");

        harness.service.importAllInTransaction(Arrays.asList(first, second),
                new ArrayList<DataSourceEntity>(), new ArrayList<ApiGroupEntity>(), new ArrayList<ApiModuleEntity>());

        // the second model must observe the row created by the first pass, not duplicate it
        assertEquals(1, harness.dataSource.inserted.size());
        assertEquals(1, harness.group.inserted.size());
        assertEquals(1, harness.module.inserted.size());
        assertEquals(2, harness.createdRequests.size());
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // import (file entry point)
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void importAssignmentsReadsTheUploadedFileAndRunsTheTransaction() throws Exception {
        Harness harness = new Harness();
        ExpImpAssignmentModel model = importModel();
        String content = JsonUtils.toJsonString(Collections.singletonList(model));

        Object previousBeanFactory = installBeanFactory(ExportImportService.class, harness.service);
        try {
            harness.service.importAssignments(uploadedFile(content));
        } finally {
            restoreBeanFactory(previousBeanFactory);
        }

        assertEquals("the parsed file feeds the transactional importer", 1, harness.createdRequests.size());
        assertEquals("demo", harness.createdRequests.get(0).getName());
    }

    private static Object installBeanFactory(Class<?> beanType, Object bean) throws Exception {
        Object factory = Proxy.newProxyInstance(ExportImportServiceTest.class.getClassLoader(),
                new Class<?>[]{org.springframework.beans.factory.config.ConfigurableListableBeanFactory.class},
                (p, m, a) -> {
                    if ("getBean".equals(m.getName()) && a != null && a.length > 0 && a[0] == beanType) {
                        return bean;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    if ("toString".equals(m.getName())) {
                        return "fakeBeanFactory";
                    }
                    Class<?> rt = m.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    return null;
                });
        Field field = cn.hutool.extra.spring.SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, factory);
        return previous;
    }

    private static MultipartFile uploadedFile(String content) {
        return (MultipartFile) Proxy.newProxyInstance(ExportImportServiceTest.class.getClassLoader(),
                new Class<?>[]{MultipartFile.class},
                (p, m, a) -> {
                    if ("getInputStream".equals(m.getName())) {
                        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                    }
                    if ("getName".equals(m.getName())) {
                        return "file";
                    }
                    if ("getSize".equals(m.getName())) {
                        return (long) content.length();
                    }
                    if ("isEmpty".equals(m.getName())) {
                        return false;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    Class<?> rt = m.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    return null;
                });
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // fixtures / harness
    /////////////////////////////////////////////////////////////////////////////////////

    private static ApiAssignmentEntity assignment() {
        ApiAssignmentEntity entity = ApiAssignmentEntity.builder()
                .id(1L)
                .groupId(10L)
                .moduleId(20L)
                .datasourceId(2L)
                .name("demo")
                .method(HttpMethodEnum.POST)
                .path("demo")
                .engine(ExecuteEngineEnum.SQL)
                .namingStrategy(NamingStrategyEnum.CAMEL_CASE)
                .cacheKeyType(CacheKeyTypeEnum.NONE)
                .build();
        Map<DataTypeFormatEnum, String> formats = new EnumMap<>(DataTypeFormatEnum.class);
        formats.put(DataTypeFormatEnum.DATE, "yyyy-MM-dd");
        entity.setResponseFormat(formats);
        entity.setContextList(new ArrayList<>(Collections.singletonList(new ApiContextEntity("select 1"))));
        return entity;
    }

    private static DataSourceEntity dataSource() {
        return DataSourceEntity.builder()
                .id(2L).name("mysql-ds").type(ProductTypeEnum.MYSQL).version("8")
                .driver("com.mysql.cj.jdbc.Driver")
                .url("jdbc:mysql://localhost:3306/x")
                .username("root").password("secret-pw")
                .build();
    }

    private static ExpImpAssignmentModel importModel() {
        ExpImpDataSourceModel dataSourceModel = new ExpImpDataSourceModel();
        dataSourceModel.setName("mysql-ds");
        dataSourceModel.setType(ProductTypeEnum.MYSQL);
        dataSourceModel.setVersion("8");
        dataSourceModel.setDriver("com.mysql.cj.jdbc.Driver");
        dataSourceModel.setUrl("jdbc:mysql://localhost:3306/x");
        dataSourceModel.setUsername("root");
        // no password: export drops it while encryption is off

        ExpImpAssignmentModel model = new ExpImpAssignmentModel();
        model.setGroupName("core");
        model.setModuleName("orders");
        model.setDataSourceModel(dataSourceModel);
        model.setName("demo");
        model.setMethod(HttpMethodEnum.POST);
        model.setPath("demo");
        model.setEngine(ExecuteEngineEnum.SQL);
        model.setNamingStrategy(NamingStrategyEnum.CAMEL_CASE);
        model.setCacheKeyType(CacheKeyTypeEnum.NONE);
        Map<DataTypeFormatEnum, String> formats = new EnumMap<>(DataTypeFormatEnum.class);
        formats.put(DataTypeFormatEnum.DATE, "yyyy-MM-dd");
        model.setResponseFormat(formats);
        model.setContextList(new ArrayList<>(Collections.singletonList("select 1")));
        return model;
    }

    private static final class FakeAssignmentDao extends ApiAssignmentDao {
        ApiAssignmentEntity byId;

        @Override
        public ApiAssignmentEntity getById(Long id, boolean withSql) {
            return byId;
        }
    }

    private static final class FakeDataSourceDao extends DataSourceDao {
        List<DataSourceEntity> all = new ArrayList<>();
        final List<DataSourceEntity> inserted = new ArrayList<>();
        private long nextId = 500L;

        @Override
        public List<DataSourceEntity> listAll() {
            return all;
        }

        @Override
        public void insert(DataSourceEntity entity) {
            if (null == entity.getId()) {
                entity.setId(nextId++);
            }
            inserted.add(entity);
        }
    }

    private static final class FakeGroupDao extends ApiGroupDao {
        List<ApiGroupEntity> all = new ArrayList<>();
        final List<ApiGroupEntity> inserted = new ArrayList<>();
        private long nextId = 600L;

        @Override
        public List<ApiGroupEntity> listAll() {
            return all;
        }

        @Override
        public void insert(ApiGroupEntity entity) {
            if (null == entity.getId()) {
                entity.setId(nextId++);
            }
            inserted.add(entity);
        }
    }

    private static final class FakeModuleDao extends ApiModuleDao {
        List<ApiModuleEntity> all = new ArrayList<>();
        final List<ApiModuleEntity> inserted = new ArrayList<>();
        private long nextId = 700L;

        @Override
        public List<ApiModuleEntity> listAll() {
            return all;
        }

        @Override
        public void insert(ApiModuleEntity entity) {
            if (null == entity.getId()) {
                entity.setId(nextId++);
            }
            inserted.add(entity);
        }
    }

    private static final class Harness {
        final FakeAssignmentDao assignment = new FakeAssignmentDao();
        final FakeDataSourceDao dataSource = new FakeDataSourceDao();
        final FakeGroupDao group = new FakeGroupDao();
        final FakeModuleDao module = new FakeModuleDao();
        final List<ApiAssignmentSaveRequest> createdRequests = new ArrayList<>();
        ExportImportService service;

        Harness() {
            service = new ExportImportService();
            DataTaskTestSupport.setField(service, "apiAssignmentDao", assignment);
            DataTaskTestSupport.setField(service, "dataSourceDao", dataSource);
            DataTaskTestSupport.setField(service, "apiGroupDao", group);
            DataTaskTestSupport.setField(service, "apiModuleDao", module);
            // the import pipeline funnels through ApiAssignmentService.createAssignment;
            // a recording subclass keeps this test about export/import mapping only
            ApiAssignmentService recorder = new ApiAssignmentService() {
                @Override
                public Long createAssignment(ApiAssignmentSaveRequest request) {
                    createdRequests.add(request);
                    return 1L;
                }
            };
            DataTaskTestSupport.setField(service, "apiAssignmentService", recorder);
        }
    }

    private static final class RecordingResponse {
        final ByteArrayOutputStreamish bodyBytes = new ByteArrayOutputStreamish();
        String contentType;
        final Map<String, String> headers = new LinkedHashMap<>();
        HttpServletResponse proxy;

        RecordingResponse() {
            proxy = (HttpServletResponse) Proxy.newProxyInstance(
                    ExportImportServiceTest.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (p, m, a) -> {
                        switch (m.getName()) {
                            case "getOutputStream":
                                return bodyBytes.stream();
                            case "setContentType":
                                contentType = String.valueOf(a[0]);
                                return null;
                            case "setHeader":
                                headers.put(String.valueOf(a[0]), String.valueOf(a[1]));
                                return null;
                            case "setStatus":
                            case "setCharacterEncoding":
                                return null;
                            case "getStatus":
                                return 200;
                            case "hashCode":
                                return System.identityHashCode(p);
                            case "equals":
                                return p == a[0];
                            case "toString":
                                return "recordingResponse";
                            default:
                                Class<?> rt = m.getReturnType();
                                if (rt == boolean.class) {
                                    return false;
                                }
                                if (rt == int.class) {
                                    return 0;
                                }
                                return null;
                        }
                    });
        }
    }

    /** Minimal byte sink handing the servlet output stream back to the test. */
    private static final class ByteArrayOutputStreamish {
        private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

        ServletOutputStream stream() {
            return new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                }

                @Override
                public void write(int b) {
                    buffer.write(b);
                }
            };
        }

        byte[] toByteArray() {
            return buffer.toByteArray();
        }
    }
}
