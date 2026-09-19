// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.persistence.entity.SystemUserEntity;
import com.cs.persistence.mapper.SystemUserMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.Objects;

@Repository
public class SystemUserDao {

    @Resource
    private SystemUserMapper systemUserMapper;

    public SystemUserEntity getById(Long id) {
        return systemUserMapper.selectById(id);
    }

    public SystemUserEntity findByUsername(String username) {
        QueryWrapper<SystemUserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SystemUserEntity::getUsername, username);
        return systemUserMapper.selectOne(queryWrapper);
    }

    public void updateUserPassword(String username, String newPassword) {
        SystemUserEntity userEntity = findByUsername(username);
        if (Objects.nonNull(userEntity)) {
            userEntity.setPassword(newPassword);
            systemUserMapper.updateById(userEntity);
        }
    }

    /**
     * Creates a user row; the generated id is filled back into the entity.
     * Account providers that authenticate outside DataPoly create their rows
     * through this method, so the caller owns the password/salt and role values.
     */
    public void insert(SystemUserEntity userEntity) {
        systemUserMapper.insert(userEntity);
    }

}
