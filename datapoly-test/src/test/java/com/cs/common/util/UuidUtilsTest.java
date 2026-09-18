// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class UuidUtilsTest {

    @Test
    public void testGenerateUuidIs32HexCharsWithoutDashes() {
        String uuid = UuidUtils.generateUuid();
        assertEquals(32, uuid.length());
        assertTrue(uuid.matches("[0-9a-f]{32}"));
        assertNotEquals(uuid, UuidUtils.generateUuid());
    }
}
