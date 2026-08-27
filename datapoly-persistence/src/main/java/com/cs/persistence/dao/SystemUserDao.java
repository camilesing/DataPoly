// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.persistence.entity.SystemUserEntity;
import com.cs.persistence.mapper.SystemUserMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
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

}
