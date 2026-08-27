// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cs.common.dto.*;
import com.cs.core.dto.ApiAccessLogBasicResponse;
import com.cs.persistence.dao.AppClientDao;
import com.cs.persistence.entity.*;
import com.cs.persistence.mapper.*;
import com.cs.persistence.util.PageUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OverviewService {

    @Resource
    private AccessRecordMapper accessRecordMapper;

    @Resource
    private DataSourceMapper dataSourceMapper;

    @Resource
    private ApiAssignmentMapper apiAssignmentMapper;

    public Map<String, Integer> count() {
        return accessRecordMapper.selectCount();
    }

    public List<DateCount> trend(Integer days) {
        return accessRecordMapper.getDailyTrend(days > 0 ? days - 1 : days);
    }

    public List<NameCount> httpStatus(Integer days) {
        return accessRecordMapper.getHttpStatusCount(days > 0 ? days - 1 : days);
    }

    public List<NameCount> topPath(Integer days, Integer n) {
        return accessRecordMapper.getTopPathAccess(days > 0 ? days - 1 : days, n);
    }

    public List<NameCount> topAddr(Integer days, Integer n) {
        return accessRecordMapper.getTopIpAddrAccess(days > 0 ? days - 1 : days, n);
    }

    public List<NameCount> topClient(Integer days, Integer n) {
        return accessRecordMapper.getTopAppClientAccess(days > 0 ? days - 1 : days, n);
    }

    public List<NameCount> datasourceTypeRatio() {
        return dataSourceMapper.getDatasourceTypeRatio();
    }

    public List<NameCount> engineRatio() {
        return apiAssignmentMapper.getEngineRatio();
    }

    public List<NameCount> datasourceApiCount() {
        return apiAssignmentMapper.getDatasourceApiCount();
    }

    public List<NameCount> methodRatio() {
        return apiAssignmentMapper.getMethodRatio();
    }

    public List<NameCount> moduleApiCount() {
        return apiAssignmentMapper.getModuleApiCount(15);
    }

    public List<NameCount> apiStatusRatio(Long apiId, Integer days) {
        return accessRecordMapper.getApiHttpStatusCount(apiId, days > 0 ? days - 1 : days);
    }

    public List<DateCount> apiDailyTrend(Long apiId, Integer days) {
        return accessRecordMapper.getApiDailyTrend(apiId, days > 0 ? days - 1 : days);
    }

    public List<HourCount> apiHourlyTrend(Long apiId, String date) {
        return accessRecordMapper.getApiHourlyTrend(apiId, date);
    }

    public PageResult<ApiAccessLogBasicResponse> pageByApiId(Long apiId, Integer page, Integer size,
                                                             Integer statusCode, String startTime, String endTime) {
        Map<String, String> map = SpringUtil.getBean(AppClientDao.class)
                .listAll().stream()
                .collect(
                        Collectors.toMap(
                                AppClientEntity::getAppKey,
                                one -> String.format("[%d]%s(%s)", one.getId(), one.getName(), one.getAppKey())));
        LambdaQueryWrapper<AccessRecordEntity> query = Wrappers.<AccessRecordEntity>lambdaQuery()
                .eq(AccessRecordEntity::getApiId, apiId);
        if (statusCode != null) {
            query.eq(AccessRecordEntity::getStatus, statusCode);
        }
        if (startTime != null && !startTime.isEmpty()) {
            query.ge(AccessRecordEntity::getCreateTime, startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            query.le(AccessRecordEntity::getCreateTime, endTime);
        }
        query.orderByDesc(AccessRecordEntity::getId);
        return PageUtils.getPage(() ->
                        accessRecordMapper.selectList(query).stream().map(
                                record -> ApiAccessLogBasicResponse.builder()
                                        .id(record.getId())
                                        .status(record.getStatus())
                                        .duration(record.getDuration())
                                        .ipAddr(record.getIpAddr())
                                        .userAgent(record.getUserAgent())
                                        .clientApp(map.get(record.getClientKey()))
                                        .parameters(record.getParameters())
                                        .exception(record.getException())
                                        .createTime(record.getCreateTime())
                                        .executorAddr(record.getExecutorAddr())
                                        .gatewayAddr(record.getGatewayAddr())
                                        .build()
                        ).collect(Collectors.toList())
                , page, size);
    }
}
