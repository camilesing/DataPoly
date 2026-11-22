// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import com.cs.common.consts.Constants;
import org.junit.Test;

import static org.junit.Assert.*;

public class AliveTimeEnumTest {

    @Test
    public void testTokenLifetimeValues() {
        // short-lived tokens expire after the client token duration, longevity never does
        assertEquals("短期", AliveTimeEnum.PERIOD.getName());
        assertEquals(Constants.CLIENT_TOKEN_DURATION_SECONDS.longValue(), AliveTimeEnum.PERIOD.getValue());
        assertEquals("长期", AliveTimeEnum.LONGEVITY.getName());
        assertEquals(Constants.CLIENT_TOKEN_LONGEVITY.longValue(), AliveTimeEnum.LONGEVITY.getValue());
        assertEquals(-1L, AliveTimeEnum.LONGEVITY.getValue());
    }
}
