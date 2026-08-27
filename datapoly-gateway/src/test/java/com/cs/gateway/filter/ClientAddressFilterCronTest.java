// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.gateway.filter;

import org.junit.Test;
import org.springframework.scheduling.support.CronSequenceGenerator;

import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * Firewall rule refresh cron semantics (A6 fix): the default expression must fire every 30 seconds.
 * The old value {@code 0/30 0 * * * ?} actually fired only at second 0/30 of minute 0 each hour (twice an hour),
 * leaving rules up to ~30 minutes stale — this test guards against that regression.
 */
public class ClientAddressFilterCronTest {

    @Test
    public void defaultFirewallCronFiresEvery30Seconds() {
        CronSequenceGenerator generator =
                new CronSequenceGenerator(ClientAddressFilter.DEFAULT_FIREWALL_CRON);
        Date start = new Date(0L);
        Date previous = generator.next(start);
        for (int i = 0; i < 6; i++) {
            Date next = generator.next(previous);
            assertEquals(30_000L, next.getTime() - previous.getTime());
            previous = next;
        }
    }

}
