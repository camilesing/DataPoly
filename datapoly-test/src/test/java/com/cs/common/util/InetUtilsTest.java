// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Must run the hosts-file probe before anything else in this class touches InetAddress:
 * the JDK reads "jdk.net.hosts.file" once while loading java.net.InetAddress, and the
 * derived resolver stays in place for the whole JVM. NAME_ASCENDING keeps the probe
 * (test00...) ahead of the ordinary lookup tests.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
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

    @Test
    public void testLocalAddressRecomputedAfterCacheReset() throws Exception {
        InetAddress first = InetUtils.getLocalAddress();
        assertNotNull(first);

        // clearing the cache forces the full interface walk again and re-populates it
        resetLocalAddressCache();
        try {
            InetAddress second = InetUtils.getLocalAddress();
            assertNotNull(second);
            assertNotNull(InetUtils.getLocalAddress());
        } finally {
            resetLocalAddressCache();
        }
    }

    @Test
    public void testValidV4AddressAccepted() throws Exception {
        // 192.0.2.0/24 is documentation range: never loopback, never 0.0.0.0
        Optional<InetAddress> valid = toValidAddress(InetAddress.getByName("192.0.2.10"));
        assertTrue(valid.isPresent());
        assertEquals("192.0.2.10", valid.get().getHostAddress());
    }

    @Test
    public void testLoopbackAndAnyAddressesRejected() throws Exception {
        assertFalse(toValidAddress(InetAddress.getByName("127.0.0.1")).isPresent());
        assertFalse(toValidAddress(InetAddress.getByName("0.0.0.0")).isPresent());
    }

    @Test
    public void testV6AddressRejectedWhenIpv4Preferred() throws Exception {
        // default JVM setup prefers IPv4: a v6 address is not a valid pick
        assertFalse("::1".equals(System.getProperty("java.net.preferIPv6Addresses")));
        Optional<InetAddress> v6 = toValidAddress(InetAddress.getByName("2001:db8::1"));
        assertFalse(v6.isPresent());
    }

    @Test
    public void testV6AddressAcceptedWhenIpv6Preferred() throws Exception {
        System.setProperty("java.net.preferIPv6Addresses", "true");
        try {
            Optional<InetAddress> v6 = toValidAddress(InetAddress.getByName("2001:db8::1"));
            assertTrue(v6.isPresent());
        } finally {
            System.clearProperty("java.net.preferIPv6Addresses");
        }
    }

    @Test
    public void testNullAddressIsInvalidV4() throws Exception {
        Method method = InetUtils.class.getDeclaredMethod("isValidV4Address", InetAddress.class);
        method.setAccessible(true);
        assertFalse((Boolean) method.invoke(null, (Object) null));
    }

    @Test
    public void testV6LoopbackIsInvalidV4Shape() throws Exception {
        Method method = InetUtils.class.getDeclaredMethod("isValidV4Address", InetAddress.class);
        method.setAccessible(true);
        // "::1" has a colon-separated textual form: never matches the dotted IPv4 pattern
        assertFalse((Boolean) method.invoke(null, InetAddress.getByName("::1")));
    }

    @Test
    public void testNormalizeV6AddressKeepsScope() throws Exception {
        byte[] addr = new byte[16];
        addr[0] = (byte) 0xfe;
        addr[1] = (byte) 0x80;
        addr[15] = 1;
        Inet6Address scoped = Inet6Address.getByAddress(null, addr, 5);
        assertTrue(scoped.getHostAddress().contains("%5"));

        InetAddress normalized = normalizeV6(scoped);
        assertNotNull(normalized);
        assertTrue(normalized.getHostAddress(), normalized.getHostAddress().contains("%5"));
    }

    @Test
    public void testNormalizeV6AddressWithoutScopeReturnsSameInstance() throws Exception {
        Inet6Address plain = (Inet6Address) InetAddress.getByName("2001:db8::1");
        assertSame(plain, normalizeV6(plain));
    }

    /**
     * Drives {@code getLocalAddress0} through its interface-enumeration fallback by
     * pointing the JDK hosts-file resolver at a file that cannot resolve the local
     * host name. Exercises three shapes: unresolved host name (catch + walk),
     * host name mapped to 127.0.0.1 (empty Optional at the loopback guard + walk),
     * and an IPv6-preferred walk where link-local candidates fail isReachable first.
     */
    @Test
    public void test00LocalHostNameFailureFallsBackToInterfaceWalk() throws Exception {
        Assume.assumeTrue("jdk.net.hosts.file already configured for this JVM",
                System.getProperty("jdk.net.hosts.file") == null);

        Path hostsFile = Files.createTempFile("datapoly-inetutils-hosts", ".txt");
        // base content: localhost plus a probe name that only this file can answer
        writeHostsFile(hostsFile, "127.0.0.1 localhost\n"
                + "127.0.0.1 datapoly-poison-probe.invalid\n");

        String previousTtl = System.setProperty("networkaddress.cache.ttl", "0");
        String previousNegativeTtl = System.setProperty("networkaddress.cache.negative.ttl", "0");
        System.setProperty("jdk.net.hosts.file", hostsFile.toString());
        try {
            // if InetAddress was class-loaded before us, the resolver is already fixed
            // to platform DNS and the probe stays unresolvable: skip silently then
            boolean resolverInstalled;
            try {
                resolverInstalled = InetAddress.getByName("datapoly-poison-probe.invalid")
                        .getHostAddress().equals("127.0.0.1");
            } catch (UnknownHostException notInstalled) {
                resolverInstalled = false;
            }
            Assume.assumeTrue("hosts-file resolver not installable in this JVM order",
                    resolverInstalled);

            // 1) local host name unresolvable: getLocalHost throws, walk interfaces
            String localHostName = null;
            try {
                InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                localHostName = parseHostNameFromResolverMessage(e.getMessage());
            }
            resetLocalAddressCache();
            InetAddress walked = InetUtils.getLocalAddress();
            assertFallbackAddressOwnedByActiveInterface(walked, false);

            // 2) local host name resolves to the loopback: valid-address Optional is
            //    empty, so the early return is skipped and the walk runs as well
            if (localHostName != null) {
                writeHostsFile(hostsFile, "127.0.0.1 localhost\n"
                        + "127.0.0.1 datapoly-poison-probe.invalid\n"
                        + "127.0.0.1 " + localHostName + "\n");
                assertEquals("loopback mapping must take effect immediately",
                        "127.0.0.1", InetAddress.getLocalHost().getHostAddress());
                resetLocalAddressCache();
                InetAddress loopbackCase = InetUtils.getLocalAddress();
                assertFallbackAddressOwnedByActiveInterface(loopbackCase, false);
                writeHostsFile(hostsFile, "127.0.0.1 localhost\n"
                        + "127.0.0.1 datapoly-poison-probe.invalid\n");
            }

            // 3) IPv6 preferred: unreachable scoped link-local addresses on tunnel
            //    interfaces exercise the not-reachable branch before a reachable pick
            System.setProperty("java.net.preferIPv6Addresses", "true");
            try {
                resetLocalAddressCache();
                InetAddress v6Pick = InetUtils.getLocalAddress();
                assertFallbackAddressOwnedByActiveInterface(v6Pick, true);
            } finally {
                System.clearProperty("java.net.preferIPv6Addresses");
            }
        } finally {
            // the resolver keeps pointing at this file for the rest of the JVM:
            // leave the safe localhost mapping behind, never a missing file
            writeHostsFile(hostsFile, "127.0.0.1 localhost\n::1 localhost\n");
            System.clearProperty("jdk.net.hosts.file");
            if (previousTtl == null) {
                System.clearProperty("networkaddress.cache.ttl");
            } else {
                System.setProperty("networkaddress.cache.ttl", previousTtl);
            }
            if (previousNegativeTtl == null) {
                System.clearProperty("networkaddress.cache.negative.ttl");
            } else {
                System.setProperty("networkaddress.cache.negative.ttl", previousNegativeTtl);
            }
            // re-populate the cache through the normal path for the remaining tests
            resetLocalAddressCache();
            try {
                InetUtils.getLocalAddress();
            } catch (Throwable ignored) {
                // exotic hosts without any usable address: nothing to cache
            }
        }
    }

    private static void writeHostsFile(Path hostsFile, String content) throws Exception {
        Files.write(hostsFile, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String parseHostNameFromResolverMessage(String message) {
        if (message == null) {
            return null;
        }
        int begin = message.indexOf("resolve host ");
        int end = message.indexOf(" in hosts file");
        if (begin < 0 || end < 0 || end <= begin + "resolve host ".length()) {
            return null;
        }
        return message.substring(begin + "resolve host ".length(), end);
    }

    /**
     * The fallback result, when present, must be an address really attached to an
     * up, non-loopback, non-virtual interface; on hosts owning such an address it
     * must also be non-null (something reachable is always picked).
     */
    private static void assertFallbackAddressOwnedByActiveInterface(
            InetAddress picked, boolean v6Preferred) throws Exception {
        // compare raw bytes: scoped v6 textual forms differ in zone notation between
        // interface-derived addresses and the normalized pick, bytes do not
        Set<String> candidates = new HashSet<>();
        boolean anyReachable = false;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            if (network.isLoopback() || network.isVirtual() || !network.isUp()) {
                continue;
            }
            Enumeration<InetAddress> addresses = network.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                boolean v6 = address instanceof Inet6Address;
                if (v6 && !v6Preferred) {
                    // IPv6 is only a candidate when preferred; IPv4 always is
                    continue;
                }
                String textual = address.getHostAddress();
                if (textual == null || "0.0.0.0".equals(textual) || "127.0.0.1".equals(textual)) {
                    continue;
                }
                candidates.add(toKey(address));
                if (address.isReachable(100)) {
                    anyReachable = true;
                }
            }
        }
        if (anyReachable) {
            assertNotNull("a reachable interface address must be picked", picked);
        }
        if (picked != null) {
            assertTrue("picked address " + picked.getHostAddress()
                            + " must belong to an active interface",
                    candidates.contains(toKey(picked)));
        }
    }

    private static String toKey(InetAddress address) {
        StringBuilder key = new StringBuilder();
        for (byte b : address.getAddress()) {
            key.append(String.format("%02x", b));
        }
        return key.toString();
    }

    private static Optional<InetAddress> toValidAddress(InetAddress address) throws Exception {
        Method method = InetUtils.class.getDeclaredMethod("toValidAddress", InetAddress.class);
        method.setAccessible(true);
        return (Optional<InetAddress>) method.invoke(null, address);
    }

    private static InetAddress normalizeV6(Inet6Address address) throws Exception {
        Method method = InetUtils.class.getDeclaredMethod("normalizeV6Address", Inet6Address.class);
        method.setAccessible(true);
        return (InetAddress) method.invoke(null, address);
    }

    private static void resetLocalAddressCache() throws Exception {
        Field field = InetUtils.class.getDeclaredField("LOCAL_ADDRESS");
        field.setAccessible(true);
        field.set(null, null);
    }
}
