// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.ResultEntity;
import com.cs.core.service.SystemParamService;
import io.swagger.annotations.Api;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Api(tags = {"参数管理接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/param")
public class SystemParamController {

    @Resource
    private SystemParamService systemParamService;

    @GetMapping(value = "/value/query", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity getByParamKey(@RequestParam("key") String key) {
        return ResultEntity.success(systemParamService.getByParamKey(key));
    }

    @PostMapping(value = "/value/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity updateByParamKey(@RequestParam("key") String key,
                                         @RequestParam("value") String value) {
        systemParamService.updateByParamKey(key, value);
        return ResultEntity.success();
    }
}
