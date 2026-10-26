// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cs.persistence.entity.LoginTokenEntity;
import com.cs.persistence.mapper.LoginTokenMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;

/**
 * Persistence of manager login sessions. Every lookup is a single indexed read
 * ({@code uk_access_token}) and every invalidation is a delete — sessions take effect and
 * end on the next request, with no cache to invalidate across processes.
 */
@Repository
public class LoginTokenDao {

    @Resource
    private LoginTokenMapper loginTokenMapper;

    /** The session row of one token, or {@code null} when it was never issued / already deleted. */
    public LoginTokenEntity findByToken(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return null;
        }
        return loginTokenMapper.selectOne(
                Wrappers.<LoginTokenEntity>lambdaQuery()
                        .eq(LoginTokenEntity::getAccessToken, accessToken));
    }

    public void insert(LoginTokenEntity loginToken) {
        loginTokenMapper.insert(loginToken);
    }

    /** Ends one session (logout, or a session found expired); unknown tokens are a no-op. */
    public void deleteByToken(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return;
        }
        QueryWrapper<LoginTokenEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(LoginTokenEntity::getAccessToken, accessToken);
        loginTokenMapper.delete(queryWrapper);
    }

    /**
     * Ends every session of one account. {@code keepToken} spares a single session — the
     * caller's own one when a user rotates their own password — and is ignored when blank.
     */
    public void deleteByUsername(String username, String keepToken) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        QueryWrapper<LoginTokenEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(LoginTokenEntity::getUsername, username)
                .ne(StringUtils.isNotBlank(keepToken), LoginTokenEntity::getAccessToken, keepToken);
        loginTokenMapper.delete(queryWrapper);
    }

    /** Sweeps rows nobody will look at again; called at startup and nightly. */
    public int deleteExpired(long currentTimestamp) {
        QueryWrapper<LoginTokenEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().le(LoginTokenEntity::getExpireAt, currentTimestamp);
        return loginTokenMapper.delete(queryWrapper);
    }
}