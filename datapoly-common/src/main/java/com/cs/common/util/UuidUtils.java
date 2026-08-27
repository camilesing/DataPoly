// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import lombok.experimental.UtilityClass;

import java.util.UUID;

/**
 * UUID utility
 */
@UtilityClass
public final class UuidUtils {

    public static String generateUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
