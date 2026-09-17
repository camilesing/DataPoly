// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import cn.hutool.crypto.digest.BCrypt;
import org.junit.Test;

import static org.junit.Assert.*;

public class PasswordUtilsTest {

    @Test
    public void testEncryptPasswordVerifiesWithBcrypt() {
        String salt = BCrypt.gensalt();
        String hashed = PasswordUtils.encryptPassword("secret-123", salt);
        assertNotEquals("secret-123", hashed);
        assertTrue(BCrypt.checkpw("secret-123", hashed));
        assertFalse(BCrypt.checkpw("wrong-password", hashed));
    }
}
