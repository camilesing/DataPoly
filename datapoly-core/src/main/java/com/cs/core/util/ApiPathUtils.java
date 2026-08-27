// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.consts.Constants;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class ApiPathUtils {

    public static String getFullPath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return String.format("/%s/%s", Constants.API_PATH_PREFIX, path);
    }
}
