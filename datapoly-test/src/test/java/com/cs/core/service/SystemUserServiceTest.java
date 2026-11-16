// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.crypto.digest.BCrypt;
import com.cs.common.dto.AccessToken;
import com.cs.common.exception.*;
import com.cs.common.util.PasswordUtils;
import com.cs.core.dto.SystemUserDetailResponse;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.entity.SystemUserEntity;
import org.junit.*;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

public class SystemUserServiceTest {

    private static final String USERNAME = "admin";
    private static final String RAW_PASSWORD = "right-password";

    @Test
    public void loginFailedBranchesReturnSameCodeAndMessage() throws Exception {
        SystemUserService service = newService(fakeDao(null));

        CommonException userMissing;
        try {
            service.login("nobody", "whatever");
            userMissing = null;
        } catch (CommonException e) {
            userMissing = e;
        }
        Assert.assertNotNull(userMissing);

        SystemUserEntity user = buildUser(USERNAME, "wrong-password");
        SystemUserService existingService = newService(fakeDao(user));

        CommonException wrongPassword;
        try {
            existingService.login(USERNAME, "whatever");
            wrongPassword = null;
        } catch (CommonException e) {
            wrongPassword = e;
        }
        Assert.assertNotNull(wrongPassword);

        // Anti-enumeration: nonexistent user and wrong password return the same code and message
        Assert.assertEquals(userMissing.getCode(), wrongPassword.getCode());
        Assert.assertEquals(ResponseErrorCode.ERROR_USER_PASSWORD_WRONG, wrongPassword.getCode());
        Assert.assertEquals(userMissing.getMessage(), wrongPassword.getMessage());
    }

    @Test
    public void loginSuccessReturnsAccessToken() throws Exception {
        SystemUserEntity user = buildUser(USERNAME, RAW_PASSWORD);
        SystemUserService service = newService(fakeDao(user));

        AccessToken accessToken = service.login(USERNAME, RAW_PASSWORD);
        Assert.assertNotNull(accessToken);
        Assert.assertEquals(USERNAME, accessToken.getAppKey());
        Assert.assertFalse(accessToken.getAccessToken().isEmpty());
        // The role travels with the login response so the UI can filter by it
        Assert.assertEquals("ADMIN", accessToken.getRole());
    }

    @Test
    public void changeOwnPasswordWithWrongOldPasswordRejected() throws Exception {
        SystemUserEntity user = buildUser(USERNAME, RAW_PASSWORD);
        AtomicReference<String> updated = new AtomicReference<>();
        SystemUserService service = newService(new SystemUserDao() {
            @Override
            public SystemUserEntity findByUsername(String username) {
                return user;
            }

            @Override
            public void updateUserPassword(String username, String newPassword) {
                updated.set(newPassword);
            }
        });

        try {
            service.changeOwnPassword(fakeRequest(), "wrong-old", "new-password");
            Assert.fail("expected password wrong exception");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_USER_PASSWORD_WRONG, e.getCode());
        }
        Assert.assertNull(updated.get());
    }

    @Test
    public void changeOwnPasswordWithCorrectOldPassword() throws Exception {
        SystemUserEntity user = buildUser(USERNAME, RAW_PASSWORD);
        AtomicReference<String> updated = new AtomicReference<>();
        SystemUserService service = newService(new SystemUserDao() {
            @Override
            public SystemUserEntity findByUsername(String username) {
                return user;
            }

            @Override
            public void updateUserPassword(String username, String newPassword) {
                updated.set(newPassword);
            }
        });

        service.changeOwnPassword(fakeRequest(), RAW_PASSWORD, "new-password");
        Assert.assertNotNull(updated.get());
        Assert.assertEquals(PasswordUtils.encryptPassword("new-password", user.getSalt()), updated.get());
    }

    @Test
    public void changeOwnPasswordKeepsTheCallersSessionAndDropsTheOthers() throws Exception {
        SystemUserEntity user = buildUser(USERNAME, RAW_PASSWORD);
        LoginSessionService loginSessionService = new LoginSessionService(new InMemoryLoginTokenDao(),
                LoginSessionService.DEFAULT_TTL_SECONDS);
        SystemUserService service = newService(new SystemUserDao() {
            @Override
            public SystemUserEntity findByUsername(String username) {
                return user;
            }

            @Override
            public void updateUserPassword(String username, String newPassword) {
            }
        }, loginSessionService);
        String mine = loginSessionService.issue(user);
        String other = loginSessionService.issue(user);

        service.changeOwnPassword(fakeRequest(mine), RAW_PASSWORD, "new-password");

        Assert.assertNotNull(loginSessionService.resolve(mine));
        Assert.assertNull(loginSessionService.resolve(other));
    }

    @Test
    public void logoutRevokesTheBearerTokenSession() throws Exception {
        SystemUserEntity user = buildUser(USERNAME, RAW_PASSWORD);
        LoginSessionService loginSessionService = new LoginSessionService(new InMemoryLoginTokenDao(),
                LoginSessionService.DEFAULT_TTL_SECONDS);
        SystemUserService service = newService(fakeDao(user), loginSessionService);
        String token = loginSessionService.issue(user);
        Assert.assertNotNull(loginSessionService.resolve(token));

        service.logout(fakeRequest(token));

        Assert.assertNull(loginSessionService.resolve(token));
    }

    @Test
    public void getUserDetailByIdCopiesEntityFields() throws Exception {
        SystemUserEntity user = buildUser(USERNAME, RAW_PASSWORD);
        user.setId(7L);
        user.setEmail("admin@datapoly.local");
        user.setLocked(Boolean.FALSE);
        SystemUserService service = newService(new SystemUserDao() {
            @Override
            public SystemUserEntity getById(Long id) {
                return Long.valueOf(7L).equals(id) ? user : null;
            }
        });

        SystemUserDetailResponse detail = service.getUserDetailById(7L);
        Assert.assertEquals(Long.valueOf(7L), detail.getId());
        Assert.assertEquals(USERNAME, detail.getUsername());
        Assert.assertEquals(USERNAME, detail.getRealName());
        Assert.assertEquals("admin@datapoly.local", detail.getEmail());
        Assert.assertEquals(Boolean.FALSE, detail.getLocked());
    }

    @Test
    public void getUserDetailByIdForUnknownIdFails() throws Exception {
        SystemUserService service = newService(new SystemUserDao() {
            @Override
            public SystemUserEntity getById(Long id) {
                return null;
            }
        });
        try {
            service.getUserDetailById(404L);
            Assert.fail("expected not exists");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void getUserDetailByUsernameCopiesEntityFields() throws Exception {
        SystemUserEntity user = buildUser(USERNAME, RAW_PASSWORD);
        user.setId(9L);
        SystemUserService service = newService(fakeDao(user));

        SystemUserDetailResponse detail = service.getUserDetailByUsername(USERNAME);
        Assert.assertEquals(Long.valueOf(9L), detail.getId());
        Assert.assertEquals(USERNAME, detail.getUsername());
        Assert.assertEquals(USERNAME, detail.getRealName());
    }

    @Test
    public void getUserDetailByUsernameForUnknownUserFails() throws Exception {
        SystemUserService service = newService(fakeDao(null));
        try {
            service.getUserDetailByUsername("ghost");
            Assert.fail("expected not exists");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void changeOwnPasswordForMissingUserFails() throws Exception {
        SystemUserService service = newService(fakeDao(null));
        try {
            service.changeOwnPassword(fakeRequest(), "old", "new");
            Assert.fail("expected user not exists");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_USER_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void loginAgainstAccountWithoutStoredHashStillFailsClosed() throws Exception {
        // password column null (e.g. half-provisioned account): constant-time compare
        // treats it as a mismatch instead of throwing an NPE
        SystemUserEntity user = buildUser(USERNAME, RAW_PASSWORD);
        user.setPassword(null);
        SystemUserService service = newService(fakeDao(user));
        try {
            service.login(USERNAME, RAW_PASSWORD);
            Assert.fail("expected password wrong");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_USER_PASSWORD_WRONG, e.getCode());
        }
    }

    private static SystemUserService newService(SystemUserDao dao) throws Exception {
        return newService(dao, new LoginSessionService(new InMemoryLoginTokenDao(),
                LoginSessionService.DEFAULT_TTL_SECONDS));
    }

    private static SystemUserService newService(SystemUserDao dao,
                                                LoginSessionService loginSessionService) throws Exception {
        SystemUserService service = new SystemUserService();
        setField(service, "systemUserDao", dao);
        setField(service, "loginSessionService", loginSessionService);
        return service;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = SystemUserService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static SystemUserDao fakeDao(SystemUserEntity user) {
        return new SystemUserDao() {
            @Override
            public SystemUserEntity findByUsername(String username) {
                return user;
            }
        };
    }

    private static SystemUserEntity buildUser(String username, String rawPassword) {
        String salt = BCrypt.gensalt();
        SystemUserEntity user = new SystemUserEntity();
        user.setUsername(username);
        user.setRealName(username);
        user.setSalt(salt);
        user.setPassword(PasswordUtils.encryptPassword(rawPassword, salt));
        user.setRole("ADMIN");
        return user;
    }

    private static HttpServletRequest fakeRequest() {
        return fakeRequest(null);
    }

    /** A request carrying {@code token} as its Bearer token (the session making the call). */
    private static HttpServletRequest fakeRequest(String token) {
        return (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                SystemUserServiceTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getAttribute":
                            return USERNAME;
                        case "getHeader":
                            return null == token ? null : "Bearer " + token;
                        default:
                            return null;
                    }
                });
    }
}
