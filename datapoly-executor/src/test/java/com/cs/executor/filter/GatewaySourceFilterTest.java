// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.filter;

import org.junit.*;

/**
 * Tests for the address matching logic of the gateway source check (S2).
 */
public class GatewaySourceFilterTest {

    @Test
    public void exactMatch() {
        Assert.assertTrue(GatewaySourceFilter.matches("172.28.0.40", "172.28.0.40"));
        Assert.assertTrue(GatewaySourceFilter.matches("::1", "::1"));
        Assert.assertFalse(GatewaySourceFilter.matches("172.28.0.40", "172.28.0.41"));
    }

    @Test
    public void loopbackAliases() {
        Assert.assertTrue(GatewaySourceFilter.matches("127.0.0.1", "0:0:0:0:0:0:0:1"));
        Assert.assertTrue(GatewaySourceFilter.matches("::1", "127.0.0.1"));
    }

    @Test
    public void cidrMatch() {
        Assert.assertTrue(GatewaySourceFilter.matches("172.28.0.0/24", "172.28.0.40"));
        Assert.assertFalse(GatewaySourceFilter.matches("172.28.0.0/24", "172.28.1.40"));
        Assert.assertTrue(GatewaySourceFilter.matches("10.0.0.0/8", "10.99.8.7"));
        Assert.assertTrue(GatewaySourceFilter.matches("192.168.1.5/32", "192.168.1.5"));
        Assert.assertFalse(GatewaySourceFilter.matches("192.168.1.5/32", "192.168.1.6"));
        Assert.assertTrue(GatewaySourceFilter.matches("0.0.0.0/0", "8.8.8.8"));
    }

    @Test
    public void invalidInputsNeverMatch() {
        Assert.assertFalse(GatewaySourceFilter.matches("", "1.2.3.4"));
        Assert.assertFalse(GatewaySourceFilter.matches("not-a-cidr/xx", "1.2.3.4"));
        Assert.assertFalse(GatewaySourceFilter.matches("172.28.0.0/24", "not-an-ip"));
        Assert.assertFalse(GatewaySourceFilter.matches("172.28.0.0/24", "::1"));
        Assert.assertFalse(GatewaySourceFilter.matches("172.28.0.0/33", "172.28.0.1"));
    }

}
