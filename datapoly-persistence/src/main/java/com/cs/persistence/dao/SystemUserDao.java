// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.persistence.entity.SystemUserEntity;
import com.cs.persistence.mapper.SystemUserMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;
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

    /** All users ordered by id, optionally narrowed by a username/realName keyword. */
    public List<SystemUserEntity> listAll(String searchText) {
        QueryWrapper<SystemUserEntity> queryWrapper = new QueryWrapper<>();
        String keyword = StringUtils.trimToNull(searchText);
        if (keyword != null) {
            queryWrapper.lambda().and(w -> w
                    .like(SystemUserEntity::getUsername, keyword)
                    .or()
                    .like(SystemUserEntity::getRealName, keyword));
        }
        queryWrapper.lambda().orderByAsc(SystemUserEntity::getId);
        return systemUserMapper.selectList(queryWrapper);
    }

    /**
     * Unlocked ADMIN rows — the guard behind "the system must keep at least one usable
     * administrator": demoting or locking the last one would lock everybody out.
     */
    public long countUnlockedAdmins() {
        QueryWrapper<SystemUserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(SystemUserEntity::getRole, "ADMIN")
                .and(w -> w.isNull(SystemUserEntity::getLocked).or().eq(SystemUserEntity::getLocked, false));
        return systemUserMapper.selectCount(queryWrapper);
    }

    /**
     * Profile update by the admin console; null fields of {@code user} are left untouched
     * (MyBatis-Plus default NOT_NULL strategy), so callers only set what changes.
     */
    public void updateUser(SystemUserEntity user) {
        systemUserMapper.updateById(user);
    }

    /** Rotates a credential together with its salt (admin reset); per-user salt, per-user hash. */
    public void updateUserPasswordAndSalt(String username, String password, String salt) {
        QueryWrapper<SystemUserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SystemUserEntity::getUsername, username);
        SystemUserEntity update = new SystemUserEntity();
        update.setPassword(password);
        update.setSalt(salt);
        systemUserMapper.update(update, queryWrapper);
    }

}
