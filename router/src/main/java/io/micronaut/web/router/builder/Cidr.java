/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

/**
 * A range of IPv4 or IPv6 addresses in CIDR notation, e.g. {@code 10.0.0.0/8} or
 * {@code fd00::/8}, for {@link RouteCondition#remoteAddress(String...)}. A range is written with
 * addresses, never host names: no name is ever looked up. An IPv4 address mapped to IPv6, e.g.
 * {@code ::ffff:10.0.0.1}, is an IPv4 address.
 *
 * @param network The bytes of the network address, the bits after the prefix cleared: 4 of them
 *                for IPv4, 16 for IPv6
 * @param prefix  The number of leading bits that the addresses of the range share
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record Cidr(byte[] network, int prefix) {

    /**
     * @param network The bytes of the network address, copied, the bits after the prefix cleared
     * @param prefix  The prefix length
     */
    public Cidr {
        Objects.requireNonNull(network, "network");
        if (network.length != 4 && network.length != 16) {
            throw new IllegalArgumentException("A network address has 4 or 16 bytes");
        }
        if (prefix < 0 || prefix > network.length * 8) {
            throw new IllegalArgumentException("The prefix length " + prefix + " is out of range");
        }
        network = network.clone();
        for (int i = 0; i < network.length; i++) {
            network[i] &= mask(prefix, i);
        }
    }

    /**
     * Parse a range: an address, the range of that address alone, or an address and a prefix
     * length, e.g. {@code 192.168.1.7}, {@code 10.0.0.0/8}, {@code ::1} or {@code 2001:db8::/32}.
     *
     * @param cidr The range
     * @return The range
     * @throws IllegalArgumentException if it is not a range of addresses
     */
    public static Cidr parse(String cidr) {
        Objects.requireNonNull(cidr, "cidr");
        String range = cidr.strip();
        int slash = range.indexOf('/');
        String literal = slash < 0 ? range : range.substring(0, slash);
        byte[] network = addressLiteral(literal);
        if (network == null) {
            throw new IllegalArgumentException("Not an IPv4 or IPv6 address range in CIDR notation: " + cidr);
        }
        int prefix = network.length * 8;
        if (slash >= 0) {
            try {
                prefix = Integer.parseInt(range.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not an IPv4 or IPv6 address range in CIDR notation: " + cidr, e);
            }
            if (network.length == 4 && literal.indexOf(':') >= 0) {
                // an IPv4 address mapped to IPv6: the prefix length counts the 96 bits of the mapping
                prefix -= 96;
            }
            if (prefix < 0 || prefix > network.length * 8) {
                throw new IllegalArgumentException("The prefix length of the address range " + cidr + " is out of range");
            }
        }
        return new Cidr(network, prefix);
    }

    /**
     * @return A copy of the bytes of the network address
     */
    @Override
    public byte[] network() {
        return network.clone();
    }

    /**
     * @param address The bytes of an address, see {@link #address(String)}
     * @return Whether the address is in the range
     */
    public boolean contains(byte[] address) {
        if (address.length != network.length) {
            return false;
        }
        for (int i = 0; i < address.length; i++) {
            if ((byte) (address[i] & mask(prefix, i)) != network[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse a client address, e.g. of the peer of a connection or of a header. A name is never
     * looked up.
     *
     * @param value An address, with or without a port, an IPv6 address in brackets or not, e.g.
     *              {@code 192.0.2.1:4711} or {@code [2001:db8::1]:4711}
     * @return Its bytes, 4 of them for an IPv4 address or an IPv4 address mapped to IPv6, or
     * {@code null} if it is not an address, e.g. {@code unknown}
     */
    public static byte @Nullable [] address(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String address = value.strip();
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end < 0) {
                return null;
            }
            address = address.substring(1, end);
        } else {
            int port = address.indexOf(':');
            if (port >= 0 && port == address.lastIndexOf(':')) {
                // an IPv4 address and a port: an IPv6 address has more than one colon
                address = address.substring(0, port);
            }
        }
        return addressLiteral(address);
    }

    /**
     * @param address An address, e.g. of the peer of a connection
     * @return Its bytes, 4 of them for an IPv4 address or an IPv4 address mapped to IPv6
     */
    public static byte[] address(InetAddress address) {
        return normalize(address.getAddress());
    }

    /**
     * Parse an address literal. A name is never looked up.
     *
     * @param literal An IPv4 or an IPv6 address, an IPv6 address in brackets or not
     * @return Its bytes, or {@code null} if it is not an address
     */
    private static byte @Nullable [] addressLiteral(@Nullable String literal) {
        if (literal == null || literal.isEmpty()) {
            return null;
        }
        String address = literal;
        if (address.startsWith("[") && address.endsWith("]")) {
            address = address.substring(1, address.length() - 1);
        }
        if (address.indexOf(':') < 0) {
            return ipv4(address);
        }
        int zone = address.indexOf('%');
        if (zone >= 0) {
            // the zone id of a scoped address, e.g. fe80::1%en0, names an interface: not part of the address
            if (zone == address.length() - 1) {
                return null;
            }
            address = address.substring(0, zone);
        }
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (Character.digit(c, 16) < 0 && c != ':' && c != '.') {
                return null;
            }
        }
        try {
            // a literal with a colon is parsed as an IPv6 address, it is not looked up
            return normalize(InetAddress.getByName(address).getAddress());
        } catch (UnknownHostException | IllegalArgumentException e) {
            return null;
        }
    }

    private static byte @Nullable [] ipv4(String address) {
        byte[] bytes = new byte[4];
        int part = 0;
        int value = -1;
        for (int i = 0; i <= address.length(); i++) {
            char c = i == address.length() ? '.' : address.charAt(i);
            if (c == '.') {
                if (value < 0 || part == 4) {
                    return null;
                }
                bytes[part++] = (byte) value;
                value = -1;
            } else if (c >= '0' && c <= '9') {
                value = value < 0 ? c - '0' : value * 10 + (c - '0');
                if (value > 255) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return part == 4 ? bytes : null;
    }

    /**
     * @param address The bytes of an address
     * @return The IPv4 address of an IPv4 address mapped to IPv6, otherwise the address
     */
    private static byte[] normalize(byte[] address) {
        if (address.length != 16) {
            return address;
        }
        for (int i = 0; i < 10; i++) {
            if (address[i] != 0) {
                return address;
            }
        }
        if (address[10] == (byte) 0xff && address[11] == (byte) 0xff) {
            return Arrays.copyOfRange(address, 12, 16);
        }
        return address;
    }

    /**
     * @param prefix The prefix length
     * @param index  The index of a byte of an address
     * @return The mask of the byte
     */
    private static byte mask(int prefix, int index) {
        int bits = prefix - index * 8;
        if (bits >= 8) {
            return (byte) 0xff;
        }
        if (bits <= 0) {
            return 0;
        }
        return (byte) (0xff << (8 - bits));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Cidr that && prefix == that.prefix && Arrays.equals(network, that.network);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(network) + prefix;
    }

    @Override
    public String toString() {
        try {
            return InetAddress.getByAddress(network).getHostAddress() + "/" + prefix;
        } catch (UnknownHostException e) {
            return Arrays.toString(network) + "/" + prefix;
        }
    }
}
