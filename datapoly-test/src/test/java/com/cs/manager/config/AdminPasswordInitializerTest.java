// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import cn.hutool.crypto.digest.BCrypt;
import com.cs.common.util.PasswordUtils;
import com.cs.core.service.LoginSessionService;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.entity.SystemUserEntity;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Drives the seed admin password governance branches with hand-written fakes:
 * override-on-startup (with session revocation), the idempotent skip when the property
 * already matches, the factory-default warning branch, and the swallowed dao failure.
 */
public class AdminPasswordInitializerTest {

    private static class FakeSystemUserDao extends SystemUserDao {
        SystemUserEntity found;
        String updatedUsername;
        String updatedHash;
        int updateCount;
        boolean fail;

        @Override
        public SystemUserEntity findByUsername(String username) {
            if (fail) {
                throw new IllegalStateException("dao down");
            }
            return found;
        }

        @Override
        public void updateUserPassword(String username, String newPassword) {
            this.updatedUsername = username;
            this.updatedHash = newPassword;
            this.updateCount++;
        }
    }

    private static class FakeLoginSessionService extends LoginSessionService {
        int revokeAllCount;
        String revokedUsername;

        FakeLoginSessionService() {
            super(null, LoginSessionService.DEFAULT_TTL_SECONDS);
        }

        @Override
        public void revokeAll(String username) {
            this.revokedUsername = username;
            this.revokeAllCount++;
        }
    }

    private AdminPasswordInitializer initializer;
    private FakeSystemUserDao userDao;
    private FakeLoginSessionService loginSessionService;

    /** bcrypt salts must be real gensalt strings; arbitrary strings are rejected by BCrypt. */
    private static String newSalt() {
        return BCrypt.gensalt();
    }

    @Before
    public void setUp() throws Exception {
        initializer = new AdminPasswordInitializer();
        userDao = new FakeSystemUserDao();
        loginSessionService = new FakeLoginSessionService();
        inject("systemUserDao", userDao);
        inject("loginSessionService", loginSessionService);
        setAdminPassword("");
    }

    private void inject(String name, Object value) throws Exception {
        Field field = AdminPasswordInitializer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(initializer, value);
    }

    private void setAdminPassword(String value) throws Exception {
        Field field = AdminPasswordInitializer.class.getDeclaredField("adminPassword");
        field.setAccessible(true);
        field.set(initializer, value);
    }

    private SystemUserEntity admin(String passwordHash, String salt) {
        SystemUserEntity admin = new SystemUserEntity();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword(passwordHash);
        admin.setSalt(salt);
        return admin;
    }

    @Test
    public void missingAdminRowLeavesEverythingUntouched() {
        userDao.found = null;

        initializer.initAdminPassword();

        assertEquals(0, userDao.updateCount);
        assertEquals(0, loginSessionService.revokeAllCount);
    }

    @Test
    public void propertyOverrideRewritesHashAndRevokesSessions() throws Exception {
        String salt = newSalt();
        userDao.found = admin(PasswordUtils.encryptPassword("old-password", salt), salt);
        setAdminPassword("brand-new-password");

        initializer.initAdminPassword();

        assertEquals(1, userDao.updateCount);
        assertEquals("admin", userDao.updatedUsername);
        assertEquals(PasswordUtils.encryptPassword("brand-new-password", salt), userDao.updatedHash);
        assertEquals(1, loginSessionService.revokeAllCount);
        assertEquals("admin", loginSessionService.revokedUsername);
    }

    @Test
    public void propertyMatchingStoredHashIsIdempotentAndKeepsSessions() throws Exception {
        String salt = newSalt();
        userDao.found = admin(PasswordUtils.encryptPassword("same-password", salt), salt);
        setAdminPassword("same-password");

        initializer.initAdminPassword();

        assertEquals(0, userDao.updateCount);
        assertEquals(0, loginSessionService.revokeAllCount);
    }

    @Test
    public void nullStoredHashWithPropertySetStillRotates() throws Exception {
        userDao.found = admin(null, newSalt());
        setAdminPassword("whatever");

        initializer.initAdminPassword();

        assertEquals(1, userDao.updateCount);
        assertEquals(1, loginSessionService.revokeAllCount);
    }

    @Test
    public void blankPropertyWithFactoryDefaultHashDoesNotTouchAnything() {
        userDao.found = admin(AdminPasswordInitializer.DEFAULT_ADMIN_BCRYPT_HASH, newSalt());

        initializer.initAdminPassword();

        assertEquals(0, userDao.updateCount);
        assertEquals(0, loginSessionService.revokeAllCount);
    }

    @Test
    public void blankPropertyWithChangedHashDoesNothing() {
        userDao.found = admin(PasswordUtils.encryptPassword("user-changed", newSalt()), newSalt());

        initializer.initAdminPassword();

        assertEquals(0, userDao.updateCount);
        assertEquals(0, loginSessionService.revokeAllCount);
    }

    @Test
    public void daoFailureIsSwallowedToNotBreakStartup() {
        userDao.fail = true;

        initializer.initAdminPassword();

        assertEquals(0, userDao.updateCount);
        assertEquals(0, loginSessionService.revokeAllCount);
    }
}
