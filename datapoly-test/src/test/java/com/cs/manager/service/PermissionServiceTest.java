// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.common.consts.PermissionAction;
import com.cs.common.consts.PermissionModule;
import com.cs.common.consts.UserRole;
import com.cs.core.dto.UserPermissionResponse;
import com.cs.persistence.dao.UserPermissionDao;
import com.cs.persistence.entity.UserPermissionEntity;
import org.junit.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single judge of the permission matrix, on fake rows (no Spring context): the read-only
 * default, the exact-row semantics (a row without R revokes even the read), the ADMIN bypass
 * that never touches the table, and the effective matrix the UI trims by.
 */
public class PermissionServiceTest {

    private FakePermissionDao permissionDao;
    private PermissionService permissionService;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        permissionDao = new FakePermissionDao();
        permissionService = new PermissionService();
        setField(permissionService, "userPermissionDao", permissionDao);
    }

    @After
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void adminPassesEverythingWithoutReadingTheTable() {
        for (PermissionModule module : PermissionModule.values()) {
            for (PermissionAction action : PermissionAction.values()) {
                Assert.assertTrue(permissionService.allows(admin(), module, action));
            }
        }
        Assert.assertEquals(0, permissionDao.lookups);
    }

    @Test
    public void unconfiguredModuleIsReadOnly() {
        for (PermissionAction action : PermissionAction.values()) {
            boolean expected = action == PermissionAction.R;
            Assert.assertEquals(expected, permissionService.allows(user(), PermissionModule.DATASOURCE, action));
        }
    }

    @Test
    public void configuredRowIsExact() {
        permissionDao.rows.put("test|DATASOURCE", row("DATASOURCE", "R,C"));
        Assert.assertTrue(permissionService.allows(user(), PermissionModule.DATASOURCE, PermissionAction.R));
        Assert.assertTrue(permissionService.allows(user(), PermissionModule.DATASOURCE, PermissionAction.C));
        Assert.assertFalse(permissionService.allows(user(), PermissionModule.DATASOURCE, PermissionAction.U));
        Assert.assertFalse(permissionService.allows(user(), PermissionModule.DATASOURCE, PermissionAction.D));
        Assert.assertFalse(permissionService.allows(user(), PermissionModule.DATASOURCE, PermissionAction.X));
        // other modules keep their default
        Assert.assertTrue(permissionService.allows(user(), PermissionModule.FIREWALL, PermissionAction.R));
        Assert.assertFalse(permissionService.allows(user(), PermissionModule.FIREWALL, PermissionAction.U));
    }

    @Test
    public void rowWithoutReadRevokesEvenTheRead() {
        permissionDao.rows.put("test|DATASOURCE", row("DATASOURCE", "C,U"));
        Assert.assertFalse(permissionService.allows(user(), PermissionModule.DATASOURCE, PermissionAction.R));
    }

    @Test
    public void missingAuthenticationIsDenied() {
        Assert.assertFalse(permissionService.allows(null, PermissionModule.DATASOURCE, PermissionAction.R));
    }

    @Test
    public void checkCurrentThrowsWhenDenied() {
        try {
            SecurityContextHolder.getContext().setAuthentication(user());
            permissionService.checkCurrent(PermissionModule.DATASOURCE, PermissionAction.C);
            Assert.fail("expected denial");
        } catch (AuthorizationDeniedException e) {
            // Without a Spring context I18nUtils falls back to the key itself; the formatted
            // "无权访问: 模块/操作" text is covered by the container smoke test and by
            // I18nMessageFormatTest, so here only the denial type matters
            Assert.assertNotNull(e.getMessage());
        }
    }

    @Test
    public void checkCurrentPassesWhenGranted() {
        SecurityContextHolder.getContext().setAuthentication(user());
        permissionDao.rows.put("test|DATASOURCE", row("DATASOURCE", "C"));
        permissionService.checkCurrent(PermissionModule.DATASOURCE, PermissionAction.C);
    }

    @Test
    public void checkAdminCurrentDeniesUsersAndPassesAdmins() {
        SecurityContextHolder.getContext().setAuthentication(user());
        try {
            permissionService.checkAdminCurrent();
            Assert.fail("expected denial");
        } catch (AuthorizationDeniedException ignored) {
            // expected
        }

        SecurityContextHolder.getContext().setAuthentication(admin());
        permissionService.checkAdminCurrent();
    }

    @Test
    public void effectiveMatrixOfAnAdminGrantsEveryAction() {
        List<UserPermissionResponse> matrix = permissionService.effectiveMatrix("test", UserRole.ADMIN);
        Assert.assertEquals(PermissionModule.values().length, matrix.size());
        for (UserPermissionResponse entry : matrix) {
            Assert.assertEquals(PermissionAction.values().length, entry.getActions().size());
            Assert.assertFalse(entry.getCustomized());
        }
    }

    @Test
    public void effectiveMatrixOfAPlainUserIsReadOnlyByDefault() {
        List<UserPermissionResponse> matrix = permissionService.effectiveMatrix("test", UserRole.USER);
        for (UserPermissionResponse entry : matrix) {
            Assert.assertEquals(List.of("R"), entry.getActions());
            Assert.assertFalse(entry.getCustomized());
        }
    }

    @Test
    public void effectiveMatrixMixesRowsAndDefaults() {
        permissionDao.rows.put("test|API", row("API", "C"));
        Map<String, UserPermissionResponse> byModule = new HashMap<>();
        for (UserPermissionResponse entry : permissionService.effectiveMatrix("test", UserRole.USER)) {
            byModule.put(entry.getModule(), entry);
        }
        Assert.assertEquals(List.of("C"), byModule.get("API").getActions());
        Assert.assertTrue(byModule.get("API").getCustomized());
        Assert.assertEquals(List.of("R"), byModule.get("DATASOURCE").getActions());
        Assert.assertFalse(byModule.get("DATASOURCE").getCustomized());
    }

    @Test
    public void configuredMatrixOnlyListsRows() {
        permissionDao.rows.put("test|API", row("API", "R,C"));
        permissionDao.rows.put("test|FIREWALL", row("FIREWALL", "U"));
        Map<String, UserPermissionResponse> byModule = new HashMap<>();
        for (UserPermissionResponse entry : permissionService.configuredMatrix("test")) {
            byModule.put(entry.getModule(), entry);
        }
        Assert.assertEquals(2, byModule.size());
        Assert.assertEquals(java.util.List.of("R", "C"), byModule.get("API").getActions());
        Assert.assertEquals(java.util.List.of("U"), byModule.get("FIREWALL").getActions());
    }

    private static Authentication user() {
        return UsernamePasswordAuthenticationToken.authenticated("test", "token",
                List.of(new SimpleGrantedAuthority(UserRole.USER.authority())));
    }

    private static Authentication admin() {
        return UsernamePasswordAuthenticationToken.authenticated("boss", "token",
                List.of(new SimpleGrantedAuthority(UserRole.ADMIN.authority())));
    }

    private static UserPermissionEntity row(String module, String actions) {
        return UserPermissionEntity.builder().username("test").module(module).actions(actions).createAt(0L).build();
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

    private static class FakePermissionDao extends UserPermissionDao {

        private final Map<String, UserPermissionEntity> rows = new LinkedHashMap<>();
        private int lookups;

        @Override
        public List<UserPermissionEntity> findByUsername(String username) {
            List<UserPermissionEntity> found = new ArrayList<>();
            for (UserPermissionEntity row : rows.values()) {
                if (row.getUsername().equals(username)) {
                    found.add(row);
                }
            }
            return found;
        }

        @Override
        public UserPermissionEntity findByUsernameAndModule(String username, String module) {
            lookups++;
            for (UserPermissionEntity row : rows.values()) {
                if (row.getUsername().equals(username) && row.getModule().equals(module)) {
                    return row;
                }
            }
            return null;
        }
    }
}
