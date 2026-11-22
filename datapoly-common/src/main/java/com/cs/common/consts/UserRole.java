// Use of this source code is governed by a BSD-style license
package com.cs.common.consts;

import org.apache.commons.lang3.StringUtils;

/**
 * Values of {@code DATAPOLY_SYSTEM_USER.user_role}. The column is a bare varchar, so this enum is
 * the one place that knows the two legal values and how they map onto Spring Security authorities.
 * Anything unrecognized (dirty data only — the column is {@code not null default 'USER'}) reads as
 * {@link #USER}, failing closed to the lower privilege.
 */
public enum UserRole {

    ADMIN,
    USER;

    /** Spring Security authority of this role, i.e. what {@code hasRole("ADMIN")} matches. */
    public String authority() {
        return "ROLE_" + name();
    }

    public static UserRole of(String role) {
        return ADMIN.name().equalsIgnoreCase(StringUtils.trimToEmpty(role)) ? ADMIN : USER;
    }
}
