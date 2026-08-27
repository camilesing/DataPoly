// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.persistence.entity.ApiContextEntity;
import com.cs.persistence.mapper.ApiContextMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class ApiContextDao {

    @Resource
    private ApiContextMapper apiContextMapper;

    @Transactional(rollbackFor = Exception.class)
    public void batchInsert(List<ApiContextEntity> records) {
        if (null != records && records.size() > 0) {
            records.forEach(apiContextMapper::insert);
        }
    }

    public List<ApiContextEntity> getByApiConfigId(Long apiId) {
        QueryWrapper<ApiContextEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiContextEntity::getApiId, apiId);
        return apiContextMapper.selectList(queryWrapper);
    }

    public void deleteByApiConfigId(Long apiId) {
        QueryWrapper<ApiContextEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(ApiContextEntity::getApiId, apiId);
        apiContextMapper.delete(queryWrapper);
    }

}
