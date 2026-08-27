// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.core.bean.BeanUtil;
import com.cs.common.dto.AccessToken;
import com.cs.common.exception.*;
import com.cs.common.util.*;
import com.cs.core.dto.SystemUserDetailResponse;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.entity.SystemUserEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

@Service
public class SystemUserService {

    /**
     * Anti-enumeration on login failure: when the user does not exist, still run one bcrypt with a fixed salt to equalize timing across branches.
     * The salt must be a valid bcrypt salt ($2a$10$ + 22 alphanumeric chars).
     */
    private static final String DUMMY_BCRYPT_SALT = "$2a$10$DATAPOLYDUMMY0000000000";

    @Resource
    private SystemUserDao systemUserDao;

    public AccessToken login(String username, String password) {
        SystemUserEntity user = systemUserDao.findByUsername(username);
        if (Objects.isNull(user)) {
            // Both branches return the same code and message with equal timing, preventing username enumeration (codes 7/8 no longer differ)
            PasswordUtils.encryptPassword(password, DUMMY_BCRYPT_SALT);
            throw new CommonException(ResponseErrorCode.ERROR_USER_PASSWORD_WRONG, "auth.login.failed");
        }

        String encryptPassword = PasswordUtils.encryptPassword(password, user.getSalt());
        if (!isEqualsConstantTime(encryptPassword, user.getPassword())) {
            throw new CommonException(ResponseErrorCode.ERROR_USER_PASSWORD_WRONG, "auth.login.failed");
        }

        String token = TokenUtils.generateValue();
        CacheUtils.put(token, user);
        AccessToken accessTokenWrapper = new AccessToken(user.getRealName(), user.getUsername(), token,
                System.currentTimeMillis() / 1000, CacheUtils.CACHE_DURATION_SECONDS);
        return accessTokenWrapper;
    }

    public void logout(HttpServletRequest request) {
        String token = TokenUtils.getRequestToken(request);
        if (StringUtils.isNotBlank(token)) {
            CacheUtils.remove(token);
        }
    }

    public SystemUserDetailResponse getUserDetailById(Long id) {
        SystemUserEntity user = systemUserDao.getById(id);
        if (Objects.isNull(user)) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "id=" + id);
        }
        SystemUserDetailResponse detailResponse = new SystemUserDetailResponse();
        BeanUtil.copyProperties(user, detailResponse);
        return detailResponse;
    }

    public SystemUserDetailResponse getUserDetailByUsername(String username) {
        SystemUserEntity user = findByUsername(username);
        if (Objects.isNull(user)) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "username=" + username);
        }

        SystemUserDetailResponse detailResponse = new SystemUserDetailResponse();
        BeanUtil.copyProperties(user, detailResponse);
        return detailResponse;
    }

    public void changeOwnPassword(HttpServletRequest request, String oldPassword, String newPassword) {
        String username = request.getAttribute("username").toString();
        SystemUserEntity systemUserEntity = findByUsername(username);
        if (Objects.isNull(systemUserEntity)) {
            throw new CommonException(ResponseErrorCode.ERROR_USER_NOT_EXISTS, username);
        }

        String encryptOldPassword = PasswordUtils
                .encryptPassword(oldPassword, systemUserEntity.getSalt());
        if (!isEqualsConstantTime(encryptOldPassword, systemUserEntity.getPassword())) {
            throw new CommonException(ResponseErrorCode.ERROR_USER_PASSWORD_WRONG, username);
        }

        String encryptNewPassword = PasswordUtils
                .encryptPassword(newPassword, systemUserEntity.getSalt());
        systemUserDao.updateUserPassword(username, encryptNewPassword);
    }

    private static boolean isEqualsConstantTime(String a, String b) {
        if (null == a || null == b) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public SystemUserEntity findByUsername(String username) {
        return systemUserDao.findByUsername(username);
    }
}
