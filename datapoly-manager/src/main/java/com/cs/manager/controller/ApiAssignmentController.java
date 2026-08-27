// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.common.enums.*;
import com.cs.core.dto.*;
import com.cs.core.service.*;
import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Api(tags = {"API配置接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/assignment")
public class ApiAssignmentController {

    @Resource
    private ApiAssignmentService apiAssignmentService;
    @Resource
    private ExportImportService exportImportService;

    @ApiOperation(value = "获取自动提示列表")
    @GetMapping(value = "/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity completions() {
        return ResultEntity.success(apiAssignmentService.completions());
    }

    @ApiOperation(value = "响应属性命名策略")
    @GetMapping(value = "/response-naming-strategy", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity responseNamingStrategy() {
        return ResultEntity.success(
                Arrays.stream(NamingStrategyEnum.values())
                        .map(
                                e ->
                                        NameValueBaseResponse.builder()
                                                .key(e.name())
                                                .value(e.getDescription())
                                                .build()
                        ).collect(Collectors.toList())
        );
    }

    @ApiOperation(value = "响应数据类型格式")
    @GetMapping(value = "/response-type-format", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity responseTypeFormat() {
        return ResultEntity.success(
                Arrays.stream(DataTypeFormatEnum.values())
                        .map(
                                e ->
                                        NameValueRemarkResponse.builder()
                                                .key(e.name())
                                                .value(e.getDefault())
                                                .remark(e.getRemark())
                                                .build()
                        ).collect(Collectors.toList())
        );
    }

    @ApiOperation(value = "获取SQL中的入参列表")
    @PostMapping(value = "/parse", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity parse(@RequestParam("sql") String sql) {
        return ResultEntity.success(apiAssignmentService.parseSqlParams(sql));
    }

    @ApiOperation(value = "调试API配置")
    @PostMapping(value = "/debug", produces = MediaType.APPLICATION_JSON_VALUE)
    public void debug(@Valid @RequestBody ApiDebugExecuteRequest request, HttpServletResponse response)
            throws IOException {
        apiAssignmentService.debugExecute(request, response);
    }

    @ApiOperation(value = "添加API配置")
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity create(@Valid @RequestBody ApiAssignmentSaveRequest request) {
        Long id = apiAssignmentService.createAssignment(request);
        return ResultEntity.success(id);
    }

    @ApiOperation(value = "更新API配置")
    @PostMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity update(@Valid @RequestBody ApiAssignmentSaveRequest request) {
        apiAssignmentService.updateAssignment(request);
        return ResultEntity.success();
    }

    @ApiOperation(value = "查看API配置")
    @GetMapping(value = "/detail/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity detail(@PathVariable("id") Long id) {
        return ResultEntity.success(apiAssignmentService.detailAssignment(id));
    }

    @ApiOperation(value = "删除API配置")
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity delete(@PathVariable("id") Long id) {
        apiAssignmentService.deleteAssignment(id);
        return ResultEntity.success();
    }

    @ApiOperation(value = "查询API配置列表")
    @PostMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<ApiAssignmentBaseResponse> listAll(@RequestBody AssignmentSearchRequest request) {
        return apiAssignmentService.listAll(request);
    }

    @ApiOperation(value = "上线接口搜索")
    @PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<ApiAssignmentBaseResponse> search(@RequestBody ApiOnlineSearchRequest request) {
        return apiAssignmentService.search(request);
    }

    @ApiOperation(value = "批量更新授权组")
    @PostMapping(value = "/group/{groupId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity updateGroup(@PathVariable("groupId") Long groupId, @RequestBody List<Long> ids) {
        apiAssignmentService.updateGroup(groupId, ids);
        return ResultEntity.success();
    }

    @ApiOperation(value = "发布版本")
    @PutMapping(value = "/publish", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity publish(@Valid @RequestBody AssignmentPublishRequest request) {
        apiAssignmentService.publish(request);
        return ResultEntity.success();
    }

    @ApiOperation(value = "上线")
    @PutMapping(value = "/deploy/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity deploy(@PathVariable("id") Long id,
                               @RequestParam(value = "commitId", required = false) Long commitId) {
        apiAssignmentService.deployAssignment(id, commitId);
        return ResultEntity.success();
    }

    @ApiOperation(value = "下线")
    @PutMapping(value = "/retire/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity retire(@PathVariable("id") Long id) {
        apiAssignmentService.retireAssignment(id);
        return ResultEntity.success();
    }

    @ApiOperation(value = "批量导出API配置")
    @PostMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public void exportAssignments(@Valid @NotEmpty @RequestBody List<Long> ids, HttpServletResponse response) {
        exportImportService.exportAssignments(ids, response);
    }

    @ApiOperation(value = "批量导入API配置")
    @PostMapping(value = "/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<String> importAssignments(@RequestPart(value = "file") MultipartFile file) throws IOException {
        exportImportService.importAssignments(file);
        return ResultEntity.success();
    }
}
