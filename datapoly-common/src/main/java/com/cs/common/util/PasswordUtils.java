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

    /** A fresh bcrypt salt for a new credential (user creation, admin password reset). */
    public static String newSalt() {
        return BCrypt.gensalt();
    }

}
