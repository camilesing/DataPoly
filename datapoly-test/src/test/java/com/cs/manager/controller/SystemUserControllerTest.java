// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.SystemUserDetailResponse;
import com.cs.core.service.SystemUserService;
import com.cs.manager.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/**
 * The user-detail endpoints carry no matrix cell on purpose — they are self-service: an account's
 * profile (name, mail, address) goes to its owner or to an ADMIN, and to nobody else.
 */
public class SystemUserControllerTest {

    private StubUserService service;
    private StubPermissionService permissionService;
    private SystemUserController controller;

    @Before
    public void setUp() {
        service = new StubUserService();
        permissionService = new StubPermissionService();
        controller = new SystemUserController();
        DataTaskTestSupport.setField(controller, "systemUserService", service);
        DataTaskTestSupport.setField(controller, "permissionService", permissionService);
    }

    private static HttpServletRequest caller(String username) {
        return (HttpServletRequest) Proxy.newProxyInstance(SystemUserControllerTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (p, m, a) -> {
                    if ("getAttribute".equals(m.getName())) {
                        return username;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
    }

    @Test
    public void ownerReadsOwnProfileByNameAndById() {
        assertEquals("alice", controller.getUserByName("alice", caller("alice")).getData().getUsername());
        assertEquals("alice", controller.getUserById(7L, caller("alice")).getData().getUsername());
        assertEquals("self-service needs no admin check", 0, permissionService.adminChecks);
    }

    /**
     * Regression: both endpoints took any id or username, so every logged-in account could read
     * every other account's name, mail and address.
     */
    @Test
    public void anotherAccountsProfileIsRefusedForAPlainUser() {
        try {
            controller.getUserByName("alice", caller("bob"));
            fail("a plain user must not read another account's profile");
        } catch (AuthorizationDeniedException expected) {
            // refused before the profile is read
            assertNull(service.requestedUsername);
        }

        try {
            controller.getUserById(7L, caller("bob"));
            fail("a plain user must not read another account's profile by id");
        } catch (AuthorizationDeniedException expected) {
            // refused
        }
        assertEquals(2, permissionService.adminChecks);
    }

    @Test
    public void adminReadsAnyProfile() {
        permissionService.admin = true;

        assertEquals("alice", controller.getUserByName("alice", caller("admin-root")).getData().getUsername());
        assertEquals("alice", controller.getUserById(7L, caller("admin-root")).getData().getUsername());
        assertEquals(2, permissionService.adminChecks);
    }

    private static class StubUserService extends SystemUserService {
        String requestedUsername;

        @Override
        public SystemUserDetailResponse getUserDetailByUsername(String username) {
            requestedUsername = username;
            return detail();
        }

        @Override
        public SystemUserDetailResponse getUserDetailById(Long id) {
            return detail();
        }

        private static SystemUserDetailResponse detail() {
            SystemUserDetailResponse detail = new SystemUserDetailResponse();
            detail.setId(7L);
            detail.setUsername("alice");
            detail.setRealName("Alice");
            detail.setEmail("alice@example.com");
            return detail;
        }
    }

    private static class StubPermissionService extends PermissionService {
        boolean admin = false;
        int adminChecks;

        @Override
        public void checkAdminCurrent() {
            adminChecks++;
            if (!admin) {
                throw new AuthorizationDeniedException("admin only");
            }
        }
    }
}
