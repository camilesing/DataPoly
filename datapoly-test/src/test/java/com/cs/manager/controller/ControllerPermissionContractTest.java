// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.manager.security.RequireAdmin;
import com.cs.manager.security.RequirePermission;
import org.junit.Assert;
import org.junit.Test;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The machine-checked half of the permission convention: every manager endpoint must carry a
 * security mark ({@code @RequirePermission} for the matrix, {@code @RequireAdmin} for the
 * hardcoded admin-only set) or appear in the explicit self-service/permitAll allowlist below —
 * so a newly added endpoint fails this test until someone decides its permission cell. That
 * decision is meant to be deliberate, never inherited from "forgot to annotate".
 */
public class ControllerPermissionContractTest {

    /** Endpoints that authenticate differently or act only on the caller's own account. */
    private static final Set<String> ALLOWED_UNMARKED = new HashSet<>(Arrays.asList(
            "AliveHealthController#getProjectVersion",   // health probe, permitAll in the chain
            "AuthenticationController#login",            // the credential exchange itself
            "AuthenticationController#logout",           // self-service
            "SystemUserController#getUserById",          // self-service detail lookups
            "SystemUserController#getUserByName",
            "SystemUserController#changeOwnPassword",    // own password only
            "SystemUserController#selfPermissions"));    // own effective matrix

    private static final Class<?>[] CONTROLLERS = {
            AliveHealthController.class,
            ApiAssignmentController.class,
            ApiGroupController.class,
            ApiModuleController.class,
            AppClientController.class,
            AuthenticationController.class,
            DataSourceController.class,
            DataTaskController.class,
            FirewallController.class,
            McpClientController.class,
            McpToolController.class,
            NodeController.class,
            OverviewController.class,
            SystemParamController.class,
            SystemUserController.class,
            UnifyAlarmController.class,
            VersionCommitController.class,
    };

    @Test
    public void everyEndpointCarriesAPermissionMarkOrIsExplicitlyAllowlisted() {
        Set<String> unmarked = new HashSet<>();
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                String id = controller.getSimpleName() + "#" + method.getName();
                if (method.getAnnotation(RequirePermission.class) == null
                        && method.getAnnotation(RequireAdmin.class) == null
                        && !ALLOWED_UNMARKED.contains(id)) {
                    unmarked.add(id);
                }
            }
        }
        Assert.assertTrue("Endpoints without a permission mark (annotate them or extend the "
                + "allowlist deliberately): " + unmarked, unmarked.isEmpty());
    }

    private static boolean isEndpoint(Method method) {
        return method.getAnnotation(GetMapping.class) != null
                || method.getAnnotation(PostMapping.class) != null
                || method.getAnnotation(PutMapping.class) != null
                || method.getAnnotation(DeleteMapping.class) != null
                || method.getAnnotation(PatchMapping.class) != null
                || method.getAnnotation(RequestMapping.class) != null;
    }
}
