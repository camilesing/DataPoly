// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.core.driver.DriverLoadService;
import com.cs.core.dto.*;
import com.cs.core.service.DataSourceService;
import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Api(tags = {"数据源管理接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/datasource")
public class DataSourceController {

    @Resource
    private DataSourceService datasourceService;
    @Resource
    private DriverLoadService driverLoadService;

    @ApiOperation(value = "数据库类型")
    @GetMapping(value = "/types", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity getTypes() {
        return ResultEntity.success(datasourceService.getTypes());
    }

    @ApiOperation(value = "数据库驱动")
    @GetMapping(value = "/{type}/drivers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity getDrivers(@PathVariable("type") ProductTypeEnum type) {
        return ResultEntity.success(driverLoadService.getDrivers(type));
    }

    @ApiOperation(value = "连接预测试")
    @PostMapping(value = "/preTest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity preTest(@Valid @RequestBody DataSourceBaseRequest request) {
        datasourceService.preTest(request);
        return ResultEntity.success();
    }

    @ApiOperation(value = "数据源列表")
    @PostMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<DatasourceDetailResponse> getConnections(@RequestBody EntitySearchRequest request) {
        return datasourceService.searchList(request);
    }

    @ApiOperation(value = "数据源详情")
    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity getDetail(@PathVariable("id") Long id) {
        return ResultEntity.success(datasourceService.getDetailById(id));
    }

    @ApiOperation(value = "数据源测试连接")
    @GetMapping(value = "/test/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity test(@PathVariable("id") Long id) {
        datasourceService.testDataSource(id);
        return ResultEntity.success();
    }

    @ApiOperation(value = "添加数据源")
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity create(@Valid @RequestBody DataSourceSaveRequest request) {
        datasourceService.createDataSource(request);
        return ResultEntity.success();
    }

    @ApiOperation(value = "修改数据源")
    @PostMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity update(@Valid @RequestBody DataSourceSaveRequest request) {
        datasourceService.updateDataSource(request);
        return ResultEntity.success();
    }

    @ApiOperation(value = "删除数据源")
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity delete(@PathVariable("id") Long id) {
        datasourceService.deleteDataSource(id);
        return ResultEntity.success();
    }

    @ApiOperation(value = "数据源名称列表")
    @GetMapping(value = "/list/name", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<EntityIdNameResponse> getNameList(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return datasourceService.getDataSourceNames(page, size);
    }

    @ApiOperation(value = "查询数据源的Schema列表")
    @GetMapping(value = "/schemas/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity getSchemas(@PathVariable("id") Long id) {
        return ResultEntity.success(datasourceService.getDatasourceSchemas(id));
    }

    @ApiOperation(value = "查询数据源在指定Schema下的所有物理表列表")
    @GetMapping(value = "/tables/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity getSchemaTables(@PathVariable("id") Long id,
                                        @RequestParam("schema") String schema) {
        return ResultEntity.success(datasourceService.getSchemaTables(id, schema));
    }

    @ApiOperation(value = "查询数据源在指定Schema下的所有视图表列表")
    @GetMapping(value = "/views/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity getSchemaViews(@PathVariable("id") Long id,
                                       @RequestParam("schema") String schema) {
        return ResultEntity.success(datasourceService.getSchemaViews(id, schema));
    }

    @ApiOperation(value = "查询数据源指定表的列信息")
    @GetMapping(value = "/columns/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity getTableColumns(@PathVariable("id") Long id,
                                        @RequestParam("schema") String schema, @RequestParam("table") String table) {
        return ResultEntity.success(datasourceService.getTableColumns(id, schema, table));
    }

}
