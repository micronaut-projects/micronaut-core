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
package io.micronaut.http.ssl;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Resolves and validates the TLS key exchange named groups from {@link SslConfiguration#getKeyExchangeGroups()}
 * and {@link SslConfiguration#getPqcPolicy()}.
 *
 * @since 5.3.0
 */
@Internal
public final class KeyExchangeGroups {

    /**
     * The hybrid post-quantum group combining X25519 with ML-KEM-768.
     */
    public static final String X25519_MLKEM768 = "X25519MLKEM768";

    /**
     * The only protocol that negotiates post-quantum groups.
     */
    public static final String TLS_13 = "TLSv1.3";

    /**
     * Groups enabled by {@link PqcPolicy#RELAXED} when no groups are configured.
     */
    static final List<String> RELAXED_DEFAULTS = List.of(X25519_MLKEM768, "x25519", "secp256r1", "secp384r1");

    /**
     * Groups enabled by {@link PqcPolicy#STRICT} when no groups are configured, as far as supported.
     */
    static final List<String> STRICT_DEFAULTS = List.of(X25519_MLKEM768, "SecP256r1MLKEM768", "SecP384r1MLKEM1024");

    /**
     * IANA TLS supported group code points, used to check the groups a JDK engine actually offers.
     */
    private static final Map<String, Integer> CODE_POINTS = Map.ofEntries(
        Map.entry("secp256r1", 0x0017),
        Map.entry("secp384r1", 0x0018),
        Map.entry("secp521r1", 0x0019),
        Map.entry("x25519", 0x001d),
        Map.entry("x448", 0x001e),
        Map.entry("ffdhe2048", 0x0100),
        Map.entry("ffdhe3072", 0x0101),
        Map.entry("ffdhe4096", 0x0102),
        Map.entry("ffdhe6144", 0x0103),
        Map.entry("ffdhe8192", 0x0104),
        Map.entry("mlkem512", 0x0200),
        Map.entry("mlkem768", 0x0201),
        Map.entry("mlkem1024", 0x0202),
        Map.entry("secp256r1mlkem768", 0x11eb),
        Map.entry("x25519mlkem768", 0x11ec),
        Map.entry("secp384r1mlkem1024", 0x11ed)
    );

    private static final int EXTENSION_SUPPORTED_GROUPS = 0x000a;

    /**
     * Probe results per context and lower case group name. Each probe builds an engine and a
     * ClientHello, and the groups are resolved several times per server start and client creation.
     */
    private static final Map<SSLContext, Map<String, Boolean>> JDK_PROBES = new WeakHashMap<>();

    private KeyExchangeGroups() {
    }

    /**
     * Whether the given named group is a post-quantum (pure or hybrid) key exchange group.
     *
     * @param group The group name
     * @return {@code true} for an ML-KEM based group
     */
    public static boolean isPostQuantum(String group) {
        String upper = group.toUpperCase(Locale.ROOT);
        return upper.contains("MLKEM") || upper.contains("KYBER");
    }

    /**
     * Whether a configuration sets any key exchange option, so that groups need to be resolved.
     *
     * @param ssl The SSL configuration
     * @return {@code true} if groups are configured or a post-quantum policy is set
     */
    public static boolean isConfigured(SslConfiguration ssl) {
        return ssl.getKeyExchangeGroups().isPresent() || ssl.getPqcPolicy() != PqcPolicy.NONE;
    }

    /**
     * Resolve the groups to apply to a TLS engine.
     *
     * @param ssl The SSL configuration
     * @param supported Whether the TLS engine supports and enforces a group
     * @param engine The name of the TLS engine, for error messages
     * @return The groups to apply in order of preference, or {@code null} to keep the engine defaults
     * @throws SslConfigurationException If the configuration is invalid or the engine does not support it
     */
    public static @Nullable List<String> resolve(SslConfiguration ssl, Predicate<String> supported, String engine) {
        PqcPolicy policy = ssl.getPqcPolicy();
        String[] configured = ssl.getKeyExchangeGroups().orElse(null);
        if (configured == null) {
            if (policy == PqcPolicy.NONE) {
                return null;
            }
            validateProtocols(ssl, policy);
            List<String> defaults = policy == PqcPolicy.STRICT ? STRICT_DEFAULTS : RELAXED_DEFAULTS;
            List<String> groups = new ArrayList<>(defaults.size());
            for (String group : defaults) {
                if (supported.test(group)) {
                    groups.add(group);
                }
            }
            if (groups.stream().noneMatch(KeyExchangeGroups::isPostQuantum)) {
                throw new SslConfigurationException("The " + name(policy) + " post-quantum policy requires a post-quantum key exchange group, but the "
                    + engine + " TLS implementation supports none of " + defaults.stream().filter(KeyExchangeGroups::isPostQuantum).toList());
            }
            if (policy == PqcPolicy.RELAXED && groups.stream().allMatch(KeyExchangeGroups::isPostQuantum)) {
                throw new SslConfigurationException("The relaxed post-quantum policy requires a classical key exchange group as a fallback, but the "
                    + engine + " TLS implementation supports none of " + defaults.stream().filter(g -> !isPostQuantum(g)).toList());
            }
            return List.copyOf(groups);
        }

        List<String> groups = normalize(configured);
        if (groups.isEmpty()) {
            throw new SslConfigurationException("At least one TLS key exchange group must be configured when key-exchange-groups is set");
        }
        if (policy == PqcPolicy.STRICT) {
            List<String> classical = groups.stream().filter(g -> !isPostQuantum(g)).toList();
            if (!classical.isEmpty()) {
                throw new SslConfigurationException("The strict post-quantum policy only allows post-quantum key exchange groups, but found " + classical);
            }
        } else if (policy == PqcPolicy.RELAXED) {
            if (groups.stream().noneMatch(KeyExchangeGroups::isPostQuantum)) {
                throw new SslConfigurationException("The relaxed post-quantum policy requires at least one post-quantum key exchange group, such as "
                    + X25519_MLKEM768 + ", but found " + groups);
            }
            if (groups.stream().allMatch(KeyExchangeGroups::isPostQuantum)) {
                throw new SslConfigurationException("The relaxed post-quantum policy requires at least one classical key exchange group as a fallback, such as x25519, but found "
                    + groups + ". Use the strict policy to only allow post-quantum groups");
            }
        }
        if (policy != PqcPolicy.NONE) {
            validateProtocols(ssl, policy);
        }
        List<String> unsupported = groups.stream().filter(supported.negate()).toList();
        if (!unsupported.isEmpty()) {
            throw new SslConfigurationException("The " + engine + " TLS implementation does not support the configured key exchange groups " + unsupported);
        }
        return groups;
    }

    /**
     * Support check for groups of a JDK {@link SSLContext}. A group counts as supported when an
     * engine of the context, restricted to that group, offers exactly that group in its TLS 1.3
     * ClientHello, so groups the provider does not know or does not enforce are rejected. Groups
     * without a known code point must be listed by
     * {@link SSLContext#getSupportedSSLParameters()}.
     *
     * @param context The context, or {@code null} for the default context
     * @return Whether the context supports and enforces a group
     */
    public static Predicate<String> jdkSupport(@Nullable SSLContext context) {
        SSLContext ctx;
        String[] names;
        try {
            ctx = context == null ? SSLContext.getDefault() : context;
            names = ctx.getSupportedSSLParameters().getNamedGroups();
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to determine the supported TLS key exchange groups", e);
        }
        Predicate<String> listed = names == null ? group -> false : caseInsensitive(Arrays.asList(names));
        Map<String, Boolean> probed;
        synchronized (JDK_PROBES) {
            probed = JDK_PROBES.computeIfAbsent(ctx, c -> new ConcurrentHashMap<>());
        }
        return group -> {
            Integer codePoint = CODE_POINTS.get(group.toLowerCase(Locale.ROOT));
            if (codePoint == null) {
                return listed.test(group);
            }
            return probed.computeIfAbsent(group.toLowerCase(Locale.ROOT), g -> List.of(codePoint).equals(offeredGroups(ctx, group)));
        };
    }

    /**
     * Create a support check from a list of supported groups.
     *
     * @param supported The supported group names
     * @return Whether a group is in the list, ignoring case
     */
    public static Predicate<String> caseInsensitive(Collection<String> supported) {
        Set<String> lower = new LinkedHashSet<>();
        for (String name : supported) {
            lower.add(name.toLowerCase(Locale.ROOT));
        }
        return group -> lower.contains(group.toLowerCase(Locale.ROOT));
    }

    /**
     * The supported group code points a client engine restricted to one group puts in its ClientHello.
     */
    private static @Nullable List<Integer> offeredGroups(SSLContext context, String group) {
        try {
            SSLEngine engine = context.createSSLEngine();
            engine.setUseClientMode(true);
            SSLParameters parameters = engine.getSSLParameters();
            // post-quantum groups need TLS 1.3, classical groups are offered by TLS 1.2 contexts too
            if (isPostQuantum(group) || Arrays.asList(engine.getEnabledProtocols()).contains(TLS_13)) {
                parameters.setProtocols(new String[]{TLS_13});
            }
            parameters.setNamedGroups(new String[]{group});
            engine.setSSLParameters(parameters);
            ByteBuffer out = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
            engine.beginHandshake();
            engine.wrap(ByteBuffer.allocate(0), out);
            engine.closeOutbound();
            out.flip();
            return parseSupportedGroups(out);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Read the supported_groups extension of a TLS ClientHello record.
     *
     * @param buffer The record
     * @return The group code points, or {@code null} if the record is not a ClientHello with the extension
     */
    static @Nullable List<Integer> parseSupportedGroups(ByteBuffer buffer) {
        try {
            if (buffer.get() != 22) { // handshake record
                return null;
            }
            buffer.position(buffer.position() + 4); // version, length
            if (buffer.get() != 1) { // client hello
                return null;
            }
            buffer.position(buffer.position() + 3 + 2 + 32); // length, version, random
            skip(buffer, buffer.get() & 0xff); // session id
            skip(buffer, buffer.getShort() & 0xffff); // cipher suites
            skip(buffer, buffer.get() & 0xff); // compression methods
            int extensionsLength = buffer.getShort() & 0xffff;
            int end = buffer.position() + extensionsLength;
            while (buffer.position() < end) {
                int type = buffer.getShort() & 0xffff;
                int length = buffer.getShort() & 0xffff;
                if (type == EXTENSION_SUPPORTED_GROUPS) {
                    int count = (buffer.getShort() & 0xffff) / 2;
                    List<Integer> codePoints = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        codePoints.add(buffer.getShort() & 0xffff);
                    }
                    return codePoints;
                }
                skip(buffer, length);
            }
            return null;
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            return null;
        }
    }

    private static void skip(ByteBuffer buffer, int bytes) {
        buffer.position(buffer.position() + bytes);
    }

    private static List<String> normalize(String[] configured) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> groups = new ArrayList<>(configured.length);
        for (String group : configured) {
            String trimmed = group.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains(":") || trimmed.chars().anyMatch(Character::isWhitespace)) {
                throw new SslConfigurationException("Invalid TLS key exchange group '" + trimmed + "': configure each group as its own list entry");
            }
            if (seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                groups.add(trimmed);
            }
        }
        return List.copyOf(groups);
    }

    private static void validateProtocols(SslConfiguration ssl, PqcPolicy policy) {
        String[] protocols = ssl.getProtocols().orElse(null);
        if (protocols == null) {
            return;
        }
        if (policy == PqcPolicy.STRICT) {
            List<String> other = Arrays.stream(protocols).filter(p -> !TLS_13.equalsIgnoreCase(p)).toList();
            if (!other.isEmpty() || protocols.length == 0) {
                throw new SslConfigurationException("The strict post-quantum policy only allows " + TLS_13
                    + ", because older protocols can complete a handshake without a post-quantum group, but the enabled protocols are " + Arrays.toString(protocols));
            }
        } else if (Arrays.stream(protocols).noneMatch(TLS_13::equalsIgnoreCase)) {
            throw new SslConfigurationException("The " + name(policy) + " post-quantum policy requires " + TLS_13
                + ", but the enabled protocols are " + Arrays.toString(protocols));
        }
    }

    private static String name(PqcPolicy policy) {
        return policy.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
