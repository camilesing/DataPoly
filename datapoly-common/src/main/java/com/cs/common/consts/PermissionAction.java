// Use of this source code is governed by a BSD-style license
package com.cs.common.consts;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The operation axis of the permission matrix. Publish/deploy/retire/revert/save all modify
 * existing state and count as {@link #U}; parse/debug/preview/submit/test actually run something
 * and count as {@link #X}. Persisted as a comma set of enum names in
 * {@code DATAPOLY_USER_PERMISSION.actions}.
 */
public enum PermissionAction {

    R,
    C,
    U,
    D,
    X;

    /** Parses {@code "R,C,U"} (order-free, blanks tolerated); unknown names are dropped. */
    public static EnumSet<PermissionAction> parseActions(String actions) {
        EnumSet<PermissionAction> parsed = EnumSet.noneOf(PermissionAction.class);
        for (String token : StringUtils.defaultString(actions).split(",")) {
            String name = StringUtils.trimToNull(token);
            if (name != null) {
                for (PermissionAction action : values()) {
                    if (action.name().equalsIgnoreCase(name)) {
                        parsed.add(action);
                        break;
                    }
                }
            }
        }
        return parsed;
    }

    public static String joinActions(EnumSet<PermissionAction> actions) {
        List<String> names = new ArrayList<>();
        for (PermissionAction action : values()) {
            if (actions.contains(action)) {
                names.add(action.name());
            }
        }
        return String.join(",", names);
    }
}
