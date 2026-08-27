// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.core.dto.ApiAccessLogBasicResponse;
import com.cs.core.service.OverviewService;
import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Api(tags = {"统计相关接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/overview")
public class OverviewController {

    @Resource
    private OverviewService overviewService;

    @ApiOperation(value = "计数统计")
    @GetMapping(value = "/counter", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity count() {
        return ResultEntity.success(overviewService.count());
    }

    @ApiOperation(value = "趋势统计")
    @GetMapping(value = "/trend/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<DateCount>> trend(@PathVariable("days") Integer days) {
        return ResultEntity.success(overviewService.trend(days));
    }

    @ApiOperation(value = "HTTP状态统计")
    @GetMapping(value = "/ratio/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity httpStatus(@PathVariable("days") Integer days) {
        return ResultEntity.success(overviewService.httpStatus(days));
    }

    @ApiOperation(value = "路径TOP")
    @GetMapping(value = "/top/path/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> topPath(@PathVariable("days") Integer days, @RequestParam("n") Integer n) {
        return ResultEntity.success(overviewService.topPath(days, n));
    }

    @ApiOperation(value = "地址TOP")
    @GetMapping(value = "/top/addr/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> topAddr(@PathVariable("days") Integer days, @RequestParam("n") Integer n) {
        return ResultEntity.success(overviewService.topAddr(days, n));
    }

    @ApiOperation(value = "客户端TOP")
    @GetMapping(value = "/top/client/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> topClient(@PathVariable("days") Integer days, @RequestParam("n") Integer n) {
        return ResultEntity.success(overviewService.topClient(days, n));
    }

    @ApiOperation(value = "接口调用日志")
    @GetMapping(value = "/log/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<ApiAccessLogBasicResponse> callLogs(@PathVariable("id") Long id, @RequestParam("page") Integer page,
                                                          @RequestParam("size") Integer size,
                                                          @RequestParam(value = "statusCode", required = false) Integer statusCode,
                                                          @RequestParam(value = "startTime", required = false) String startTime,
                                                          @RequestParam(value = "endTime", required = false) String endTime) {
        return overviewService.pageByApiId(id, page, size, statusCode, startTime, endTime);
    }

    @ApiOperation(value = "数据源类别占比")
    @GetMapping(value = "/datasource-type-ratio", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> datasourceTypeRatio() {
        return ResultEntity.success(overviewService.datasourceTypeRatio());
    }

    @ApiOperation(value = "引擎类型占比")
    @GetMapping(value = "/engine-ratio", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> engineRatio() {
        return ResultEntity.success(overviewService.engineRatio());
    }

    @ApiOperation(value = "数据源接口数量")
    @GetMapping(value = "/datasource-api-count", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> datasourceApiCount() {
        return ResultEntity.success(overviewService.datasourceApiCount());
    }

    @ApiOperation(value = "接口方法占比")
    @GetMapping(value = "/method-ratio", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> methodRatio() {
        return ResultEntity.success(overviewService.methodRatio());
    }

    @ApiOperation(value = "模块接口数量")
    @GetMapping(value = "/module-api-count", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> moduleApiCount() {
        return ResultEntity.success(overviewService.moduleApiCount());
    }

    @ApiOperation(value = "API状态码占比")
    @GetMapping(value = "/api/{apiId}/status-ratio/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<NameCount>> apiStatusRatio(@PathVariable("apiId") Long apiId,
                                                        @PathVariable("days") Integer days) {
        return ResultEntity.success(overviewService.apiStatusRatio(apiId, days));
    }

    @ApiOperation(value = "API每日调用趋势")
    @GetMapping(value = "/api/{apiId}/daily-trend/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<DateCount>> apiDailyTrend(@PathVariable("apiId") Long apiId,
                                                       @PathVariable("days") Integer days) {
        return ResultEntity.success(overviewService.apiDailyTrend(apiId, days));
    }

    @ApiOperation(value = "API 24小时调用趋势")
    @GetMapping(value = "/api/{apiId}/hourly-trend", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<HourCount>> apiHourlyTrend(@PathVariable("apiId") Long apiId,
                                                        @RequestParam("date") String date) {
        return ResultEntity.success(overviewService.apiHourlyTrend(apiId, date));
    }
}
