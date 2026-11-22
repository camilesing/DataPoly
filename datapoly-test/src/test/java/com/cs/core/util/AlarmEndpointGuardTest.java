// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.exception.CommonException;
import org.junit.Test;

import java.lang.reflect.Method;

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

    @Test
    public void unparsableUriIsRejectedWithItsOwnMessage() {
        try {
            AlarmEndpointGuard.checkEndpointAllowed("http://exa mple.com/hook");
            fail("unparsable URI must be rejected");
        } catch (CommonException e) {
            assertTrue(e.getMessage().contains("must be a parsable URI"));
        }
    }

    @Test
    public void numericShorthandAddressesAreJudgedByTheResolvedAddress() {
        // partial-quad shorthand resolves to Inet4Address inside the guard (no URI host grammar
        // involved when calling isHostAllowed directly) and is judged by its policy class:
        // 10.1 -> 10.0.0.1 site-local, 169.254.1 -> link-local, 224.1 -> multicast, 0.1 -> 0.0.0.0/8
        assertFalse(AlarmEndpointGuard.isHostAllowed("10.1"));
        assertFalse(AlarmEndpointGuard.isHostAllowed("169.254.1"));
        assertFalse(AlarmEndpointGuard.isHostAllowed("224.1"));
        assertFalse(AlarmEndpointGuard.isHostAllowed("0.1"));
        // 8.4 -> 8.0.0.4 public
        assertTrue(AlarmEndpointGuard.isHostAllowed("8.4"));
    }

    @Test
    public void outOfRangeDottedQuadIsRejectedBeforePolicyChecks() {
        // matches the dotted-quad regex but an octet exceeds 255: ipv4ToLong -> -1 -> denied
        assertFalse(AlarmEndpointGuard.isHostAllowed("999.999.999.999"));
        assertFalse(AlarmEndpointGuard.isHostAllowed("8.8.8.256"));
    }

    @Test
    public void unspecifiedAndAnyLocalFormsAreRejected() {
        // [::] keeps the any-local IPv6 form (no JDK v4 conversion applies)
        assertFalse(endpointAllowed("http://[::]/hook"));
        assertFalse(endpointAllowed("http://[0:0:0:0:0:0:0:0]/hook"));
    }

    @Test
    public void pureHexWordsStayHostnamesAndAreAllowed() {
        // "beef" is hex-looking but has no digit: never treated as an address literal, and
        // hostname resolution is out of scope, so it is allowed
        assertTrue(AlarmEndpointGuard.isHostAllowed("beef"));
        assertTrue(endpointAllowed("http://beef/hook"));
    }

    @Test
    public void hexDottedLiteralsTheJdkRefusesAreDenied() {
        // "0x0.0.0.0" is not a JDK literal form (hex needs the 0x prefix on the first
        // component): getAllByName throws UnknownHostException -> denied, no DNS reliance
        assertFalse(AlarmEndpointGuard.isHostAllowed("0x0.0.0.0"));
        assertFalse(AlarmEndpointGuard.isHostAllowed("0x0.1"));
    }

    /**
     * The digit-detecting lambda inside isNumericShorthandHost must walk past hex letters
     * (>= '0' but > '9') and dots (< '0') before it finds the digit that classifies the host
     * as an alternate IPv4 notation. Pure predicate, invoked directly because every host
     * with that shape is unparsable as a literal and would go to DNS.
     */
    @Test
    public void numericShorthandHostDetectionWalksNonDigitChars() throws Exception {
        Method m = AlarmEndpointGuard.class.getDeclaredMethod("isNumericShorthandHost", String.class);
        m.setAccessible(true);
        assertTrue((Boolean) m.invoke(null, "f.1"));
        assertTrue((Boolean) m.invoke(null, "ff.1"));
        assertFalse((Boolean) m.invoke(null, "beef"));
        assertFalse((Boolean) m.invoke(null, "ff"));
    }

    /**
     * Embedded-IPv4 detection inside Inet6Address: every byte position 0..9 of the mapped
     * candidate must be able to fail the "all zero" test on its own. All these addresses
     * carry the ::ffff signature in groups 6-7 but a nonzero byte earlier, so they are NOT
     * converted by the JDK (true ::ffff:a.b.c.d arrives here as Inet4Address) and are judged
     * as ordinary IPv6: global unicast, allowed.
     */
    @Test
    public void ipv6AlmostMappedFormsAreJudgedAsPlainIpv6() {
        assertTrue(AlarmEndpointGuard.isHostAllowed("100:0:0:0:0:ffff:102:304")); // byte 0 nonzero
        assertTrue(AlarmEndpointGuard.isHostAllowed("1:0:0:0:0:ffff:102:304"));   // byte 1
        assertTrue(AlarmEndpointGuard.isHostAllowed("0:100:0:0:0:ffff:102:304")); // byte 2
        assertTrue(AlarmEndpointGuard.isHostAllowed("0:1:0:0:0:ffff:102:304"));   // byte 3
        assertTrue(AlarmEndpointGuard.isHostAllowed("0:0:100:0:0:ffff:102:304")); // byte 4
        assertTrue(AlarmEndpointGuard.isHostAllowed("0:0:1:0:0:ffff:102:304"));   // byte 5
        assertTrue(AlarmEndpointGuard.isHostAllowed("0:0:0:100:0:ffff:102:304")); // byte 6
        assertTrue(AlarmEndpointGuard.isHostAllowed("0:0:0:1:0:ffff:102:304"));   // byte 7
        assertTrue(AlarmEndpointGuard.isHostAllowed("0:0:0:0:100:ffff:102:304")); // byte 8
        assertTrue(AlarmEndpointGuard.isHostAllowed("0:0:0:0:1:ffff:102:304"));   // byte 9
        // bytes 10-11 carry ff-00 (not ff-ff): mapped test fails at the second ffff byte
        assertTrue(AlarmEndpointGuard.isHostAllowed("0:0:0:0:0:ff00:102:304"));
    }

    @Test
    public void ipv4CompatibleIpv6EmbeddingIsJudgedByEmbeddedV4() {
        // first 12 bytes zero -> Inet6Address survives the JDK conversion and is judged
        // by the embedded IPv4 policy: 1.2.3.4 public, 10.1.2.3 private
        assertTrue(AlarmEndpointGuard.isHostAllowed("0:0:0:0:0:0:102:304"));
        assertFalse(AlarmEndpointGuard.isHostAllowed("0:0:0:0:0:0:a01:203"));
    }

    @Test
    public void invokedReflectivelyIsNumericShorthandHostClassifiesCorrectly() throws Exception {
        // sanity for the reflective helper used above: dotted-digit hosts are shorthand,
        // plain hostnames are not
        Method m = AlarmEndpointGuard.class.getDeclaredMethod("isNumericShorthandHost", String.class);
        m.setAccessible(true);
        assertTrue((Boolean) m.invoke(null, "127.1"));
        assertFalse((Boolean) m.invoke(null, "example.com"));
    }

    @Test
    public void malformedIpv6LiteralsAreDenied() {
        // nine groups: the JDK refuses the literal (no DNS involved), the guard denies
        assertFalse(AlarmEndpointGuard.isHostAllowed("1:2:3:4:5:6:7:8:9"));
        assertFalse(AlarmEndpointGuard.isHostAllowed("zz::1"));
    }
}
