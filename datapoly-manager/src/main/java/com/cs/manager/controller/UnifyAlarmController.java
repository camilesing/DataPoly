// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.ResultEntity;
import com.cs.core.dto.*;
import com.cs.core.executor.UnifyAlarmOpsService;
import com.cs.persistence.entity.UnifyAlarmEntity;
import io.swagger.annotations.Api;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Api(tags = {"告警配置接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/alarm")
public class UnifyAlarmController {

    @Resource
    private UnifyAlarmOpsService unifyAlarmOpsService;

    @GetMapping(value = "/detail", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<UnifyAlarmEntity> getUnifyAlarmConfig() {
        return ResultEntity.success(unifyAlarmOpsService.getUnifyAlarmConfig());
    }

    @GetMapping(value = "/example", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameValueBaseResponse>> getExampleDataModel() {
        return ResultEntity.success(unifyAlarmOpsService.getExampleDataModel());
    }

    @PostMapping(value = "/test", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity testUnifyAlarmConfig(@Valid @RequestBody TestAlarmConfigRequest request) {
        unifyAlarmOpsService.testUnifyAlarmConfig(request);
        return ResultEntity.success();
    }

    @PostMapping(value = "/save", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity saveUnifyAlarmConfig(@Valid @RequestBody UpdateAlarmConfigRequest request) {
        unifyAlarmOpsService.updateUnifyAlarmConfig(request);
        return ResultEntity.success();
    }
}
