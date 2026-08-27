// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.dto.PageResult;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.common.exception.*;
import com.cs.common.util.JdbcUrlUtils;
import com.cs.core.driver.DriverLoadService;
import com.cs.core.dto.*;
import com.cs.core.util.DataSourceUtils;
import com.cs.persistence.dao.*;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.util.PageUtils;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

@Service
public class DataSourceService {

    @Resource
    private DataSourceDao dataSourceDao;

    public List<DatabaseTypeDetailResponse> getTypes() {
        List<DatabaseTypeDetailResponse> lists = new ArrayList<>();
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            DatabaseTypeDetailResponse detail = new DatabaseTypeDetailResponse();
            detail.setId(type.getId());
            detail.setType(type.getName().toUpperCase());
            detail.setDriver(type.getDriver());
            detail.setSample(type.getSample());
            lists.add(detail);
        }

        return lists;
    }

    public PageResult<DatasourceDetailResponse> searchList(EntitySearchRequest request) {
        Supplier<List<DatasourceDetailResponse>> method = () -> {
            List<DataSourceEntity> list = dataSourceDao.listAll(request.getSearchText());
            return list.stream().map(dataSourceEntity -> {
                DatasourceDetailResponse response = new DatasourceDetailResponse();
                DataSourceUtils.decrypt(dataSourceEntity);
                BeanUtil.copyProperties(dataSourceEntity, response);
                return response;
            }).collect(Collectors.toList());
        };

        return PageUtils.getPage(method, request.getPage(), request.getSize());
    }

    public DatasourceDetailResponse getDetailById(Long id) {
        DataSourceEntity dataSourceEntity = dataSourceDao.getById(id);
        DatasourceDetailResponse response = new DatasourceDetailResponse();
        DataSourceUtils.decrypt(dataSourceEntity);
        BeanUtil.copyProperties(dataSourceEntity, response);
        return response;
    }

    private void testConnection(HikariDataSource ds, ProductTypeEnum type) {
        try (Connection connection = ds.getConnection()) {
            if (StringUtils.isNotBlank(type.getSql())) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(type.getSql());
                }
            } else {
                if (!connection.isValid(2)) {
                    throw new RuntimeException("Connection is invalid!");
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void preTest(DataSourceBaseRequest request) {
        DataSourceEntity dataSourceEntity = DataSourceEntity.builder()
                .name(request.getName())
                .type(request.getType())
                .driver(request.getDriver())
                .version(request.getVersion())
                .url(request.getUrl())
                .username(request.getUsername())
                .password(request.getPassword())
                .poolConfig(request.getPoolConfig())
                .build();
        File driverPathFile = SpringUtil.getBean(DriverLoadService.class)
                .getVersionDriverFile(dataSourceEntity.getType(),
                        dataSourceEntity.getVersion());
        String driverPath = driverPathFile.getAbsolutePath();
        HikariDataSource ds = DataSourceUtils.createDataSource(dataSourceEntity, driverPath);
        try {
            testConnection(ds, request.getType());
        } finally {
            DataSourceUtils.closeHikariDataSource(ds);
        }
    }

    public void testDataSource(Long id) {
        DataSourceEntity dataSourceEntity = dataSourceDao.getById(id);
        if (null == dataSourceEntity) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "common.id.not.found", id);
        }
        File driverPathFile = SpringUtil.getBean(DriverLoadService.class)
                .getVersionDriverFile(dataSourceEntity.getType(),
                        dataSourceEntity.getVersion());
        String driverPath = driverPathFile.getAbsolutePath();
        HikariDataSource ds = DataSourceUtils.getHikariDataSource(dataSourceEntity, driverPath);
        testConnection(ds, dataSourceEntity.getType());
    }

    public void createDataSource(DataSourceSaveRequest request) {
        if (Objects.nonNull(dataSourceDao.getByName(request.getName()))) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS,
                    "datasource.name.exists", request.getName());
        }

        DataSourceEntity dataSourceEntity = new DataSourceEntity();
        BeanUtil.copyProperties(request, dataSourceEntity);

        validJdbcUrlFormat(dataSourceEntity);
        DataSourceUtils.encrypt(dataSourceEntity);
        dataSourceDao.insert(dataSourceEntity);
    }

    public void updateDataSource(DataSourceSaveRequest request) {
        if (Objects.isNull(request.getId()) || Objects.isNull(dataSourceDao.getById(request.getId()))) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "common.id.not.found", request.getId());
        }

        DataSourceEntity exist = dataSourceDao.getByName(request.getName());
        if (Objects.nonNull(exist) && !exist.getId().equals(request.getId())) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, "common.name.exists", request.getName());
        }

        DataSourceEntity dataSourceEntity = new DataSourceEntity();
        BeanUtil.copyProperties(request, dataSourceEntity);

        validJdbcUrlFormat(dataSourceEntity);
        DataSourceUtils.encrypt(dataSourceEntity);
        dataSourceDao.updateById(dataSourceEntity);
        DataSourceUtils.dropHikariDataSource(request.getId());
    }

    public void deleteDataSource(Long id) {
        if (SpringUtil.getBean(ApiAssignmentDao.class).existsDataSourceById(id)) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED, "common.id.not.found", id);
        }
        if (SpringUtil.getBean(ApiOnlineDao.class).existsDataSourceById(id)) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED, "common.id.not.found", id);
        }
        dataSourceDao.deleteById(id);
        DataSourceUtils.dropHikariDataSource(id);
    }

    public PageResult<EntityIdNameResponse> getDataSourceNames(Integer page, Integer size) {
        Supplier<List<EntityIdNameResponse>> method = () -> {
            List<DataSourceEntity> lists = dataSourceDao.listAll(null);
            return lists.stream()
                    .map(c -> new EntityIdNameResponse(c.getId(), c.getName()))
                    .collect(Collectors.toList());
        };

        return PageUtils.getPage(method, page, size);
    }

    public List<String> getDatasourceSchemas(Long id) {
        DataSourceEntity dataSourceEntity = dataSourceDao.getById(id);
        String sqlList = dataSourceEntity.getType().getContext().getSqlSchemaList();
        if (null == sqlList) {
            List<String> ret = dataSourceEntity.getType().getContext().getRetSchemaList();
            if (null != ret) {
                return ret;
            }
        }

        File driverPathFile = SpringUtil.getBean(DriverLoadService.class)
                .getVersionDriverFile(dataSourceEntity.getType(),
                        dataSourceEntity.getVersion());
        String driverPath = driverPathFile.getAbsolutePath();
        HikariDataSource ds = DataSourceUtils.getHikariDataSource(dataSourceEntity, driverPath);

        List<String> result = new ArrayList<>();
        if (StringUtils.isNotBlank(sqlList)) {
            try (Connection connection = ds.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(sqlList)) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            try (Connection connection = ds.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                try (ResultSet rs = metaData.getSchemas(connection.getCatalog(), null)) {
                    while (rs.next()) {
                        result.add(rs.getString("TABLE_SCHEM"));
                    }
                }
                if (CollectionUtils.isEmpty(result)) {
                    try (ResultSet catalogRs = metaData.getCatalogs()) {
                        while (catalogRs.next()) {
                            result.add(catalogRs.getString("TABLE_CAT"));
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    public List<String> getSchemaTables(Long id, String schema) {
        DataSourceEntity dataSourceEntity = dataSourceDao.getById(id);
        File driverPathFile = SpringUtil.getBean(DriverLoadService.class)
                .getVersionDriverFile(dataSourceEntity.getType(),
                        dataSourceEntity.getVersion());
        String driverPath = driverPathFile.getAbsolutePath();
        ProductTypeEnum productType = dataSourceEntity.getType();
        List<String> result = new ArrayList<>();
        HikariDataSource ds = DataSourceUtils.getHikariDataSource(dataSourceEntity, driverPath);
        try (Connection connection = ds.getConnection()) {
            String catalogName = productType.getContext().isHasCatalogAndSchema()
                    ? connection.getCatalog()
                    : getAdapter(productType, connection, schema).getLeft();
            String schemaName = getAdapter(productType, connection, schema).getRight();
            try (ResultSet rs = connection.getMetaData()
                    .getTables(catalogName, schemaName, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    result.add(rs.getString("TABLE_NAME"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public List<String> getSchemaViews(Long id, String schema) {
        DataSourceEntity dataSourceEntity = dataSourceDao.getById(id);
        File driverPathFile = SpringUtil.getBean(DriverLoadService.class)
                .getVersionDriverFile(dataSourceEntity.getType(),
                        dataSourceEntity.getVersion());
        String driverPath = driverPathFile.getAbsolutePath();
        ProductTypeEnum productType = dataSourceEntity.getType();
        if (productType.isNoViewTables()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        HikariDataSource ds = DataSourceUtils.getHikariDataSource(dataSourceEntity, driverPath);
        try (Connection connection = ds.getConnection()) {
            String catalogName = productType.getContext().isHasCatalogAndSchema()
                    ? connection.getCatalog()
                    : getAdapter(productType, connection, schema).getLeft();
            String schemaName = getAdapter(productType, connection, schema).getRight();
            try (ResultSet rs = connection.getMetaData()
                    .getTables(catalogName, schemaName, "%", new String[]{"VIEW"})) {
                while (rs.next()) {
                    result.add(rs.getString("TABLE_NAME"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public List<MetadataColumnResponse> getTableColumns(Long id, String schema, String table) {
        DataSourceEntity dataSourceEntity = dataSourceDao.getById(id);
        File driverPathFile = SpringUtil.getBean(DriverLoadService.class)
                .getVersionDriverFile(dataSourceEntity.getType(),
                        dataSourceEntity.getVersion());
        String driverPath = driverPathFile.getAbsolutePath();
        ProductTypeEnum productType = dataSourceEntity.getType();
        List<MetadataColumnResponse> result = new ArrayList<>();
        HikariDataSource ds = DataSourceUtils.getHikariDataSource(dataSourceEntity, driverPath);
        try (Connection connection = ds.getConnection()) {
            String catalogName = productType.getContext().isHasCatalogAndSchema()
                    ? connection.getCatalog()
                    : getAdapter(productType, connection, schema).getLeft();
            String schemaName = getAdapter(productType, connection, schema).getRight();
            try (ResultSet rs = connection.getMetaData()
                    .getColumns(catalogName, schemaName, table, null)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    String remarks = rs.getString("REMARKS");
                    result.add(MetadataColumnResponse.builder()
                            .name(columnName).type(typeName).remarks(remarks)
                            .build());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private void validJdbcUrlFormat(DataSourceEntity conn) {
        String typeName = conn.getType().getName().toUpperCase();
        ProductTypeEnum supportDbType = ProductTypeEnum.valueOf(typeName);
        if (!conn.getUrl().startsWith(supportDbType.getUrlPrefix())) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_JDBC_URL, conn.getUrl());
        }
        if (ProductTypeEnum.HTTP == supportDbType) {
            return;
        }

        for (int i = 0; i < supportDbType.getUrl().length; ++i) {
            String pattern = supportDbType.getUrl()[i];
            Matcher matcher = JdbcUrlUtils.getPattern(pattern).matcher(conn.getUrl());
            if (!matcher.matches()) {
                if (i == supportDbType.getUrl().length - 1) {
                    throw new CommonException(ResponseErrorCode.ERROR_INVALID_JDBC_URL, conn.getUrl());
                }
            } else {
                if (supportDbType.hasDatabaseName() && StringUtils.isBlank(matcher.group("database"))) {
                    throw new CommonException(ResponseErrorCode.ERROR_INVALID_JDBC_URL,
                            "datasource.db.name.missing", conn.getUrl());
                }
                if (supportDbType.hasFilePath() && StringUtils.isBlank(matcher.group("file"))) {
                    throw new CommonException(ResponseErrorCode.ERROR_INVALID_JDBC_URL,
                            "datasource.file.path.missing", conn.getUrl());
                }

                break;
            }
        }
    }

    private Pair<String, String> getAdapter(ProductTypeEnum productType, Connection connection, String schema) {
        if (null != productType.getContext().getAdapter()) {
            return productType.getContext().getAdapter().apply(schema);
        }
        return getAdapter(connection, schema);
    }

    @SneakyThrows
    private Pair<String, String> getAdapter(Connection connection, String schema) {
        boolean hasCatalogLayer = hasCatalogLayer(connection);
        boolean hasSchemaLayer = hasSchemaLayer(connection);
        if (hasCatalogLayer) {
            if (hasSchemaLayer) {
                return Pair.of(connection.getCatalog(), schema);
            } else {
                return Pair.of(schema, null);
            }
        } else {
            return Pair.of(null, schema);
        }
    }

    private boolean hasCatalogLayer(Connection conn) {
        try (ResultSet catalogs = conn.getMetaData().getCatalogs()) {
            return catalogs.next();
        } catch (SQLException e) {
            throw new RuntimeException("Detect has catalog layer failed:" + e.getMessage());
        }
    }

    private boolean hasSchemaLayer(Connection conn) {
        try {
            DatabaseMetaData dbMeta = conn.getMetaData();
            try (ResultSet catalogRs = dbMeta.getCatalogs()) {
                while (catalogRs.next()) {
                    String name = catalogRs.getString("TABLE_CAT");
                    try (ResultSet schemaRs = dbMeta.getSchemas(name, null)) {
                        if (schemaRs.next()) {
                            return true;
                        }
                    }
                }
            }
            try (ResultSet schemaRs = dbMeta.getSchemas()) {
                return schemaRs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Detect has schema layer failed:" + e.getMessage());
        }
    }

}
