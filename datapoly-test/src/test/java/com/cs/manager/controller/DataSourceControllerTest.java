// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.core.dto.*;
import com.cs.core.driver.DriverLoadService;
import com.cs.core.service.DataSourceService;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Thin REST layer: every endpoint must forward its parameters to the service untouched and wrap
 * the returned payload in a successful {@link ResultEntity} (or hand back the {@link PageResult} as-is).
 * Fakes subclass the concrete services and record the calls; no Spring context is involved.
 */
public class DataSourceControllerTest {

    /** Service fake: answers canned payloads and records the last arguments of each endpoint method. */
    private static class RecordingDataSourceService extends DataSourceService {
        final AtomicReference<Long> idArg = new AtomicReference<>();
        final AtomicReference<String> schemaArg = new AtomicReference<>();
        final AtomicReference<String> tableArg = new AtomicReference<>();
        final AtomicReference<DataSourceBaseRequest> baseRequest = new AtomicReference<>();
        final AtomicReference<DataSourceSaveRequest> saveRequest = new AtomicReference<>();
        final AtomicReference<EntitySearchRequest> searchRequest = new AtomicReference<>();
        final AtomicReference<Integer> pageArg = new AtomicReference<>();
        final AtomicReference<Integer> sizeArg = new AtomicReference<>();
        final AtomicReference<ProductTypeEnum> typeArg = new AtomicReference<>();

        List<DatabaseTypeDetailResponse> types = new ArrayList<>();
        List<DatasourceDetailResponse> details = new ArrayList<>();
        PageResult<DatasourceDetailResponse> searchResult = new PageResult<>();
        DatasourceDetailResponse detail = new DatasourceDetailResponse();
        List<String> schemas = new ArrayList<>();
        List<String> tables = new ArrayList<>();
        List<EntityIdNameResponse> names = new ArrayList<>();
        List<MetadataColumnResponse> columns = new ArrayList<>();

        @Override
        public List<DatabaseTypeDetailResponse> getTypes() {
            return types;
        }

        @Override
        public PageResult<DatasourceDetailResponse> searchList(EntitySearchRequest request) {
            searchRequest.set(request);
            return searchResult;
        }

        @Override
        public DatasourceDetailResponse getDetailById(Long id) {
            idArg.set(id);
            return detail;
        }

        @Override
        public void preTest(DataSourceBaseRequest request) {
            baseRequest.set(request);
        }

        @Override
        public void testDataSource(Long id) {
            idArg.set(id);
        }

        @Override
        public void createDataSource(DataSourceSaveRequest request) {
            saveRequest.set(request);
        }

        @Override
        public void updateDataSource(DataSourceSaveRequest request) {
            saveRequest.set(request);
        }

        @Override
        public void deleteDataSource(Long id) {
            idArg.set(id);
        }

        @Override
        public PageResult<EntityIdNameResponse> getDataSourceNames(Integer page, Integer size) {
            pageArg.set(page);
            sizeArg.set(size);
            PageResult<EntityIdNameResponse> result = new PageResult<>();
            result.setData(names);
            return result;
        }

        @Override
        public List<String> getDatasourceSchemas(Long id) {
            idArg.set(id);
            return schemas;
        }

        @Override
        public List<String> getSchemaTables(Long id, String schema) {
            idArg.set(id);
            schemaArg.set(schema);
            return tables;
        }

        @Override
        public List<String> getSchemaViews(Long id, String schema) {
            idArg.set(id);
            schemaArg.set(schema);
            return Collections.emptyList();
        }

        @Override
        public List<MetadataColumnResponse> getTableColumns(Long id, String schema, String table) {
            idArg.set(id);
            schemaArg.set(schema);
            tableArg.set(table);
            return columns;
        }
    }

    private RecordingDataSourceService service;
    private DriverLoadService driverLoadService;
    private DataSourceController controller;

    @Before
    public void setUp() throws Exception {
        service = new RecordingDataSourceService();
        driverLoadService = new DriverLoadService() {
            @Override
            public List<DatabaseTypeDriverResponse> getDrivers(ProductTypeEnum dbTypeEnum) {
                DatabaseTypeDriverResponse response = DatabaseTypeDriverResponse.builder()
                        .driverVersion("8.0.33")
                        .driverClass(dbTypeEnum.getDriver())
                        .driverPath("/tmp/drivers/mysql/8.0.33")
                        .jarFiles(Collections.singletonList("mysql-connector.jar"))
                        .build();
                return Collections.singletonList(response);
            }
        };
        controller = new DataSourceController();
        setField("datasourceService", service);
        setField("driverLoadService", driverLoadService);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = DataSourceController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    @Test
    public void getTypesWrapsServiceList() {
        DatabaseTypeDetailResponse mysql = new DatabaseTypeDetailResponse();
        mysql.setId(1);
        mysql.setType("MYSQL");
        service.types = Collections.singletonList(mysql);

        ResultEntity result = controller.getTypes();

        assertEquals(Integer.valueOf(0), result.getCode());
        assertEquals("success", result.getMessage());
        assertSame(service.types, result.getData());
    }

    @Test
    public void getDriversDelegatesToDriverLoadService() {
        ResultEntity result = controller.getDrivers(ProductTypeEnum.MYSQL);

        List<?> data = (List<?>) result.getData();
        assertEquals(1, data.size());
        DatabaseTypeDriverResponse response = (DatabaseTypeDriverResponse) data.get(0);
        assertEquals("8.0.33", response.getDriverVersion());
        assertEquals(ProductTypeEnum.MYSQL.getDriver(), response.getDriverClass());
        assertEquals(Collections.singletonList("mysql-connector.jar"), response.getJarFiles());
    }

    @Test
    public void preTestForwardsRequestAndReturnsSuccess() {
        DataSourceBaseRequest request = new DataSourceBaseRequest();
        request.setName("ds");
        request.setType(ProductTypeEnum.MYSQL);

        ResultEntity result = controller.preTest(request);

        assertEquals(Integer.valueOf(0), result.getCode());
        assertNull(result.getData());
        assertSame(request, service.baseRequest.get());
    }

    @Test
    public void getConnectionsReturnsPageResultUntouched() {
        EntitySearchRequest request = new EntitySearchRequest();
        request.setPage(2);
        request.setSize(5);
        request.setSearchText("mysql");
        service.searchResult.setData(Arrays.asList(new DatasourceDetailResponse(), new DatasourceDetailResponse()));

        PageResult<DatasourceDetailResponse> result = controller.getConnections(request);

        assertSame(service.searchResult, result);
        assertEquals(2, result.getData().size());
        assertSame(request, service.searchRequest.get());
    }

    @Test
    public void getDetailForwardsIdAndWrapsDetail() {
        service.detail.setName("the-datasource");

        ResultEntity result = controller.getDetail(42L);

        assertSame(service.detail, result.getData());
        assertEquals(Long.valueOf(42L), service.idArg.get());
    }

    @Test
    public void testConnectionForwardsId() {
        ResultEntity result = controller.test(7L);

        assertEquals(Integer.valueOf(0), result.getCode());
        assertEquals(Long.valueOf(7L), service.idArg.get());
    }

    @Test
    public void createForwardsSaveRequest() {
        DataSourceSaveRequest request = new DataSourceSaveRequest();
        request.setId(9L);
        request.setName("new-ds");

        ResultEntity result = controller.create(request);

        assertEquals(Integer.valueOf(0), result.getCode());
        assertSame(request, service.saveRequest.get());
    }

    @Test
    public void updateForwardsSaveRequest() {
        DataSourceSaveRequest request = new DataSourceSaveRequest();
        request.setId(9L);

        ResultEntity result = controller.update(request);

        assertEquals(Integer.valueOf(0), result.getCode());
        assertSame(request, service.saveRequest.get());
    }

    @Test
    public void deleteForwardsId() {
        ResultEntity result = controller.delete(11L);

        assertEquals(Integer.valueOf(0), result.getCode());
        assertEquals(Long.valueOf(11L), service.idArg.get());
    }

    @Test
    public void getNameListForwardsPagingParameters() {
        service.names = Arrays.asList(new EntityIdNameResponse(1L, "a"), new EntityIdNameResponse(2L, "b"));

        PageResult<EntityIdNameResponse> result = controller.getNameList(3, 20);

        assertEquals(service.names, result.getData());
        assertEquals(Integer.valueOf(3), service.pageArg.get());
        assertEquals(Integer.valueOf(20), service.sizeArg.get());
    }

    @Test
    public void getNameListToleratesMissingPagingParameters() {
        controller.getNameList(null, null);

        assertNull(service.pageArg.get());
        assertNull(service.sizeArg.get());
    }

    @Test
    public void getSchemasWrapsSchemaList() {
        service.schemas = Arrays.asList("public", "information_schema");

        ResultEntity result = controller.getSchemas(5L);

        assertEquals(service.schemas, result.getData());
        assertEquals(Long.valueOf(5L), service.idArg.get());
    }

    @Test
    public void getSchemaTablesForwardsSchema() {
        service.tables = Collections.singletonList("T_USER");

        ResultEntity result = controller.getSchemaTables(5L, "public");

        assertEquals(service.tables, result.getData());
        assertEquals(Long.valueOf(5L), service.idArg.get());
        assertEquals("public", service.schemaArg.get());
    }

    @Test
    public void getSchemaViewsForwardsSchema() {
        ResultEntity result = controller.getSchemaViews(5L, "public");

        assertEquals(Collections.emptyList(), result.getData());
        assertEquals("public", service.schemaArg.get());
    }

    @Test
    public void getTableColumnsForwardsSchemaAndTable() {
        service.columns = Collections.singletonList(MetadataColumnResponse.builder()
                .name("id").type("BIGINT").remarks("primary key").build());

        ResultEntity result = controller.getTableColumns(5L, "public", "T_USER");

        assertEquals(service.columns, result.getData());
        assertEquals(Long.valueOf(5L), service.idArg.get());
        assertEquals("public", service.schemaArg.get());
        assertEquals("T_USER", service.tableArg.get());
    }
}
