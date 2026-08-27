// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.common.enums.HttpMethodEnum;
import com.cs.persistence.entity.*;
import com.cs.persistence.mapper.ApiAssignmentMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class ApiAssignmentDao {

    @Resource
    private ApiAssignmentMapper apiAssignmentMapper;
    @Resource
    private ApiContextDao apiContextDao;

    public List<Long> getUpgradeOnlineAssignments() {
        return apiAssignmentMapper.getUpgradeOnlineAssignments();
    }

    public void resetUpgradeOnlineAssignments(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        apiAssignmentMapper.resetUpgradeOnlineAssignments(ids);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(ApiAssignmentEntity apiConfigEntity) {
        apiAssignmentMapper.insert(apiConfigEntity);
        if (null != apiConfigEntity.getContextList() && apiConfigEntity.getContextList().size() > 0) {
            apiConfigEntity.getContextList().forEach(i -> i.setId(null));
            apiConfigEntity.getContextList().forEach(i -> i.setApiId(apiConfigEntity.getId()));
            apiContextDao.batchInsert(apiConfigEntity.getContextList());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ApiAssignmentEntity apiConfigEntity) {
        apiAssignmentMapper.updateById(apiConfigEntity);
        apiContextDao.deleteByApiConfigId(apiConfigEntity.getId());
        if (null != apiConfigEntity.getContextList() && apiConfigEntity.getContextList().size() > 0) {
            apiConfigEntity.getContextList().forEach(i -> i.setId(null));
            apiConfigEntity.getContextList().forEach(i -> i.setApiId(apiConfigEntity.getId()));
            apiContextDao.batchInsert(apiConfigEntity.getContextList());
        }
    }

    public ApiAssignmentEntity getById(Long id, boolean withSql) {
        ApiAssignmentEntity apiConfigEntity = apiAssignmentMapper.selectById(id);
        if (null != apiConfigEntity && withSql) {
            apiConfigEntity.setContextList(apiContextDao.getByApiConfigId(apiConfigEntity.getId()));
        }
        return apiConfigEntity;
    }

    public List<ApiAssignmentEntity> getByIds(List<Long> ids) {
        return apiAssignmentMapper.selectBatchIds(ids);
    }

    public List<ModuleAssignmentEntity> getModuleAssignments() {
        return apiAssignmentMapper.getModuleAssignments();
    }

    public void resetGroupByGroupId(Long groupId) {
        apiAssignmentMapper.resetGroup(groupId);
    }

    public void updateGroup(Long groupId, List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        apiAssignmentMapper.updateGroup(groupId, ids);
    }

    public ApiAssignmentEntity getByUk(HttpMethodEnum method, String path) {
        QueryWrapper<ApiAssignmentEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiAssignmentEntity::getMethod, method.name())
                .eq(ApiAssignmentEntity::getPath, path);
        ApiAssignmentEntity apiConfigEntity = apiAssignmentMapper.selectOne(queryWrapper);
        if (null != apiConfigEntity) {
            apiConfigEntity.setContextList(apiContextDao.getByApiConfigId(apiConfigEntity.getId()));
        }
        return apiConfigEntity;
    }

    public List<ApiAssignmentEntity> searchAll(Long groupId, Long moduleId, Boolean open,
                                               String searchText, Boolean online) {
        if (StringUtils.isNotEmpty(searchText)) {
            searchText = "%" + searchText + "%";
        }
        return apiAssignmentMapper.searchAll(groupId, moduleId, open, searchText, online);
    }

    public List<ApiAssignmentEntity> listAll() {
        return apiAssignmentMapper.selectList(null);
    }

    public boolean existsDataSourceById(Long dataSourceId) {
        QueryWrapper<ApiAssignmentEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiAssignmentEntity::getDatasourceId, dataSourceId);
        return apiAssignmentMapper.selectCount(queryWrapper) > 0;
    }

    public boolean existsGroupById(Long groupId) {
        QueryWrapper<ApiAssignmentEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiAssignmentEntity::getGroupId, groupId);
        return apiAssignmentMapper.selectCount(queryWrapper) > 0;
    }

    public boolean existsModuleById(Long moduleId) {
        QueryWrapper<ApiAssignmentEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiAssignmentEntity::getModuleId, moduleId);
        return apiAssignmentMapper.selectCount(queryWrapper) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        apiContextDao.deleteByApiConfigId(id);
        apiAssignmentMapper.deleteById(id);
    }

}
