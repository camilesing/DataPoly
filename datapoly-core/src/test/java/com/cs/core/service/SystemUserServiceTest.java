// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import cn.hutool.crypto.digest.BCrypt;
import com.cs.common.dto.AccessToken;
import com.cs.common.exception.*;
import com.cs.common.util.PasswordUtils;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.entity.SystemUserEntity;
import org.junit.*;

import javax.servlet.http.HttpServletRequest;
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

    private static SystemUserService newService(SystemUserDao dao) throws Exception {
        SystemUserService service = new SystemUserService();
        Field field = SystemUserService.class.getDeclaredField("systemUserDao");
        field.setAccessible(true);
        field.set(service, dao);
        return service;
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
        return user;
    }

    private static HttpServletRequest fakeRequest() {
        return (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                SystemUserServiceTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> "getAttribute".equals(method.getName()) ? USERNAME : null);
    }
}
