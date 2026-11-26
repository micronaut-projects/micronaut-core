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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
     * Groups enabled by {@link PqcPolicy#RELAXED} when no groups are configured.
     */
    static final List<String> RELAXED_DEFAULTS = List.of(X25519_MLKEM768, "x25519", "secp256r1", "secp384r1");

    /**
     * Groups enabled by {@link PqcPolicy#STRICT} when no groups are configured, as far as supported.
     */
    static final List<String> STRICT_DEFAULTS = List.of(X25519_MLKEM768, "SecP256r1MLKEM768", "SecP384r1MLKEM1024");

    private static final String TLS_13 = "TLSv1.3";

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
     * Resolve the groups to apply to a TLS engine.
     *
     * @param ssl The SSL configuration
     * @param supported Whether the TLS engine supports a group
     * @param engine The name of the TLS engine, for error messages
     * @return The groups to apply in order of preference, or {@code null} to keep the engine defaults
     * @throws SslConfigurationException If the configuration is invalid or the engine does not support it
     */
    public static @Nullable List<String> resolve(SslConfiguration ssl, Predicate<String> supported, String engine) {
        PqcPolicy policy = ssl.getPqcPolicy();
        String[] configured = ssl.getKeyExchangeGroups().orElse(null);
        if (configured == null) {
            if (policy == PqcPolicy.CLIENT_NEGOTIATED) {
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
        } else if (policy == PqcPolicy.RELAXED && groups.stream().noneMatch(KeyExchangeGroups::isPostQuantum)) {
            throw new SslConfigurationException("The relaxed post-quantum policy requires at least one post-quantum key exchange group, such as "
                + X25519_MLKEM768 + ", but found " + groups);
        }
        if (policy != PqcPolicy.CLIENT_NEGOTIATED) {
            validateProtocols(ssl, policy);
        }
        List<String> unsupported = groups.stream().filter(supported.negate()).toList();
        if (!unsupported.isEmpty()) {
            throw new SslConfigurationException("The " + engine + " TLS implementation does not support the configured key exchange groups " + unsupported);
        }
        return groups;
    }

    /**
     * Support check for groups of a JDK {@link SSLContext}.
     *
     * @param context The context, or {@code null} for the default context
     * @return Whether the context supports a group, ignoring case
     */
    public static Predicate<String> jdkSupport(@Nullable SSLContext context) {
        String[] names;
        try {
            SSLContext ctx = context == null ? SSLContext.getDefault() : context;
            names = ctx.getSupportedSSLParameters().getNamedGroups();
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to determine the supported TLS key exchange groups", e);
        }
        return names == null ? group -> true : caseInsensitive(Arrays.asList(names));
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

    private static List<String> normalize(String[] configured) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> groups = new ArrayList<>(configured.length);
        for (String group : configured) {
            String trimmed = group.trim();
            if (!trimmed.isEmpty() && seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                groups.add(trimmed);
            }
        }
        return List.copyOf(groups);
    }

    private static void validateProtocols(SslConfiguration ssl, PqcPolicy policy) {
        String[] protocols = ssl.getProtocols().orElse(null);
        if (protocols != null && Arrays.stream(protocols).noneMatch(TLS_13::equalsIgnoreCase)) {
            throw new SslConfigurationException("The " + name(policy) + " post-quantum policy requires " + TLS_13
                + ", but the enabled protocols are " + Arrays.toString(protocols));
        }
    }

    private static String name(PqcPolicy policy) {
        return policy.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
