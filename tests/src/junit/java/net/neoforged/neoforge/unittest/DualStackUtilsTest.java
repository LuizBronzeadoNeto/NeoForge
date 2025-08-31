/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.junit.jupiter.api.Assertions.*;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import net.neoforged.neoforge.network.DualStackUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class DualStackUtilsTest {

    private String prevPreferIPv4Stack;
    private String prevPreferIPv6Addresses;

    @BeforeEach
    void saveSystemProperties() {
        prevPreferIPv4Stack = System.getProperty("java.net.preferIPv4Stack");
        prevPreferIPv6Addresses = System.getProperty("java.net.preferIPv6Addresses");
        // Ensure class is initialized (captures initial constants) before tests mutate properties
        DualStackUtils.initialise();
    }

    @AfterEach
    void restoreSystemProperties() {
        if (prevPreferIPv4Stack == null) System.clearProperty("java.net.preferIPv4Stack");
        else System.setProperty("java.net.preferIPv4Stack", prevPreferIPv4Stack);

        if (prevPreferIPv6Addresses == null) System.clearProperty("java.net.preferIPv6Addresses");
        else System.setProperty("java.net.preferIPv6Addresses", prevPreferIPv6Addresses);
    }

    @Test
    void checkIPv6_ipv4Address_setsPropertiesAndReturnsFalse() throws Exception {
        InetAddress ipv4 = InetAddress.getByName("127.0.0.1");
        assertInstanceOf(Inet4Address.class, ipv4);

        boolean result = DualStackUtils.checkIPv6(ipv4);
        assertFalse(result, "IPv4 address should report not IPv6");
        assertEquals("true", System.getProperty("java.net.preferIPv4Stack"));
        assertEquals("false", System.getProperty("java.net.preferIPv6Addresses"));
    }

    @Test
    void checkIPv6_ipv6Address_setsPropertiesAndReturnsTrue() throws Exception {
        InetAddress ipv6 = InetAddress.getByName("::1");
        assertInstanceOf(Inet6Address.class, ipv6);

        boolean result = DualStackUtils.checkIPv6(ipv6);
        assertTrue(result, "IPv6 address should report IPv6");
        assertEquals("false", System.getProperty("java.net.preferIPv4Stack"));
        assertEquals("true", System.getProperty("java.net.preferIPv6Addresses"));
    }

    @Test
    void checkIPv6_null_usesFallbackAndSetsPropertiesConsistently() {
        boolean result = DualStackUtils.checkIPv6(null);
        if (result) {
            // IPv6 assumed
            assertEquals("false", System.getProperty("java.net.preferIPv4Stack"));
            assertEquals("true", System.getProperty("java.net.preferIPv6Addresses"));
        } else {
            // IPv4 assumed
            assertEquals("true", System.getProperty("java.net.preferIPv4Stack"));
            assertEquals("false", System.getProperty("java.net.preferIPv6Addresses"));
        }
    }

    @Test
    void getAddressString_formatsIPv4Correctly() throws UnknownHostException {
        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345);
        String s = DualStackUtils.getAddressString(addr);
        assertEquals("/127.0.0.1:12345", s);
    }

    @Test
    void getAddressString_formatsIPv6WithBrackets() throws UnknownHostException {
        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("::1"), 25565);
        String s = DualStackUtils.getAddressString(addr);
        assertEquals("/[::1]:25565", s);
    }

    @Test
    void getAddressString_unresolvedIncludesMarker() {
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("example.invalid", 9999);
        String s = DualStackUtils.getAddressString(unresolved);
        assertEquals("example.invalid/<unresolved>:9999", s);
    }

    @Test
    void getLocalAddress_returnsNonNull() {
        InetAddress local = DualStackUtils.getLocalAddress();
        assertNotNull(local, "Local address should not be null");
    }

    @Test
    void getMulticastGroup_returnsKnownValueAndUpdatesPropertiesConsistently() {
        String group = DualStackUtils.getMulticastGroup();
        assertTrue(
            group.equals("FF75:230::60") || group.equals("224.0.2.60"),
            "Multicast group must be IPv6 or IPv4 predefined value"
        );

        if (group.equals("FF75:230::60")) {
            assertEquals("true", System.getProperty("java.net.preferIPv6Addresses"));
            assertEquals("false", System.getProperty("java.net.preferIPv4Stack"));
        } else {
            assertEquals("true", System.getProperty("java.net.preferIPv4Stack"));
            assertEquals("false", System.getProperty("java.net.preferIPv6Addresses"));
        }
    }
}
