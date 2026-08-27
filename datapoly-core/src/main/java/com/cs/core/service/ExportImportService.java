// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.core.io.IoUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.core.dto.*;
import com.cs.persistence.dao.*;
import com.cs.persistence.entity.*;
import com.cs.persistence.util.JsonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExportImportService {

    private final String charsetName = StandardCharsets.UTF_8.name();

    @Resource
    private ApiAssignmentDao apiAssignmentDao;
    @Resource
    private DataSourceDao dataSourceDao;
    @Resource
    private ApiGroupDao apiGroupDao;
    @Resource
    private ApiModuleDao apiModuleDao;
    @Resource
    private ApiAssignmentService apiAssignmentService;

    public void exportAssignments(List<Long> ids, HttpServletResponse response) {
        Map<Long, DataSourceEntity> dataSourceEntityMap = dataSourceDao.listAll()
                .stream().collect(Collectors.toMap(DataSourceEntity::getId, Function.identity()));
        Map<Long, ApiGroupEntity> apiGroupEntityMap = apiGroupDao.listAll()
                .stream().collect(Collectors.toMap(ApiGroupEntity::getId, Function.identity()));
        Map<Long, ApiModuleEntity> apiModuleEntityMap = apiModuleDao.listAll()
                .stream().collect(Collectors.toMap(ApiModuleEntity::getId, Function.identity()));
        List<ExpImpAssignmentModel> assignmentEntityList = new ArrayList<>();
        for (Long id : ids) {
            ApiAssignmentEntity assignmentEntity = apiAssignmentDao.getById(id, true);
            if (null != assignmentEntity) {
                DataSourceEntity dsEntity = dataSourceEntityMap.get(assignmentEntity.getDatasourceId());
                ApiGroupEntity groupEntity = apiGroupEntityMap.get(assignmentEntity.getGroupId());
                ApiModuleEntity moduleEntity = apiModuleEntityMap.get(assignmentEntity.getModuleId());
                ExpImpAssignmentModel model = buildAssignmentModel(assignmentEntity, dsEntity, groupEntity, moduleEntity);
                assignmentEntityList.add(model);
            }
        }
        String jsonArrays = JsonUtils.toJsonString(assignmentEntityList);
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=datapoly_assignments.json");
        try (OutputStream os = response.getOutputStream()) {
            IoUtil.write(os, false, jsonArrays.getBytes(charsetName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ExpImpAssignmentModel buildAssignmentModel(ApiAssignmentEntity assignmentEntity,
                                                       DataSourceEntity dataSourceEntity, ApiGroupEntity apiGroupEntity, ApiModuleEntity apiModuleEntity) {
        ExpImpDataSourceModel dataSourceModel = new ExpImpDataSourceModel();
        dataSourceModel.setName(dataSourceEntity.getName());
        dataSourceModel.setType(dataSourceEntity.getType());
        dataSourceModel.setVersion(dataSourceEntity.getVersion());
        dataSourceModel.setDriver(dataSourceEntity.getDriver());
        dataSourceModel.setUrl(dataSourceEntity.getUrl());
        dataSourceModel.setUsername(dataSourceEntity.getUsername());
        dataSourceModel.setPassword(dataSourceEntity.getPassword());

        List<String> contextList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(assignmentEntity.getContextList())) {
            for (ApiContextEntity contextEntity : assignmentEntity.getContextList()) {
                contextList.add(contextEntity.getSqlText());
            }
        }

        ExpImpAssignmentModel model = new ExpImpAssignmentModel();
        model.setGroupName(apiGroupEntity.getName());
        model.setModuleName(apiModuleEntity.getName());
        model.setDataSourceModel(dataSourceModel);
        model.setName(assignmentEntity.getName());
        model.setDescription(assignmentEntity.getDescription());
        model.setMethod(assignmentEntity.getMethod());
        model.setContentType(assignmentEntity.getContentType());
        model.setPath(assignmentEntity.getPath());
        model.setParams(assignmentEntity.getParams());
        model.setOutputs(assignmentEntity.getOutputs());
        model.setOpen(assignmentEntity.getOpen());
        model.setAlarm(assignmentEntity.getAlarm());
        model.setContentType(assignmentEntity.getContentType());
        model.setEngine(assignmentEntity.getEngine());
        model.setResponseFormat(assignmentEntity.getResponseFormat());
        model.setNamingStrategy(assignmentEntity.getNamingStrategy());
        model.setFlowStatus(assignmentEntity.getFlowStatus());
        model.setFlowGrade(assignmentEntity.getFlowGrade());
        model.setFlowCount(assignmentEntity.getFlowCount());
        model.setCacheKeyType(assignmentEntity.getCacheKeyType());
        model.setCacheKeyExpr(assignmentEntity.getCacheKeyExpr());
        model.setCacheExpireSeconds(assignmentEntity.getCacheExpireSeconds());
        model.setContextList(contextList);

        return model;
    }

    public void importAssignments(MultipartFile file) throws IOException {
        String content = IoUtil.read(file.getInputStream(), charsetName);
        List<ExpImpAssignmentModel> apiModels = JsonUtils.toBeanList(content, ExpImpAssignmentModel.class);
        List<DataSourceEntity> dsEntityList = dataSourceDao.listAll();
        List<ApiGroupEntity> groupEntityList = apiGroupDao.listAll();
        List<ApiModuleEntity> moduleEntityList = apiModuleDao.listAll();
        ExportImportService service = SpringUtil.getBean(this.getClass());
        service.importAllInTransaction(apiModels, dsEntityList, groupEntityList, moduleEntityList);
    }

    @Transactional(rollbackFor = Exception.class)
    public void importAllInTransaction(List<ExpImpAssignmentModel> apiModels, List<DataSourceEntity> dsEntityList,
                                       List<ApiGroupEntity> groupEntityList, List<ApiModuleEntity> moduleEntityList) {
        for (ExpImpAssignmentModel model : apiModels) {
            Long dsId = mapDataSourceId(dsEntityList, model.getDataSourceModel());
            Long groupId = mapGroupId(groupEntityList, model.getGroupName());
            Long moduleId = mapModelId(moduleEntityList, model.getModuleName());
            ApiAssignmentSaveRequest apiAssignment = toApiAssignment(model, dsId, groupId, moduleId);
            apiAssignmentService.createAssignment(apiAssignment);
        }
    }

    private ApiAssignmentSaveRequest toApiAssignment(ExpImpAssignmentModel model, Long dsId, Long gId, Long mId) {
        List<DataTypeFormatMapValue> formatMap = new ArrayList<>();
        for (Map.Entry<DataTypeFormatEnum, String> entry : model.getResponseFormat().entrySet()) {
            formatMap.add(DataTypeFormatMapValue.builder().key(entry.getKey()).value(entry.getValue()).build());
        }
        ApiAssignmentSaveRequest request = new ApiAssignmentSaveRequest();
        request.setDatasourceId(dsId);
        request.setGroupId(gId);
        request.setModuleId(mId);
        request.setName(model.getName());
        request.setDescription(model.getDescription());
        request.setMethod(model.getMethod());
        request.setContentType(model.getContentType());
        request.setPath(model.getPath());
        request.setOpen(model.getOpen());
        request.setAlarm(model.getAlarm());
        request.setEngine(model.getEngine());
        request.setContextList(model.getContextList());
        request.setParams(model.getParams());
        request.setOutputs(model.getOutputs());
        request.setFormatMap(formatMap);
        request.setNamingStrategy(model.getNamingStrategy());
        request.setFlowStatus(model.getFlowStatus());
        request.setFlowGrade(model.getFlowGrade());
        request.setFlowCount(model.getFlowCount());
        request.setCacheKeyType(model.getCacheKeyType());
        request.setCacheKeyExpr(model.getCacheKeyExpr());
        request.setCacheExpireSeconds(model.getCacheExpireSeconds());
        return request;
    }

    private Long mapDataSourceId(List<DataSourceEntity> dsEntityList, ExpImpDataSourceModel dsModel) {
        Optional<DataSourceEntity> optionalDataSourceEntity = dsEntityList.stream()
                .filter(
                        ds ->
                                StringUtils.equals(ds.getType().name(), dsModel.getType().name())
                                        && StringUtils.equals(ds.getDriver(), dsModel.getDriver())
                                        && StringUtils.equals(ds.getUrl(), dsModel.getUrl()))
                .findFirst();
        if (optionalDataSourceEntity.isPresent()) {
            return optionalDataSourceEntity.get().getId();
        }
        DataSourceEntity dataSourceEntity = DataSourceEntity.builder()
                .name(dsModel.getName())
                .type(dsModel.getType())
                .driver(dsModel.getDriver())
                .version(dsModel.getVersion())
                .url(dsModel.getUrl())
                .username(dsModel.getUsername())
                .password(dsModel.getPassword())
                .build();
        dataSourceDao.insert(dataSourceEntity);
        dsEntityList.add(dataSourceEntity);
        return dataSourceEntity.getId();
    }

    private Long mapGroupId(List<ApiGroupEntity> groupEntityList, String groupName) {
        Optional<ApiGroupEntity> optionalApiGroupEntity = groupEntityList.stream()
                .filter(group -> StringUtils.equals(group.getName(), groupName))
                .findFirst();
        if (optionalApiGroupEntity.isPresent()) {
            return optionalApiGroupEntity.get().getId();
        }
        ApiGroupEntity apiGroupEntity = ApiGroupEntity.builder().name(groupName).build();
        apiGroupDao.insert(apiGroupEntity);
        groupEntityList.add(apiGroupEntity);
        return apiGroupEntity.getId();
    }

    private Long mapModelId(List<ApiModuleEntity> moduleEntityList, String moduleName) {
        Optional<ApiModuleEntity> optionalApiModuleEntity = moduleEntityList.stream()
                .filter(module -> StringUtils.equals(module.getName(), moduleName))
                .findFirst();
        if (optionalApiModuleEntity.isPresent()) {
            return optionalApiModuleEntity.get().getId();
        }
        ApiModuleEntity apiModuleEntity = ApiModuleEntity.builder().name(moduleName).build();
        apiModuleDao.insert(apiModuleEntity);
        moduleEntityList.add(apiModuleEntity);
        return apiModuleEntity.getId();
    }
}
