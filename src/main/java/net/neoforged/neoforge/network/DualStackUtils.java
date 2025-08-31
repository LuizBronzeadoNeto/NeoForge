/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.network;

import com.google.common.net.InetAddresses;
import com.mojang.logging.LogUtils;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import net.minecraft.util.HttpUtil;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class DualStackUtils {
    private static final String INITIAL_PREFER_IPv4_STACK = System.getProperty("java.net.preferIPv4Stack") == null ? "false" : System.getProperty("java.net.preferIPv4Stack");
    private static final String INITIAL_PREFER_IPv6_ADDRESSES = System.getProperty("java.net.preferIPv6Addresses") == null ? "false" : System.getProperty("java.net.preferIPv6Addresses");

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called by {@link NeoForge} to load this class so that the initial network
     * property constants are set before any of the other methods in this class are called. This is so we can
     * distinguish what Java's read once on JVM start vs what we've set for Netty.
     */
    @ApiStatus.Internal
    public static void initialise() {}

    /**
     * Checks if an address is an IPv6 one or an IPv4 one, lets Netty know accordingly and returns the result.
     *
     * @param inetAddress The address you want to check
     * @return true if IPv6, false if IPv4
     */
    public static boolean checkIPv6(final InetAddress inetAddress) {
        final boolean shouldLogDebug = shouldLogDebug();

        if (inetAddress instanceof Inet6Address addr) {
            return handleIPv6(addr, shouldLogDebug);
        }

        if (inetAddress instanceof Inet4Address addr) {
            return handleIPv4(addr, shouldLogDebug);
        }

        return handleUnknown(inetAddress, shouldLogDebug);
    }

    /**
     * Determines whether debug logging should be enabled.
     * Debug logs are suppressed if the current thread is the "Server Pinger"
     * to avoid ambiguous output when pinging multiple servers.
     *
     * @return {@code true} if debug logging is allowed, {@code false} otherwise
     */
    private static boolean shouldLogDebug() {
        return !Thread.currentThread().getName().contains("Server Pinger #");
    }

    /**
     * Handles the case where the given address is IPv6.
     * Logs detection if enabled and sets system properties accordingly.
     *
     * @param addr           the IPv6 address
     * @param shouldLogDebug whether debug logs should be emitted
     * @return always {@code true}, indicating IPv6
     */
    private static boolean handleIPv6(Inet6Address addr, boolean shouldLogDebug) {
        if (shouldLogDebug) {
            LOGGER.debug("Detected IPv6 address: \"{}\"", addr.getHostAddress());
        }
        setSystemProperties(false, true);
        return true;
    }

    /**
     * Handles the case where the given address is IPv4.
     * Logs detection if enabled and sets system properties accordingly.
     *
     * @param addr           the IPv4 address
     * @param shouldLogDebug whether debug logs should be emitted
     * @return always {@code false}, indicating IPv4
     */
    private static boolean handleIPv4(Inet4Address addr, boolean shouldLogDebug) {
        if (shouldLogDebug) {
            LOGGER.debug("Detected IPv4 address: \"{}\"", addr.getHostAddress());
        }
        setSystemProperties(true, false);
        return false;
    }

    /**
     * Handles the case where the IP version could not be determined.
     * Falls back to initial JVM preferences or assumes IPv4 by default.
     *
     * @param inetAddress    the unknown address (may be {@code null})
     * @param shouldLogDebug whether debug logs should be emitted
     * @return {@code true} if treated as IPv6, {@code false} otherwise
     */
    private static boolean handleUnknown(InetAddress inetAddress, boolean shouldLogDebug) {
        if (shouldLogDebug) {
            final String addr = inetAddress == null ? "null" : "\"" + inetAddress.getHostAddress() + "\"";
            LOGGER.debug("Unable to determine IP version of address: {}", addr);
        }

        // Prefer IPv6 if explicitly configured
        if (isInitialIPv6Preferred()) {
            if (shouldLogDebug) {
                LOGGER.debug("Assuming IPv6 as Java was explicitly told to prefer it...");
            }
            setSystemProperties(false, true);
            return true;
        }

        if (shouldLogDebug) {
            LOGGER.debug("Assuming IPv4...");
        }
        setSystemProperties(true, false);
        return false;
    }

    /**
     * Sets the JVM system properties to indicate IPv4 or IPv6 preference.
     *
     * @param preferIPv4 whether IPv4 should be preferred
     * @param preferIPv6 whether IPv6 should be preferred
     */
    private static void setSystemProperties(boolean preferIPv4, boolean preferIPv6) {
        System.setProperty("java.net.preferIPv4Stack", Boolean.toString(preferIPv4));
        System.setProperty("java.net.preferIPv6Addresses", Boolean.toString(preferIPv6));
    }

    /**
     * Checks whether the JVM was initially started with a preference for IPv6.
     *
     * @return {@code true} if IPv6 was explicitly preferred at JVM startup, {@code false} otherwise
     */
    private static boolean isInitialIPv6Preferred() {
        return INITIAL_PREFER_IPv4_STACK.equalsIgnoreCase("false")
                && INITIAL_PREFER_IPv6_ADDRESSES.equalsIgnoreCase("true");
    }

    /**
     * Get the device's local IP address, taking into account scenarios where the client's network adapter
     * supports IPv6 and has it enabled but the router's LAN does not.
     *
     * @return the client's local IP address or {@code null} if unable to determine it
     */
    @Nullable
    public static InetAddress getLocalAddress() {
        final InetAddress localAddr = new InetSocketAddress(HttpUtil.getAvailablePort()).getAddress();
        if (localAddr.isAnyLocalAddress()) return localAddr;

        try {
            return InetAddress.getByName("localhost");
        } catch (final UnknownHostException e) {
            return null;
        }
    }

    /**
     * Used for the "Open to LAN" feature.
     *
     * @return The multicast group to use for LAN discovery - IPv6 if available, IPv4 otherwise.
     */
    public static String getMulticastGroup() {
        if (checkIPv6(getLocalAddress())) return "FF75:230::60";
        else return "224.0.2.60";
    }

    /**
     * Logs the initial values of the {@code java.net.preferIPv4Stack} and {@code java.net.preferIPv6Addresses} system
     * properties that Java has read on JVM start. Useful for debugging hostname lookup failures.
     */
    public static void logInitialPreferences() {
        LOGGER.debug("Initial IPv4 stack preference: " + INITIAL_PREFER_IPv4_STACK);
        LOGGER.debug("Initial IPv6 addresses preference: " + INITIAL_PREFER_IPv6_ADDRESSES);
    }

    /**
     * {@link SocketAddress#toString()} but with IPv6 address compression support
     */
    public static String getAddressString(final SocketAddress address) {
        if (address instanceof final InetSocketAddress inetAddress) {
            String formatted;
            if (inetAddress.isUnresolved()) {
                formatted = inetAddress.getHostName() + "/<unresolved>";
            } else {
                formatted = InetAddresses.toAddrString(inetAddress.getAddress());
                if (inetAddress.getAddress() instanceof Inet6Address)
                    formatted = '[' + formatted + ']';

                formatted = '/' + formatted;
            }

            return formatted + ':' + inetAddress.getPort();
        }

        return address.toString();
    }
}
