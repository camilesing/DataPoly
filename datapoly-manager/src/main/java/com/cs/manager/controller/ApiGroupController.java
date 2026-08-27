// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.service.ApiGroupService;
import com.cs.persistence.entity.ApiGroupEntity;
import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@Api(tags = {"分组管理接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/group")
public class ApiGroupController {

    @Resource
    private ApiGroupService apiGroupService;

    @ApiOperation(value = "添加分组")
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity create(@Valid @NotBlank(message = "name不能为空") @RequestParam("name") String name) {
        apiGroupService.createGroup(name);
        return ResultEntity.success();
    }

    @ApiOperation(value = "更新分组")
    @PostMapping(value = "/update/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity update(@PathVariable("id") Long id,
                               @Valid @NotBlank(message = "name不能为空") @RequestParam("name") String name) {
        apiGroupService.updateGroup(id, name);
        return ResultEntity.success();
    }

    @ApiOperation(value = "删除分组")
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity delete(@PathVariable("id") Long id) {
        apiGroupService.deleteGroup(id);
        return ResultEntity.success();
    }

    @ApiOperation(value = "分组列表")
    @PostMapping(value = "/listAll", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<ApiGroupEntity> listAll(@RequestBody EntitySearchRequest request) {
        return apiGroupService.listAll(request);
    }
}
