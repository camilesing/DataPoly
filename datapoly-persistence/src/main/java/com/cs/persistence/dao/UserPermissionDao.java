// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.persistence.entity.UserPermissionEntity;
import com.cs.persistence.mapper.UserPermissionMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * Persistence of the per-user permission matrix. Reads are per-request lookups on
 * {@code uk_username_module}; a save replaces the account's rows wholesale inside one transaction
 * (delete + insert), which is what keeps the (username, module) pairs unique without merge logic.
 */
@Repository
public class UserPermissionDao {

    @Resource
    private UserPermissionMapper userPermissionMapper;

    /** All rows of one account, in module order; an unconfigured account yields an empty list. */
    public List<UserPermissionEntity> findByUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return Collections.emptyList();
        }
        QueryWrapper<UserPermissionEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(UserPermissionEntity::getUsername, username);
        return userPermissionMapper.selectList(queryWrapper);
    }

    /** One row of (username, module), or {@code null} when the module keeps its default. */
    public UserPermissionEntity findByUsernameAndModule(String username, String module) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(module)) {
            return null;
        }
        QueryWrapper<UserPermissionEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(UserPermissionEntity::getUsername, username)
                .eq(UserPermissionEntity::getModule, module);
        return userPermissionMapper.selectOne(queryWrapper);
    }

    public void insert(UserPermissionEntity entity) {
        userPermissionMapper.insert(entity);
    }

    public void deleteByUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        QueryWrapper<UserPermissionEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(UserPermissionEntity::getUsername, username);
        userPermissionMapper.delete(queryWrapper);
    }

    /**
     * Replaces the account's whole matrix with {@code rows}; the caller fills username/createAt.
     * Transactional so a failed save leaves the previous grants in place.
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceByUsername(String username, List<UserPermissionEntity> rows) {
        deleteByUsername(username);
        for (UserPermissionEntity row : rows) {
            userPermissionMapper.insert(row);
        }
    }
}
