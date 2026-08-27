// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import com.cs.common.util.PasswordUtils;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.entity.SystemUserEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Seed admin password governance (S5):
 * <ul>
 *   <li>If {@code datapoly.admin.password} is set (injectable via the DATAPOLY_ADMIN_PASSWORD env var),
 *       override the seed admin password at startup (idempotent: skipped when already equal, so a
 *       password hash the user changed later is never touched).</li>
 *   <li>If unset and the seed password is still the factory default (admin/123456), log a startup warning.</li>
 * </ul>
 * Already-executed Liquibase migrations are not modified, to keep checksums intact.
 */
@Slf4j
@Component
public class AdminPasswordInitializer {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_BCRYPT_HASH =
            "$2a$10$eUanVjvzV27BBxAb4zuBCugwnngHkRZ7ZB4iI5tdx9ETJ2tnXJJDy";

    @Value("${datapoly.admin.password:}")
    private String adminPassword;

    @Resource
    private SystemUserDao systemUserDao;

    @EventListener(ApplicationReadyEvent.class)
    public void initAdminPassword() {
        try {
            SystemUserEntity admin = systemUserDao.findByUsername(DEFAULT_ADMIN_USERNAME);
            if (null == admin) {
                return;
            }
            if (StringUtils.isNotBlank(adminPassword)) {
                String newHash = PasswordUtils.encryptPassword(adminPassword, admin.getSalt());
                if (!isEqualsConstantTime(newHash, admin.getPassword())) {
                    systemUserDao.updateUserPassword(DEFAULT_ADMIN_USERNAME, newHash);
                    log.info("Seed admin password has been overridden by property 'datapoly.admin.password'.");
                }
            } else if (isEqualsConstantTime(DEFAULT_ADMIN_BCRYPT_HASH, admin.getPassword())) {
                log.warn("!!! Security warning: the seed admin account is still using the factory default "
                        + "password. Set environment variable DATAPOLY_ADMIN_PASSWORD (property "
                        + "datapoly.admin.password) and restart, or change the password in the dashboard "
                        + "immediately.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize admin password: {}", e.getMessage(), e);
        }
    }

    private static boolean isEqualsConstantTime(String a, String b) {
        if (null == a || null == b) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

}
