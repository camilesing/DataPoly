// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cs.common.dto.ApiIdVersion;
import com.cs.common.enums.HttpMethodEnum;
import com.cs.persistence.entity.*;
import com.cs.persistence.mapper.ApiOnlineMapper;
import com.cs.persistence.util.JsonUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class ApiOnlineDao {

    @Resource
    private ApiOnlineMapper apiOnlineMapper;

    private ApiAssignmentEntity buildAssignmentEntity(ApiOnlineEntity entity) {
        if (null == entity) {
            return null;
        }
        String content = entity.getContent();
        Class<ApiAssignmentEntity> clazz = ApiAssignmentEntity.class;
        ApiAssignmentEntity assignmentEntity = JsonUtils.toBeanObject(content, clazz);
        assignmentEntity.setGroupId(entity.getGroupId());
        assignmentEntity.setModuleId(entity.getModuleId());
        assignmentEntity.setDatasourceId(entity.getDatasourceId());
        assignmentEntity.setOpen(entity.getOpen());
        assignmentEntity.setAlarm(entity.getAlarm());
        assignmentEntity.setFlowStatus(entity.getFlowStatus());
        assignmentEntity.setUpdateTime(entity.getUpdateTime());
        assignmentEntity.setCommitId(entity.getCommitId());
        return assignmentEntity;
    }

    private Long getIdByUniqueKey(HttpMethodEnum method, String path) {
        QueryWrapper<ApiOnlineEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .select(ApiOnlineEntity::getId)
                .eq(ApiOnlineEntity::getMethod, method)
                .eq(ApiOnlineEntity::getPath, path);
        ApiOnlineEntity entity = apiOnlineMapper.selectOne(queryWrapper);
        if (null != entity) {
            return entity.getId();
        }
        return null;
    }

    /**
     * Atomic upsert (A4): relies on the uk_method_path unique index to converge at the database side, removing the select-then-insert race;
     * MySQL/PostgreSQL dialect branches in {@link ApiOnlineMapper#upsert}.
     */
    public void upsert(ApiOnlineEntity entity) {
        apiOnlineMapper.upsert(entity);
    }

    public void deleteByApiId(Long apiId) {
        QueryWrapper<ApiOnlineEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiOnlineEntity::getApiId, apiId);
        apiOnlineMapper.delete(queryWrapper);
    }

    public ApiAssignmentEntity getByApiId(Long apiId) {
        QueryWrapper<ApiOnlineEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiOnlineEntity::getApiId, apiId);
        ApiOnlineEntity entity = apiOnlineMapper.selectOne(queryWrapper);
        if (null != entity) {
            return buildAssignmentEntity(entity);
        }
        return null;
    }

    public List<ApiIdVersion> filterOnline(List<Long> apiIds) {
        if (CollectionUtils.isEmpty(apiIds)) {
            return Collections.emptyList();
        }
        return apiOnlineMapper.filterOnline(apiIds);
    }

    public ApiIdVersion filterOnline(Long apiId) {
        return filterOnline(Collections.singletonList(apiId))
                .stream().findFirst().orElse(null);
    }

    public List<ApiAssignmentEntity> listAll() {
        return apiOnlineMapper.selectList(null)
                .stream().map(this::buildAssignmentEntity)
                .collect(Collectors.toList());
    }

    public List<ApiAssignmentEntity> searchAll(List<Long> groupIds, List<Long> moduleIds, Boolean open,
                                               String searchText) {
        return apiOnlineMapper.selectList(
                        Wrappers.<ApiOnlineEntity>lambdaQuery()
                                .eq(Objects.nonNull(open), ApiOnlineEntity::getOpen, open)
                                .in(CollUtil.isNotEmpty(groupIds), ApiOnlineEntity::getGroupId, groupIds)
                                .in(CollUtil.isNotEmpty(moduleIds), ApiOnlineEntity::getModuleId, moduleIds)
                                .like(StringUtils.hasText(searchText), ApiOnlineEntity::getName, searchText)
                                .orderByDesc(ApiOnlineEntity::getApiId)
                ).stream()
                .map(entity -> buildAssignmentEntity(entity))
                .collect(Collectors.toList());
    }

    public ApiAssignmentEntity getByUk(HttpMethodEnum method, String path) {
        QueryWrapper<ApiOnlineEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(ApiOnlineEntity::getMethod, method)
                .eq(ApiOnlineEntity::getPath, path);
        ApiOnlineEntity entity = apiOnlineMapper.selectOne(queryWrapper);
        if (null != entity) {
            return buildAssignmentEntity(entity);
        }
        return null;
    }

    public Long getCommitIdByUk(HttpMethodEnum method, String path) {
        QueryWrapper<ApiOnlineEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .select(ApiOnlineEntity::getCommitId)
                .eq(ApiOnlineEntity::getMethod, method)
                .eq(ApiOnlineEntity::getPath, path);
        ApiOnlineEntity entity = apiOnlineMapper.selectOne(queryWrapper);
        if (null != entity) {
            return entity.getCommitId();
        }
        return null;
    }

    public boolean existsByUniqueKey(HttpMethodEnum method, String path) {
        return null != getIdByUniqueKey(method, path);
    }

    public List<ApiAssignmentEntity> listFlowControlAll() {
        QueryWrapper<ApiOnlineEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(ApiOnlineEntity::getFlowStatus, true);
        return apiOnlineMapper.selectList(queryWrapper)
                .stream().map(this::buildAssignmentEntity)
                .collect(Collectors.toList());
    }

    public boolean existsDataSourceById(Long dataSourceId) {
        QueryWrapper<ApiOnlineEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiOnlineEntity::getDatasourceId, dataSourceId);
        return apiOnlineMapper.selectCount(queryWrapper) > 0;
    }

    public boolean existsGroupById(Long groupId) {
        QueryWrapper<ApiOnlineEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiOnlineEntity::getGroupId, groupId);
        return apiOnlineMapper.selectCount(queryWrapper) > 0;
    }

    public boolean existsModuleById(Long moduleId) {
        QueryWrapper<ApiOnlineEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiOnlineEntity::getModuleId, moduleId);
        return apiOnlineMapper.selectCount(queryWrapper) > 0;
    }

    public void resetGroupByGroupId(Long groupId) {
        apiOnlineMapper.resetGroup(groupId);
    }

    public void updateGroup(Long groupId, List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        apiOnlineMapper.updateGroup(groupId, ids);
    }
}
