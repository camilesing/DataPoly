// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.ResultEntity;
import com.cs.common.util.PomVersionUtils;
import io.swagger.annotations.Api;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Api(tags = {"存活健康接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/health")
public class AliveHealthController {

    @GetMapping(value = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<String> getProjectVersion() {
        return ResultEntity.success(PomVersionUtils.getCachedProjectVersion());
    }
}
