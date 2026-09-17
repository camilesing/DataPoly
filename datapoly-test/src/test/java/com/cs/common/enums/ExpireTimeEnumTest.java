// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExpireTimeEnumTest {

    @Test
    public void testFromMatchesExactDurationAndValue() {
        assertSame(ExpireTimeEnum.EXPIRE_FOR_EVER, ExpireTimeEnum.from(DurationTimeEnum.FOR_EVER, -1L));
        assertSame(ExpireTimeEnum.EXPIRE_ONLY_ONCE, ExpireTimeEnum.from(DurationTimeEnum.ONLY_ONCE, 0L));
        assertSame(ExpireTimeEnum.EXPIRE_05_MIN, ExpireTimeEnum.from(DurationTimeEnum.TIME_VALUE, 5 * 60L));
        assertSame(ExpireTimeEnum.EXPIRE_01_MOUTH, ExpireTimeEnum.from(DurationTimeEnum.TIME_VALUE, 30 * 24 * 3600L));
    }

    @Test
    public void testFromFallsBackToUnknownForUnmatchedValue() {
        assertSame(ExpireTimeEnum.EXPIRE_UNKNOWN, ExpireTimeEnum.from(DurationTimeEnum.TIME_VALUE, 12345L));
    }

    @Test
    public void testFromFallsBackToUnknownForNullDuration() {
        assertSame(ExpireTimeEnum.EXPIRE_UNKNOWN, ExpireTimeEnum.from(null, 0L));
    }
}
