// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cs.common.dto.IdWithName;
import com.cs.persistence.entity.*;
import com.cs.persistence.mapper.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class AppClientDao {

    @Resource
    private AppClientMapper appClientMapper;
    @Resource
    private ClientGroupMapper clientAuthMapper;

    public void insert(AppClientEntity appClientEntity) {
        appClientMapper.insert(appClientEntity);
    }

    public List<AppClientEntity> listAll() {
        return listAll(null);
    }

    public List<AppClientEntity> listAll(String searchText, Long groupId) {
        if (null == groupId) {
            return listAll(searchText);
        }
        if (null != searchText) {
            searchText = "%" + searchText + "%";
        }
        return appClientMapper.searchAppClient(searchText, groupId);
    }

    public List<AppClientEntity> listAll(String searchText) {
        return appClientMapper.selectList(
                Wrappers.<AppClientEntity>lambdaQuery()
                        .like(StringUtils.isNotBlank(searchText), AppClientEntity::getName, searchText)
                        .orderByDesc(AppClientEntity::getCreateTime)
        );
    }

    public AppClientEntity getById(Long id) {
        return appClientMapper.selectById(id);
    }

    public AppClientEntity getByAppKey(String appKey) {
        QueryWrapper<AppClientEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(AppClientEntity::getAppKey, appKey);
        return appClientMapper.selectOne(queryWrapper);
    }

    public AppClientEntity getByAccessToken(String accessToken) {
        QueryWrapper<AppClientEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(AppClientEntity::getAccessToken, accessToken);
        return appClientMapper.selectOne(queryWrapper);
    }

    public List<AppClientEntity> getByName(String name) {
        QueryWrapper<AppClientEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(AppClientEntity::getName, name);
        return appClientMapper.selectList(queryWrapper);
    }

    public boolean existsAuthGroups(String appKey, Long groupId) {
        AppClientEntity appClientEntity = getByAppKey(appKey);
        if (null == appClientEntity) {
            return false;
        }
        QueryWrapper<ClientGroupEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(ClientGroupEntity::getClientId, appClientEntity.getId())
                .eq(ClientGroupEntity::getGroupId, groupId);
        return clientAuthMapper.selectCount(queryWrapper) > 0;
    }

    public void updateTokenByAppKey(String appKey, String token) {
        AppClientEntity updateEntity = new AppClientEntity();
        updateEntity.setAppKey(appKey);
        updateEntity.setAccessToken(token);

        QueryWrapper<AppClientEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(AppClientEntity::getAppKey, appKey);

        appClientMapper.update(updateEntity, queryWrapper);
    }

    public void clearTokenByAppKey(String appKey) {
        appClientMapper.update(null,
                Wrappers.<AppClientEntity>lambdaUpdate()
                        .eq(AppClientEntity::getAppKey, appKey)
                        .set(AppClientEntity::getAccessToken, null));
    }

    public void deleteClientAuthByGroupId(Long groupId) {
        QueryWrapper<ClientGroupEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ClientGroupEntity::getGroupId, groupId);
        clientAuthMapper.delete(queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        appClientMapper.deleteById(id);

        QueryWrapper<ClientGroupEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ClientGroupEntity::getClientId, id);
        clientAuthMapper.delete(queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveAuthGroup(Long id, List<Long> groupIds) {
        QueryWrapper<ClientGroupEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ClientGroupEntity::getClientId, id);
        clientAuthMapper.delete(queryWrapper);

        if (CollectionUtils.isNotEmpty(groupIds)) {
            List<ClientGroupEntity> insertLists = new ArrayList<>();
            for (Long groupId : groupIds.stream().distinct().collect(Collectors.toList())) {
                insertLists.add(ClientGroupEntity.builder().clientId(id).groupId(groupId).build());
            }
            clientAuthMapper.insertList(insertLists);
        }
    }

    public List<IdWithName> getGroupAuth(Long id) {
        return clientAuthMapper.getGroupAuth(id);
    }
}
