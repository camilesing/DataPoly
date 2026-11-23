// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import cn.hutool.core.bean.BeanUtil;
import com.cs.common.consts.PermissionAction;
import com.cs.common.consts.PermissionModule;
import com.cs.common.consts.UserRole;
import com.cs.common.dto.PageResult;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.common.util.PasswordUtils;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.dto.SystemUserCreateRequest;
import com.cs.core.dto.SystemUserManageResponse;
import com.cs.core.dto.SystemUserResetPasswordRequest;
import com.cs.core.dto.SystemUserUpdateRequest;
import com.cs.core.dto.UserPermissionResponse;
import com.cs.core.dto.UserPermissionSaveRequest;
import com.cs.core.service.LoginSessionService;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.dao.UserPermissionDao;
import com.cs.persistence.entity.SystemUserEntity;
import com.cs.persistence.entity.UserPermissionEntity;
import com.cs.persistence.util.PageUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Admin console side of the account system: user CRUD, admin password reset and the per-user
 * permission matrix. Guards that live here and nowhere else:
 * <ul>
 *   <li>an operator never changes their own role or lock state (self-lockout / self-elevation);</li>
 *   <li>demoting or locking the last usable ADMIN is rejected — the system must keep one;</li>
 *   <li>a password reset revokes every session of the account, exactly like an own-password
 *       change does.</li>
 * </ul>
 * Role and lock changes take effect on the target's next request — the account row is re-read
 * per request, and permission rows likewise.
 */
@Service
public class SystemUserManageService {

    @Resource
    private SystemUserDao systemUserDao;

    @Resource
    private UserPermissionDao userPermissionDao;

    @Resource
    private LoginSessionService loginSessionService;

    @Resource
    private PermissionService permissionService;

    public PageResult<SystemUserManageResponse> list(EntitySearchRequest request) {
        return PageUtils.getPage(
                () -> {
                    List<SystemUserManageResponse> users = new ArrayList<>();
                    for (SystemUserEntity entity : systemUserDao.listAll(request.getSearchText())) {
                        SystemUserManageResponse response = new SystemUserManageResponse();
                        BeanUtil.copyProperties(entity, response);
                        users.add(response);
                    }
                    return users;
                },
                request.getPage(), request.getSize());
    }

    public void create(SystemUserCreateRequest request) {
        if (systemUserDao.findByUsername(request.getUsername()) != null) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, "user.duplicate");
        }
        String salt = PasswordUtils.newSalt();
        SystemUserEntity user = SystemUserEntity.builder()
                .username(request.getUsername())
                .password(PasswordUtils.encryptPassword(request.getPassword(), salt))
                .salt(salt)
                .realName(request.getRealName())
                .email(request.getEmail())
                .address(request.getAddress())
                .locked(false)
                .role(UserRole.of(request.getRole()).name())
                .build();
        systemUserDao.insert(user);
    }

    public void update(SystemUserUpdateRequest request, String operator) {
        SystemUserEntity target = systemUserDao.getById(request.getId());
        if (target == null) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "id=" + request.getId());
        }

        boolean changesRole = request.getRole() != null && !request.getRole().equals(target.getRole());
        boolean changesLocked = request.getLocked() != null && !request.getLocked().equals(target.getLocked());
        if (target.getUsername().equals(operator) && (changesRole || changesLocked)) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "user.cannot.modify.self");
        }
        if (removesUsableAdmin(target, changesRole, changesLocked) && systemUserDao.countUnlockedAdmins() <= 1) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "user.last.admin");
        }

        SystemUserEntity update = new SystemUserEntity();
        update.setId(target.getId());
        update.setRealName(request.getRealName());
        update.setEmail(request.getEmail());
        update.setAddress(request.getAddress());
        if (changesRole) {
            update.setRole(request.getRole());
        }
        if (changesLocked) {
            update.setLocked(request.getLocked());
        }
        systemUserDao.updateUser(update);
    }

    public void resetPassword(SystemUserResetPasswordRequest request) {
        SystemUserEntity target = systemUserDao.findByUsername(request.getUsername());
        if (target == null) {
            throw new CommonException(ResponseErrorCode.ERROR_USER_NOT_EXISTS, request.getUsername());
        }
        String salt = PasswordUtils.newSalt();
        systemUserDao.updateUserPasswordAndSalt(request.getUsername(),
                PasswordUtils.encryptPassword(request.getNewPassword(), salt), salt);
        loginSessionService.revokeAll(request.getUsername());
    }

    /** Effective matrix of one account (what the UI trims by); ADMIN yields every action. */
    public List<UserPermissionResponse> effectiveMatrix(String username) {
        SystemUserEntity user = requireUser(username);
        return permissionService.effectiveMatrix(username, UserRole.of(user.getRole()));
    }

    /** Configured rows of one account (the admin editor's input); default modules are absent. */
    public List<UserPermissionResponse> configuredMatrix(String username) {
        requireUser(username);
        return permissionService.configuredMatrix(username);
    }

    /**
     * Replaces the account's permission rows wholesale. Unknown modules or actions are rejected
     * (a typo must not silently narrow or widen a grant); an item with an empty action set is
     * dropped — that module falls back to the read-only default. A body without {@code items}
     * (null, like an empty list) wipes every row — the same "back to read-only defaults" save
     * the editor performs with nothing checked.
     */
    public void replacePermissions(UserPermissionSaveRequest request) {
        requireUser(request.getUsername());

        long now = System.currentTimeMillis() / 1000;
        Set<String> seenModules = new HashSet<>();
        List<UserPermissionEntity> rows = new ArrayList<>();
        List<UserPermissionSaveRequest.Item> items = request.getItems() == null
                ? List.of()
                : request.getItems();
        for (UserPermissionSaveRequest.Item item : items) {
            PermissionModule module = parseModule(item.getModule());
            if (!seenModules.add(module.name())) {
                throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                        "user.permission.duplicate.module", module.name());
            }
            EnumSet<PermissionAction> actions = parseActions(item.getActions());
            if (actions.isEmpty()) {
                continue;
            }
            rows.add(UserPermissionEntity.builder()
                    .username(request.getUsername())
                    .module(module.name())
                    .actions(PermissionAction.joinActions(actions))
                    .createAt(now)
                    .build());
        }
        userPermissionDao.replaceByUsername(request.getUsername(), rows);
    }

    private SystemUserEntity requireUser(String username) {
        SystemUserEntity user = systemUserDao.findByUsername(username);
        if (user == null) {
            throw new CommonException(ResponseErrorCode.ERROR_USER_NOT_EXISTS, username);
        }
        return user;
    }

    /**
     * Whether this update removes a usable administrator: the target is an ADMIN that is usable now —
     * not locked — and the update moves the role off ADMIN or changes its lock state (for an unlocked
     * account that can only mean locking it; any differing role value moves off ADMIN). Unlocking or
     * demoting an already locked ADMIN removes nothing.
     */
    private static boolean removesUsableAdmin(SystemUserEntity target, boolean changesRole, boolean changesLocked) {
        return UserRole.ADMIN == UserRole.of(target.getRole())
                && !Boolean.TRUE.equals(target.getLocked())
                && (changesRole || changesLocked);
    }

    private static PermissionModule parseModule(String name) {
        for (PermissionModule module : PermissionModule.values()) {
            if (module.name().equalsIgnoreCase(StringUtils.trimToEmpty(name))) {
                return module;
            }
        }
        throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "user.permission.unknown.module",
                StringUtils.defaultString(name));
    }

    private static EnumSet<PermissionAction> parseActions(List<String> actions) {
        EnumSet<PermissionAction> parsed = EnumSet.noneOf(PermissionAction.class);
        if (actions == null) {
            return parsed;
        }
        for (String raw : actions) {
            String name = StringUtils.trimToEmpty(raw);
            boolean known = false;
            for (PermissionAction action : PermissionAction.values()) {
                if (action.name().equalsIgnoreCase(name)) {
                    parsed.add(action);
                    known = true;
                    break;
                }
            }
            if (!known) {
                throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "user.permission.unknown.action",
                        name);
            }
        }
        return parsed;
    }
}
