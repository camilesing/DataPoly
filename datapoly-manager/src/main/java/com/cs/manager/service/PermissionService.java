// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.common.consts.PermissionAction;
import com.cs.common.consts.PermissionModule;
import com.cs.common.consts.UserRole;
import com.cs.common.util.I18nUtils;
import com.cs.core.dto.UserPermissionResponse;
import com.cs.persistence.dao.UserPermissionDao;
import com.cs.persistence.entity.UserPermissionEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one judge of the permission matrix. Semantics (mirrored by the user-permission table's
 * migration comment and the manager-ui's permission store — do not fork them):
 * <ul>
 *   <li>ADMIN passes everything and never reads the table;</li>
 *   <li>a USER with no row for a module keeps the read-only default: {@code R} only;</li>
 *   <li>a row narrows that module to exactly the actions it lists (so a row without {@code R}
 *       revokes even the read).</li>
 * </ul>
 * Rows are looked up per call, like the account row, so a grant or a revocation applies to the
 * very next request. The role is taken from the authentication the token filter built out of the
 * freshly-read account row — never from anything the client sent.
 */
@Service
public class PermissionService {

    @Resource
    private UserPermissionDao userPermissionDao;

    /** Decision of the current request's caller; throws {@link AuthorizationDeniedException} when denied. */
    public void checkCurrent(PermissionModule module, PermissionAction action) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!allows(authentication, module, action)) {
            // NOTE the explicit Object[]: I18nUtils has a more specific (key, String defaultMessage,
            // args) overload — a bare String arg binds to defaultMessage there and {0} never fills in
            throw new AuthorizationDeniedException(I18nUtils.getMessage("auth.no.permission",
                    new Object[]{module.name() + "/" + action.name()}));
        }
    }

    /** Admin-only mark: ADMIN passes, everyone else is denied regardless of any matrix row. */
    public void checkAdminCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAdmin(authentication)) {
            throw new AuthorizationDeniedException(I18nUtils.getMessage("auth.admin.only"));
        }
    }

    /** Whether {@code authentication} may perform {@code action} on {@code module}. */
    public boolean allows(Authentication authentication, PermissionModule module, PermissionAction action) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        UserPermissionEntity row = userPermissionDao.findByUsernameAndModule(authentication.getName(), module.name());
        if (row == null) {
            return action == PermissionAction.R;
        }
        return PermissionAction.parseActions(row.getActions()).contains(action);
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().contains(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
    }

    /**
     * The caller's effective matrix, one entry per module (every action for ADMIN, the read-only
     * default for unconfigured modules) — what the UI trims menus and buttons by.
     */
    public List<UserPermissionResponse> effectiveMatrix(String username, UserRole role) {
        List<UserPermissionResponse> matrix = new ArrayList<>();
        boolean admin = role == UserRole.ADMIN;
        Map<String, EnumSet<PermissionAction>> rows = rowsByUsername(username);
        for (PermissionModule module : PermissionModule.values()) {
            EnumSet<PermissionAction> actions = rows.get(module.name());
            List<String> granted = new ArrayList<>();
            if (admin) {
                for (PermissionAction action : PermissionAction.values()) {
                    granted.add(action.name());
                }
            } else if (actions == null) {
                granted.add(PermissionAction.R.name());
            } else {
                for (PermissionAction action : PermissionAction.values()) {
                    if (actions.contains(action)) {
                        granted.add(action.name());
                    }
                }
            }
            matrix.add(new UserPermissionResponse(module.name(), granted, actions != null));
        }
        return matrix;
    }

    /**
     * The configured rows of one account, for the admin editor: only modules with a row appear,
     * {@code customized} is always true and the default modules are the complement.
     */
    public List<UserPermissionResponse> configuredMatrix(String username) {
        List<UserPermissionResponse> configured = new ArrayList<>();
        for (UserPermissionEntity row : userPermissionDao.findByUsername(username)) {
            if (StringUtils.isBlank(row.getModule())) {
                continue;
            }
            List<String> granted = new ArrayList<>();
            for (PermissionAction action : PermissionAction.values()) {
                if (PermissionAction.parseActions(row.getActions()).contains(action)) {
                    granted.add(action.name());
                }
            }
            configured.add(new UserPermissionResponse(row.getModule(), granted, true));
        }
        return configured;
    }

    private Map<String, EnumSet<PermissionAction>> rowsByUsername(String username) {
        Map<String, EnumSet<PermissionAction>> rows = new HashMap<>();
        for (UserPermissionEntity row : userPermissionDao.findByUsername(username)) {
            if (StringUtils.isNotBlank(row.getModule())) {
                rows.put(row.getModule(), PermissionAction.parseActions(row.getActions()));
            }
        }
        return rows;
    }
}
