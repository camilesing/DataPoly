// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.service.ApiModuleService;
import com.cs.persistence.entity.ApiModuleEntity;
import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@Api(tags = {"模块管理接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/module")
public class ApiModuleController {

    @Resource
    private ApiModuleService apiModuleService;

    @ApiOperation(value = "添加模块")
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity create(@Valid @NotBlank(message = "name不能为空") @RequestParam("name") String name) {
        apiModuleService.createModule(name);
        return ResultEntity.success();
    }

    @ApiOperation(value = "更新模块")
    @PostMapping(value = "/update/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity update(@PathVariable("id") Long id,
                               @Valid @NotBlank(message = "name不能为空") @RequestParam("name") String name) {
        apiModuleService.updateModule(id, name);
        return ResultEntity.success();
    }

    @ApiOperation(value = "删除模块")
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity delete(@PathVariable("id") Long id) {
        apiModuleService.deleteModule(id);
        return ResultEntity.success();
    }

    @ApiOperation(value = "模块列表")
    @PostMapping(value = "/listAll", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<ApiModuleEntity> listAll(@RequestBody EntitySearchRequest request) {
        return apiModuleService.listAll(request);
    }

    @ApiOperation(value = "模块接口树")
    @GetMapping(value = "/moduleTree/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity moduleTree(@PathVariable("id") Long groupId) {
        return ResultEntity.success(apiModuleService.moduleTree(groupId));
    }
}
