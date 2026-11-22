// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.common.consts.UserRole;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.common.util.PasswordUtils;
import com.cs.core.dto.SystemUserCreateRequest;
import com.cs.core.dto.SystemUserResetPasswordRequest;
import com.cs.core.dto.SystemUserUpdateRequest;
import com.cs.core.dto.UserPermissionSaveRequest;
import com.cs.core.service.LoginSessionService;
import com.cs.persistence.dao.LoginTokenDao;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.dao.UserPermissionDao;
import com.cs.persistence.entity.SystemUserEntity;
import com.cs.persistence.entity.UserPermissionEntity;
import org.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin console guards on fake DAOs: the self-modification and last-admin protections, the
 * all-sessions revocation behind a password reset, and the strict parsing of permission saves.
 */
public class SystemUserManageServiceTest {

    private FakeUserDao userDao;
    private RecordingPermissionDao permissionDao;
    private RecordingLoginSessionService loginSessionService;
    private SystemUserManageService service;

    @Before
    public void setUp() {
        userDao = new FakeUserDao();
        permissionDao = new RecordingPermissionDao();
        loginSessionService = new RecordingLoginSessionService();
        service = new SystemUserManageService();
        setField(service, "systemUserDao", userDao);
        setField(service, "userPermissionDao", permissionDao);
        setField(service, "loginSessionService", loginSessionService);
        setField(service, "permissionService", newPermissionService());
    }

    @Test
    public void createRejectsADuplicateUsername() {
        userDao.users.put("test", user("test", "USER"));
        try {
            service.create(createRequest("test", "USER"));
            Assert.fail("expected duplicate rejection");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, e.getCode());
        }
    }

    @Test
    public void createHashesAFreshSaltPerUser() {
        service.create(createRequest("newcomer", "USER"));
        SystemUserEntity inserted = userDao.users.get("newcomer");
        Assert.assertNotNull(inserted);
        Assert.assertEquals(UserRole.USER.name(), inserted.getRole());
        Assert.assertEquals(Boolean.FALSE, inserted.getLocked());
        Assert.assertNotEquals("plain", inserted.getPassword());
        Assert.assertTrue(inserted.getPassword(), inserted.getPassword().startsWith("$2"));
        // the stored hash verifies against the stored salt
        Assert.assertEquals(inserted.getPassword(), PasswordUtils.encryptPassword("longenough", inserted.getSalt()));
    }

    @Test
    public void updateRejectsChangingYourOwnRoleOrLock() {
        userDao.users.put("test", user("test", "ADMIN"));
        userDao.users.put("other", user("other", "USER"));

        SystemUserUpdateRequest selfRole = updateRequest("test", "USER", null);
        try {
            service.update(selfRole, "test");
            Assert.fail("expected self-role rejection");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }

        SystemUserUpdateRequest selfLock = updateRequest("test", null, Boolean.TRUE);
        try {
            service.update(selfLock, "test");
            Assert.fail("expected self-lock rejection");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateRejectsDemotingOrLockingTheLastUsableAdmin() {
        userDao.users.put("solo", user("solo", "ADMIN"));
        SystemUserUpdateRequest demote = updateRequest("solo", "USER", null);
        try {
            service.update(demote, "other-admin");
            Assert.fail("expected last-admin rejection");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }

        SystemUserUpdateRequest lock = updateRequest("solo", null, Boolean.TRUE);
        try {
            service.update(lock, "other-admin");
            Assert.fail("expected last-admin rejection");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        Assert.assertTrue(userDao.updated.isEmpty());
    }

    @Test
    public void updateDemotesWhenAnotherAdminRemains() {
        userDao.users.put("solo", user("solo", "ADMIN"));
        userDao.users.put("backup", user("backup", "ADMIN"));
        service.update(updateRequest("solo", "USER", null), "backup");
        Assert.assertEquals(UserRole.USER.name(),
                userDao.updated.get(String.valueOf(user("solo", null).getId())).getRole());
    }

    @Test
    public void updateOfAPlainUserNeedsNoAdminQuorum() {
        userDao.users.put("test", user("test", "USER"));
        service.update(updateRequest("test", "USER", Boolean.TRUE), "admin");
        Assert.assertEquals(Boolean.TRUE,
                userDao.updated.get(String.valueOf(user("test", null).getId())).getLocked());
    }

    @Test
    public void resetPasswordRotatesSaltAndRevokesEverySession() {
        userDao.users.put("test", user("test", "USER"));
        SystemUserResetPasswordRequest request = new SystemUserResetPasswordRequest();
        request.setUsername("test");
        request.setNewPassword("rotated-pass");
        service.resetPassword(request);

        Assert.assertEquals(1, loginSessionService.revokedAllUsernames.size());
        Assert.assertEquals("test", loginSessionService.revokedAllUsernames.get(0));
        // a reset rotates the salt together with the hash — the old credential is not reusable
        Assert.assertNotEquals("$2a$10$datapolyoldsalt000000000", userDao.updatedSalt.get("test"));
        Assert.assertNotEquals("old-hash", userDao.updatedPassword.get("test"));
        Assert.assertTrue(userDao.updatedPassword.get("test"), userDao.updatedPassword.get("test").startsWith("$2"));
    }

    @Test
    public void resetPasswordOfAnUnknownUserFails() {
        try {
            SystemUserResetPasswordRequest request = new SystemUserResetPasswordRequest();
            request.setUsername("ghost");
            request.setNewPassword("rotated-pass");
            service.resetPassword(request);
            Assert.fail("expected not-exists");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_USER_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void permissionSaveRejectsUnknownModuleActionAndDuplicates() {
        userDao.users.put("test", user("test", "USER"));

        Assert.assertThrows(CommonException.class, () -> service.replacePermissions(save("test", item("HACKME", "R"))));
        Assert.assertThrows(CommonException.class, () -> service.replacePermissions(save("test", item("DATASOURCE", "Z"))));
        Assert.assertThrows(CommonException.class, () -> service.replacePermissions(save("test",
                item("DATASOURCE", "R"), item("DATASOURCE", "C"))));
        Assert.assertTrue(permissionDao.replaced.isEmpty());
    }

    @Test
    public void permissionSaveDropsEmptyRowsAndJoinsActions() {
        userDao.users.put("test", user("test", "USER"));
        service.replacePermissions(save("test",
                item("DATASOURCE", Arrays.asList("R", "C", "U")),
                item("FIREWALL", new ArrayList<>())));

        List<UserPermissionEntity> rows = permissionDao.replaced.get("test");
        Assert.assertEquals(1, rows.size());
        Assert.assertEquals("DATASOURCE", rows.get(0).getModule());
        Assert.assertEquals("R,C,U", rows.get(0).getActions());
        Assert.assertEquals("test", rows.get(0).getUsername());
    }

    private static PermissionService newPermissionService() {
        PermissionService permissionService = new PermissionService();
        setField(permissionService, "userPermissionDao", new UserPermissionDao());
        return permissionService;
    }

    private static SystemUserCreateRequest createRequest(String username, String role) {
        SystemUserCreateRequest request = new SystemUserCreateRequest();
        request.setUsername(username);
        request.setPassword("longenough");
        request.setRole(role);
        request.setRealName("Real " + username);
        return request;
    }

    private static SystemUserUpdateRequest updateRequest(String username, String role, Boolean locked) {
        SystemUserEntity target = user(username, null);
        SystemUserUpdateRequest request = new SystemUserUpdateRequest();
        request.setId(target.getId());
        request.setRole(role);
        request.setLocked(locked);
        return request;
    }

    private static UserPermissionSaveRequest save(String username, UserPermissionSaveRequest.Item... items) {
        UserPermissionSaveRequest request = new UserPermissionSaveRequest();
        request.setUsername(username);
        request.setItems(new ArrayList<>(Arrays.asList(items)));
        return request;
    }

    private static UserPermissionSaveRequest.Item item(String module, String... actions) {
        return item(module, Arrays.asList(actions));
    }

    private static UserPermissionSaveRequest.Item item(String module, List<String> actions) {
        UserPermissionSaveRequest.Item item = new UserPermissionSaveRequest.Item();
        item.setModule(module);
        item.setActions(actions);
        return item;
    }

    private static SystemUserEntity user(String username, String role) {
        SystemUserEntity entity = new SystemUserEntity();
        entity.setId((long) (username.hashCode() & 0xffffff));
        entity.setUsername(username);
        entity.setPassword("old-hash");
        entity.setSalt("$2a$10$datapolyoldsalt000000000");
        entity.setLocked(false);
        entity.setRole(role);
        return entity;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class FakeUserDao extends SystemUserDao {

        private final Map<String, SystemUserEntity> users = new LinkedHashMap<>();
        private final Map<String, SystemUserEntity> updated = new LinkedHashMap<>();
        private final Map<String, String> updatedPassword = new LinkedHashMap<>();
        private final Map<String, String> updatedSalt = new LinkedHashMap<>();

        @Override
        public SystemUserEntity getById(Long id) {
            for (SystemUserEntity user : users.values()) {
                if (user.getId().equals(id)) {
                    return user;
                }
            }
            return null;
        }

        @Override
        public SystemUserEntity findByUsername(String username) {
            return users.get(username);
        }

        @Override
        public void insert(SystemUserEntity userEntity) {
            users.put(userEntity.getUsername(), userEntity);
        }

        @Override
        public long countUnlockedAdmins() {
            long count = 0;
            for (SystemUserEntity user : users.values()) {
                if (UserRole.ADMIN.name().equals(user.getRole()) && !Boolean.TRUE.equals(user.getLocked())) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public void updateUser(SystemUserEntity user) {
            updated.put(String.valueOf(user.getId()), user);
            SystemUserEntity existing = users.get(user.getUsername());
            if (existing != null) {
                if (user.getRole() != null) {
                    existing.setRole(user.getRole());
                }
                if (user.getLocked() != null) {
                    existing.setLocked(user.getLocked());
                }
                if (user.getRealName() != null) {
                    existing.setRealName(user.getRealName());
                }
            }
        }

        @Override
        public void updateUserPasswordAndSalt(String username, String password, String salt) {
            updatedPassword.put(username, password);
            updatedSalt.put(username, salt);
        }
    }

    private static class RecordingPermissionDao extends UserPermissionDao {

        private final Map<String, List<UserPermissionEntity>> replaced = new LinkedHashMap<>();

        @Override
        public void replaceByUsername(String username, List<UserPermissionEntity> rows) {
            replaced.put(username, rows);
        }
    }

    /** Records revocations without touching a table; the real class is exercised elsewhere. */
    private static class RecordingLoginSessionService extends LoginSessionService {

        private final List<String> revokedAllUsernames = new ArrayList<>();

        private RecordingLoginSessionService() {
            super(new LoginTokenDao() {
            }, 604800L);
        }

        @Override
        public void revokeAll(String username) {
            revokedAllUsernames.add(username);
        }
    }
}
