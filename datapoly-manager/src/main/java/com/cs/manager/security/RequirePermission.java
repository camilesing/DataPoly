// Use of this source code is governed by a BSD-style license
package com.cs.manager.security;

import com.cs.common.consts.PermissionAction;
import com.cs.common.consts.PermissionModule;

import java.lang.annotation.*;

/**
 * Marks one manager endpoint as one cell of the permission matrix. The decision is made by the
 * single judge ({@code PermissionService}): ADMIN passes everything, a USER with no row for the
 * module keeps the read-only default ({@code R}), and a row narrows the module to exactly the
 * granted actions. Enforced by the advisor registered in {@code SecurityConfiguration}; a denied
 * call surfaces as {@code ERROR_ACCESS_FORBIDDEN} (HTTP 403) like every other access denial.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    PermissionModule module();

    PermissionAction action();
}
