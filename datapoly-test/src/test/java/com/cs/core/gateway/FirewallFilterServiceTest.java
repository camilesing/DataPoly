// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.gateway;

import com.cs.common.enums.*;
import com.cs.persistence.entity.FirewallRulesEntity;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Firewall rule snapshot evaluation and unloaded backoff (A6): white/blacklists, address parsing, OFF passthrough,
 * fail-closed throwing when not loaded, and backoff-window semantics.
 */
public class FirewallFilterServiceTest {

    @Test
    public void whiteListAllowsListedOnlyAndParsesAddresses() {
        FirewallFilterService service = new FirewallFilterService();
        service.loaded = new FirewallFilterService.LoadedFirewallRules(
                rules(OnOffEnum.ON, WhiteBlackEnum.WHITE, "1.2.3.4\n 5.6.7.8 \n\n"));
        assertTrue(service.isLoaded());
        assertTrue(service.canAccess("1.2.3.4"));
        assertTrue(service.canAccess("5.6.7.8"));
        assertFalse(service.canAccess("9.9.9.9"));
    }

    @Test
    public void blackListDeniesListedOnly() {
        FirewallFilterService service = new FirewallFilterService();
        service.loaded = new FirewallFilterService.LoadedFirewallRules(
                rules(OnOffEnum.ON, WhiteBlackEnum.BLACK, "1.2.3.4"));
        assertFalse(service.canAccess("1.2.3.4"));
        assertTrue(service.canAccess("5.6.7.8"));
    }

    @Test
    public void offAllowsEverything() {
        FirewallFilterService service = new FirewallFilterService();
        service.loaded = new FirewallFilterService.LoadedFirewallRules(
                rules(OnOffEnum.OFF, WhiteBlackEnum.WHITE, ""));
        assertTrue(service.canAccess("1.2.3.4"));
        assertTrue(service.canAccess("9.9.9.9"));
    }

    @Test
    public void whiteListWithBlankAddressesDeniesAll() {
        FirewallFilterService service = new FirewallFilterService();
        service.loaded = new FirewallFilterService.LoadedFirewallRules(
                rules(OnOffEnum.ON, WhiteBlackEnum.WHITE, null));
        assertFalse(service.canAccess("1.2.3.4"));
    }

    @Test
    public void getFirewallRulesReflectsLoadedSnapshot() {
        FirewallRulesEntity entity = rules(OnOffEnum.ON, WhiteBlackEnum.WHITE, "1.2.3.4");
        FirewallFilterService service = new FirewallFilterService();
        assertNull(service.getFirewallRules());
        service.loaded = new FirewallFilterService.LoadedFirewallRules(entity);
        assertSame(entity, service.getFirewallRules());
    }

    @Test
    public void unloadedLazyLoadSucceedsOnFirstAttempt() {
        FirewallRulesEntity entity = rules(OnOffEnum.ON, WhiteBlackEnum.BLACK, "1.2.3.4");
        FirewallFilterService service = new FirewallFilterService() {
            @Override
            public void refresh() {
                this.loaded = new LoadedFirewallRules(entity);
            }
        };
        assertFalse(service.isLoaded());
        // Cold path triggers lazy loading; after success, evaluation follows the rules
        assertFalse(service.canAccess("1.2.3.4"));
        assertTrue(service.canAccess("5.6.7.8"));
    }

    @Test
    public void unloadedWithinBackoffFailsFast() {
        FirewallFilterService service = new FirewallFilterService() {
            @Override
            public void refresh() {
                throw new IllegalStateException("db down");
            }
        };
        service.retryBackoffMillis = 5000L;
        // First attempt: the underlying exception is thrown as-is and the failure time is recorded
        try {
            service.canAccess("1.2.3.4");
            fail("should throw on first lazy load failure");
        } catch (IllegalStateException e) {
            // Expected: refresh's underlying exception
        }
        // Within the backoff window: no DB access, fail fast
        try {
            service.canAccess("1.2.3.4");
            fail("should fail fast within backoff window");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("not loaded"));
        }
    }

    @Test
    public void backoffWindowExpiresAndRetries() {
        FirewallFilterService service = new FirewallFilterService() {
            @Override
            public void refresh() {
                throw new IllegalStateException("db down");
            }
        };
        service.retryBackoffMillis = 5000L;
        try {
            service.canAccess("1.2.3.4");
            fail("should throw");
        } catch (IllegalStateException e) {
            // First failure
        }
        // Rewind the failure time so the backoff window expires; the next call retries (throwing the underlying exception again instead of fail-fast)
        service.lastLoadFailedAtMillis = System.currentTimeMillis() - 6000L;
        try {
            service.canAccess("1.2.3.4");
            fail("should throw again after window expiry");
        } catch (IllegalStateException e) {
            assertTrue(String.valueOf(e.getMessage()).contains("db down"));
        }
    }

    private FirewallRulesEntity rules(OnOffEnum status, WhiteBlackEnum mode, String addresses) {
        return FirewallRulesEntity.builder()
                .id(1L)
                .status(status)
                .mode(mode)
                .addresses(addresses)
                .build();
    }

}
