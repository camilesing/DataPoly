// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.core.dto.ApiAccessLogBasicResponse;
import com.cs.core.service.OverviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@Tag(name = "统计相关接口")
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/overview")
public class OverviewController {

    @Resource
    private OverviewService overviewService;

    @Operation(summary = "计数统计")
    @GetMapping(value = "/counter", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity count() {
        return ResultEntity.success(overviewService.count());
    }

    @Operation(summary = "趋势统计")
    @GetMapping(value = "/trend/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<DateCount>> trend(@PathVariable("days") Integer days) {
        return ResultEntity.success(overviewService.trend(days));
    }

    @Operation(summary = "HTTP状态统计")
    @GetMapping(value = "/ratio/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity httpStatus(@PathVariable("days") Integer days) {
        return ResultEntity.success(overviewService.httpStatus(days));
    }

    @Operation(summary = "路径TOP")
    @GetMapping(value = "/top/path/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> topPath(@PathVariable("days") Integer days, @RequestParam("n") Integer n) {
        return ResultEntity.success(overviewService.topPath(days, n));
    }

    @Operation(summary = "地址TOP")
    @GetMapping(value = "/top/addr/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> topAddr(@PathVariable("days") Integer days, @RequestParam("n") Integer n) {
        return ResultEntity.success(overviewService.topAddr(days, n));
    }

    @Operation(summary = "客户端TOP")
    @GetMapping(value = "/top/client/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> topClient(@PathVariable("days") Integer days, @RequestParam("n") Integer n) {
        return ResultEntity.success(overviewService.topClient(days, n));
    }

    @Operation(summary = "接口调用日志")
    @GetMapping(value = "/log/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<ApiAccessLogBasicResponse> callLogs(@PathVariable("id") Long id, @RequestParam("page") Integer page,
                                                          @RequestParam("size") Integer size,
                                                          @RequestParam(value = "statusCode", required = false) Integer statusCode,
                                                          @RequestParam(value = "startTime", required = false) String startTime,
                                                          @RequestParam(value = "endTime", required = false) String endTime) {
        return overviewService.pageByApiId(id, page, size, statusCode, startTime, endTime);
    }

    @Operation(summary = "数据源类别占比")
    @GetMapping(value = "/datasource-type-ratio", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> datasourceTypeRatio() {
        return ResultEntity.success(overviewService.datasourceTypeRatio());
    }

    @Operation(summary = "引擎类型占比")
    @GetMapping(value = "/engine-ratio", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> engineRatio() {
        return ResultEntity.success(overviewService.engineRatio());
    }

    @Operation(summary = "数据源接口数量")
    @GetMapping(value = "/datasource-api-count", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> datasourceApiCount() {
        return ResultEntity.success(overviewService.datasourceApiCount());
    }

    @Operation(summary = "接口方法占比")
    @GetMapping(value = "/method-ratio", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> methodRatio() {
        return ResultEntity.success(overviewService.methodRatio());
    }

    @Operation(summary = "模块接口数量")
    @GetMapping(value = "/module-api-count", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> moduleApiCount() {
        return ResultEntity.success(overviewService.moduleApiCount());
    }

    @Operation(summary = "API状态码占比")
    @GetMapping(value = "/api/{apiId}/status-ratio/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> apiStatusRatio(@PathVariable("apiId") Long apiId,
                                                        @PathVariable("days") Integer days) {
        return ResultEntity.success(overviewService.apiStatusRatio(apiId, days));
    }

    @Operation(summary = "API每日调用趋势")
    @GetMapping(value = "/api/{apiId}/daily-trend/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<DateCount>> apiDailyTrend(@PathVariable("apiId") Long apiId,
                                                       @PathVariable("days") Integer days) {
        return ResultEntity.success(overviewService.apiDailyTrend(apiId, days));
    }

    @Operation(summary = "API 24小时调用趋势")
    @GetMapping(value = "/api/{apiId}/hourly-trend", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<HourCount>> apiHourlyTrend(@PathVariable("apiId") Long apiId,
                                                        @RequestParam("date") String date) {
        return ResultEntity.success(overviewService.apiHourlyTrend(apiId, date));
    }
}
