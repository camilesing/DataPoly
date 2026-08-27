// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskService;
import com.cs.core.dto.*;
import com.cs.core.service.ApiAssignmentService;
import com.cs.persistence.util.PageUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Frontend surface for asynchronous data tasks: SQL-level task definitions with
 * input-parameter declarations, output reshaping (naming strategy / column aliases /
 * order / type formats), synchronous previews and job submission with status polling
 * for completion. Delivery targets themselves come from DataTaskSink extensions.
 */
@Api(tags = {"数据任务接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/data-task")
public class DataTaskController {

    @Resource
    private DataTaskService dataTaskService;
    @Resource
    private ApiAssignmentService apiAssignmentService;

    @ApiOperation(value = "解析SQL中的入参列表")
    @PostMapping(value = "/parse", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity parse(@RequestParam("sql") String sql) {
        return ResultEntity.success(apiAssignmentService.parseSqlParams(sql));
    }

    @ApiOperation(value = "创建数据任务")
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity create(@RequestBody DataTaskSaveRequest request) {
        return ResultEntity.success(dataTaskService.create(request));
    }

    @ApiOperation(value = "更新数据任务")
    @PostMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity update(@RequestBody DataTaskSaveRequest request) {
        dataTaskService.update(request);
        return ResultEntity.success();
    }

    @ApiOperation(value = "查看数据任务")
    @GetMapping(value = "/detail/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity detail(@PathVariable("id") Long id) {
        return ResultEntity.success(dataTaskService.detail(id));
    }

    @ApiOperation(value = "删除数据任务")
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity delete(@PathVariable("id") Long id) {
        dataTaskService.delete(id);
        return ResultEntity.success();
    }

    @ApiOperation(value = "查询数据任务列表")
    @PostMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<DataTaskBaseResponse> list(@RequestBody DataTaskSearchRequest request) {
        return PageUtils.getPage(dataTaskService.list(request),
                null == request.getPage() ? 0 : request.getPage(),
                null == request.getSize() ? 0 : request.getSize());
    }

    @ApiOperation(value = "试运行（不产生执行记录，返回 shaping 后的行）")
    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity preview(@RequestBody DataTaskRunRequest request) {
        Map<String, Object> view = dataTaskService.preview(request);
        return ResultEntity.success(view);
    }

    @ApiOperation(value = "提交异步执行")
    @PostMapping(value = "/submit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity submit(@RequestBody DataTaskRunRequest request, HttpServletRequest servletRequest) {
        Object username = servletRequest.getAttribute("username");
        Long jobId = dataTaskService.submit(request, null == username ? null : String.valueOf(username));
        return ResultEntity.success(jobId);
    }

    @ApiOperation(value = "执行记录详情（前端轮询此接口获取完成状态与产物地址）")
    @GetMapping(value = "/job/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity job(@PathVariable("id") Long id) {
        return ResultEntity.success(dataTaskService.jobView(id));
    }

    @ApiOperation(value = "执行记录搜索")
    @PostMapping(value = "/jobs/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<DataTaskJobView> jobs(@RequestBody DataTaskJobSearchRequest request) {
        return PageUtils.getPage(dataTaskService.jobViews(request),
                null == request.getPage() ? 0 : request.getPage(),
                null == request.getSize() ? 0 : request.getSize());
    }

    @ApiOperation(value = "取消执行（排队中直接取消；运行中的为协作式取消）")
    @PostMapping(value = "/job/{id}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity cancel(@PathVariable("id") Long id) {
        dataTaskService.cancel(id);
        return ResultEntity.success();
    }
}
