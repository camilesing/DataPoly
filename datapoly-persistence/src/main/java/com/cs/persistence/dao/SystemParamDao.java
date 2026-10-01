// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cs.persistence.entity.SystemParamEntity;
import com.cs.persistence.mapper.SystemParamMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;

@Repository
public class SystemParamDao {

    @Resource
    private SystemParamMapper systemParamMapper;

    public SystemParamEntity getByParamKey(String paramKey) {
        QueryWrapper<SystemParamEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SystemParamEntity::getParamKey, paramKey);
        return systemParamMapper.selectOne(queryWrapper);
    }

    public void updateByParamKey(String paramKey, String paramValue) {
        // conditional UPDATE instead of read-modify-write: concurrent updates no longer
        // lose one silently, and a row deleted in between just affects zero rows.
        // String columns (not lambdas) keep this usable without MybatisPlus table metadata.
        UpdateWrapper<SystemParamEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set("param_value", paramValue).eq("param_key", paramKey);
        systemParamMapper.update(null, updateWrapper);
    }

}
