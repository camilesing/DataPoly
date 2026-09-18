// Use of this source code is governed by a BSD-style license
package com.cs.persistence.dao;

import org.junit.Test;

import static org.junit.Assert.*;

public class ListGuardTest {

    @Test
    public void testGuardConstants() {
        assertEquals(10_000, ListGuard.MAX_ROWS);
        assertEquals("LIMIT 10000", ListGuard.LIMIT_SQL);
    }

    @Test
    public void testWarnIfHitDoesNotThrowAtOrBelowBound() {
        ListGuard.warnIfHit("api online", 9_999);
        ListGuard.warnIfHit("api online", 10_000);
    }
}
