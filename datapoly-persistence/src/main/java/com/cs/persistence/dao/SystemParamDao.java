// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.persistence.entity.SystemParamEntity;
import com.cs.persistence.mapper.SystemParamMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Objects;

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
        SystemParamEntity entity = getByParamKey(paramKey);
        if (Objects.nonNull(entity)) {
            entity.setParamValue(paramValue);
            systemParamMapper.updateById(entity);
        }
    }

}
