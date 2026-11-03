// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.exception.CommonException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Alarm webhook host allowlist (SSRF guard): loopback, RFC1918, link-local (cloud metadata
 * among them) and unspecified address literals must be rejected; public hosts pass. Hostname
 * resolution is deliberately out of scope (documented DNS rebinding residual).
 */
public class AlarmEndpointGuardTest {

    private static boolean endpointAllowed(String endpoint) {
        try {
            AlarmEndpointGuard.checkEndpointAllowed(endpoint);
            return true;
        } catch (CommonException e) {
            return false;
        }
    }

    @Test
    public void publicHostsAreAllowed() {
        assertTrue(endpointAllowed("https://hooks.example.com/path"));
        assertTrue(endpointAllowed("http://8.8.8.8/callback"));
        // 172.32.0.0 lies outside 172.16.0.0/12
        assertTrue(endpointAllowed("http://172.100.1.1/callback"));
        assertTrue(endpointAllowed("http://[2001:db8::1]:9090/hook"));
    }

    @Test
    public void loopbackIsRejected() {
        assertFalse(endpointAllowed("http://localhost/hook"));
        assertFalse(endpointAllowed("http://api.localhost/hook"));
        assertFalse(endpointAllowed("http://127.0.0.1/hook"));
        assertFalse(endpointAllowed("http://127.8.9.10/hook"));
        assertFalse(endpointAllowed("http://[::1]:8080/hook"));
        assertFalse(endpointAllowed("http://0:0:0:0:0:0:0:1/hook"));
    }

    @Test
    public void privateRangesAreRejected() {
        assertFalse(endpointAllowed("http://10.1.2.3/hook"));
        assertFalse(endpointAllowed("http://172.16.0.1/hook"));
        assertFalse(endpointAllowed("http://172.31.255.254/hook"));
        assertFalse(endpointAllowed("http://192.168.1.10/hook"));
        assertFalse(endpointAllowed("http://0.0.0.0/hook"));
    }

    @Test
    public void linkLocalAndMetadataAddressesAreRejected() {
        assertFalse(endpointAllowed("http://169.254.169.254/latest/meta-data"));
        assertFalse(endpointAllowed("http://169.254.0.9/hook"));
    }

    @Test
    public void ipv6SpecialFormsAreRejected() {
        assertFalse(endpointAllowed("http://[fd00::1]/hook"));
        assertFalse(endpointAllowed("http://[fc12::1]/hook"));
        assertFalse(endpointAllowed("http://[fe80::1]/hook"));
        // IPv4-mapped forms are judged by the embedded IPv4 address
        assertFalse(endpointAllowed("http://[::ffff:10.0.0.1]/hook"));
        assertFalse(endpointAllowed("http://[::ffff:127.0.0.1]/hook"));
        // IPv4-compatible and longhand-mapped embeddings of private targets
        assertFalse(endpointAllowed("http://[::127.0.0.1]/hook"));
        assertFalse(endpointAllowed("http://[::169.254.169.254]/hook"));
        assertFalse(endpointAllowed("http://[::a9fe:a9fe]/hook"));
        assertFalse(endpointAllowed("http://[0:0:0:0:0:ffff:a00:1]/hook"));
        assertFalse(endpointAllowed("http://[0:0:0:0:0:0:a9fe:a9fe]/hook"));
    }

    @Test
    public void alternateIpv4NotationsAreRejected() {
        assertFalse(endpointAllowed("http://127.1/hook"));
        assertFalse(endpointAllowed("http://0x7f.0.0.1/hook"));
        assertFalse(endpointAllowed("http://2130706433/hook"));
        // partial-quad numerics are not valid URI hosts at all — conservative deny
        assertFalse(endpointAllowed("http://93.1/hook"));
        // the JDK parses leading zeros as decimal, so this is 177.0.0.1 for BOTH the guard
        // and the outbound client — consistent semantics, not a bypass
        assertTrue(endpointAllowed("http://0177.0.0.1/hook"));
    }

    @Test
    public void sharedAndReservedRangesAreRejected() {
        // 100.64.0.0/10 CGN space
        assertFalse(endpointAllowed("http://100.64.1.1/hook"));
        assertFalse(endpointAllowed("http://100.127.255.254/hook"));
        assertTrue(endpointAllowed("http://100.128.1.1/hook"));
        // multicast / reserved / broadcast
        assertFalse(endpointAllowed("http://224.0.0.1/hook"));
        assertFalse(endpointAllowed("http://239.255.255.254/hook"));
        assertFalse(endpointAllowed("http://240.0.0.1/hook"));
        assertFalse(endpointAllowed("http://255.255.255.255/hook"));
    }

    @Test
    public void missingHostIsRejected() {
        assertFalse(endpointAllowed("http:///just-a-path"));
        assertFalse(endpointAllowed("notaurl"));
    }
}
