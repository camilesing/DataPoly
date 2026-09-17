// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import org.junit.Test;

import java.net.InetAddress;

import static org.junit.Assert.*;

public class InetUtilsTest {

    @Test
    public void testLocalAddressResolvedAndCached() {
        InetAddress first = InetUtils.getLocalAddress();
        assertNotNull("a local address must resolve on build hosts", first);
        assertSame("second lookup must return the cached instance", first, InetUtils.getLocalAddress());
    }

    @Test
    public void testLocalIpStrMatchesCachedAddress() {
        InetAddress address = InetUtils.getLocalAddress();
        assertEquals(address.getHostAddress(), InetUtils.getLocalIpStr());
    }
}
