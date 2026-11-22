// Use of this source code is governed by a BSD-style license
package com.cs.manager.security;

import java.lang.annotation.*;

/**
 * Marks an endpoint as Admin-only outside the permission matrix — user/permission management and
 * secret plaintexts (appSecret, MCP token). These can never be granted to a USER through the
 * matrix, otherwise a granted USER could elevate themselves.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
