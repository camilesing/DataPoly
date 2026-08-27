// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import cn.hutool.crypto.digest.BCrypt;

/**
 * Password utility
 */
public final class PasswordUtils {

    public static String encryptPassword(String password, String credentialsSalt) {
        return BCrypt.hashpw(password, credentialsSalt);
    }

}
