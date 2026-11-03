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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.manifest.DevManifest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Who may reach the development pages and responses of a launch: the {@code /dev} endpoint, the Control Panel and the
 * compiler's diagnostics. Two checks, both in the development runtime only:
 *
 * <ul>
 *     <li>the {@code Host} of a request must be {@code localhost}, a subdomain of it, a loopback address or a name
 *     of {@value DevManifest#ALLOWED_HOSTS}, so that a page of another site whose name is made to resolve to this
 *     machine cannot read them;</li>
 *     <li>the request must carry the access token generated once per launch, which the launcher logs as a link at
 *     startup: in a cookie the link sets, or in the {@value #TOKEN_HEADER} header for a client that is not a browser.
 *     {@value DevManifest#ACCESS_TOKEN_ENABLED} turns this check off.</li>
 * </ul>
 *
 * <p>The token lives as long as the launcher, so a reload keeps it; a relaunch makes a new one.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class DevAccess {

    /**
     * The query parameter of the link that hands the token to a browser.
     */
    public static final String TOKEN_PARAMETER = "token";

    /**
     * The header a client that is not a browser, such as curl or an IDE, sends the token in.
     */
    public static final String TOKEN_HEADER = "X-Micronaut-Dev-Token";

    /**
     * The server host setting, whose name is answered too when it names one.
     */
    static final String SERVER_HOST = "micronaut.server.host";

    private static final Logger LOG = LoggerFactory.getLogger(DevAccess.class);
    private static final int TOKEN_BYTES = 32;
    private static final String LOCALHOST = "localhost";

    private final String token;
    private final byte[] tokenBytes;
    private final String cookieName;
    private final @Nullable DevManifest manifest;
    private volatile Set<String> allowedHosts = Set.of(); // NOSONAR java:S3077 an unmodifiable set, replaced whole
    private volatile boolean tokenEnabled;

    /**
     * @param allowedHosts The host names answered besides the loopback ones
     * @param tokenEnabled Whether the token is needed
     */
    public DevAccess(Collection<String> allowedHosts, boolean tokenEnabled) {
        this(null, allowedHosts, tokenEnabled);
    }

    private DevAccess(@Nullable DevManifest manifest, Collection<String> allowedHosts, boolean tokenEnabled) {
        byte[] random = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(random);
        this.token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        this.tokenBytes = token.getBytes(StandardCharsets.US_ASCII);
        // one cookie per launch: two applications on localhost, on other ports, keep their own
        this.cookieName = "MICRONAUT_DEV_" + HexFormat.of().formatHex(sha256(tokenBytes), 0, 4).toUpperCase(Locale.ROOT);
        this.manifest = manifest;
        this.allowedHosts = normalized(allowedHosts);
        this.tokenEnabled = tokenEnabled;
    }

    /**
     * The access of a launch, with the manifest's settings until a generation's configuration gives those the
     * manifest leaves out.
     *
     * @param manifest The manifest
     * @return The access, with a new token
     */
    static DevAccess of(DevManifest manifest) {
        return new DevAccess(manifest, manifest.allowedHosts(), manifest.accessTokenEnabled());
    }

    /**
     * Takes the settings the manifest leaves out from a generation's configuration, {@code application.properties} for
     * instance: the manifest wins, then the application, then the defaults. The name the server is bound to, when the
     * configuration gives one, is answered too.
     *
     * @param generation The generation, or null when none runs, which leaves the settings as they are
     */
    void configure(@Nullable ApplicationContext generation) {
        if (generation == null || manifest == null) {
            return;
        }
        try {
            Set<String> hosts = new LinkedHashSet<>(manifest.sets(DevManifest.ALLOWED_HOSTS)
                ? manifest.allowedHosts()
                : generation.getEnvironment().getProperty(DevManifest.ALLOWED_HOSTS, String[].class).map(List::of).orElse(List.of()));
            generation.getEnvironment().getProperty(SERVER_HOST, String.class).ifPresent(hosts::add);
            allowedHosts = normalized(hosts);
            tokenEnabled = manifest.sets(DevManifest.ACCESS_TOKEN_ENABLED)
                ? manifest.accessTokenEnabled()
                : generation.getEnvironment().getProperty(DevManifest.ACCESS_TOKEN_ENABLED, Boolean.class).orElse(true);
        } catch (RuntimeException e) {
            // a value that does not convert, or a context stopped meanwhile: the settings in force stay
            LOG.warn("Ignoring the development access settings of the application's configuration: {}", e.getMessage());
        }
    }

    /**
     * @return The token of this launch
     */
    public String token() {
        return token;
    }

    /**
     * @return The name of the cookie a browser keeps the token in, one per launch
     */
    public String cookieName() {
        return cookieName;
    }

    /**
     * @return Whether the development pages need the token
     */
    public boolean tokenEnabled() {
        return tokenEnabled;
    }

    /**
     * Whether a value is the token of this launch, compared in constant time.
     *
     * @param candidate The value a request carries, if any
     * @return True when it is the token
     */
    public boolean accepts(@Nullable String candidate) {
        return candidate != null && MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), tokenBytes);
    }

    /**
     * Whether the development pages answer a host: {@code localhost} and its subdomains, the loopback addresses and the
     * names allowed. A port, if any, is left out of the comparison.
     *
     * @param host The {@code Host} of a request, or the host of an {@code Origin}
     * @return True when it is answered
     */
    public boolean allowsHost(@Nullable String host) {
        String name = hostName(host);
        if (name == null) {
            return false;
        }
        return isLoopback(name) || allowedHosts.contains(name);
    }

    /**
     * Whether a host name is one of this machine's loopback names, which every browser resolves to this machine.
     *
     * @param name A host name, lower case, IPv6 addresses in brackets
     * @return True for {@code localhost}, its subdomains, {@code 127.0.0.1} and {@code [::1]}
     */
    public static boolean isLoopback(String name) {
        return name.equals(LOCALHOST) || name.endsWith("." + LOCALHOST) || name.equals("127.0.0.1") || name.equals("[::1]");
    }

    /**
     * The host name of a {@code Host} value, without its port: lower case, without a trailing dot, an IPv6 address in
     * brackets.
     *
     * @param host The value
     * @return The name, or null when the value is not a host
     */
    public static @Nullable String hostName(@Nullable String host) {
        if (host == null) {
            return null;
        }
        String value = host.trim().toLowerCase(Locale.ROOT);
        String name;
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 0 || end + 1 < value.length() && !isPort(value.substring(end + 1))) {
                return null;
            }
            name = value.substring(0, end + 1);
            return name.length() > 2 && name.substring(1, name.length() - 1).chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == ':' || c == '.') ? name : null;
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            if (value.indexOf(':', colon + 1) >= 0) {
                // a bare IPv6 address, as a setting gives it: never what a Host header carries
                return value.chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == ':' || c == '.') ? "[" + value + "]" : null;
            }
            if (!isPort(value.substring(colon))) {
                return null;
            }
            value = value.substring(0, colon);
        }
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty() || !value.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '.' || c == '_')) {
            return null;
        }
        return value;
    }

    private static boolean isPort(String suffix) {
        return suffix.length() > 1 && suffix.length() <= 6 && suffix.charAt(0) == ':' && suffix.substring(1).chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static Set<String> normalized(Collection<String> hosts) {
        Set<String> names = new LinkedHashSet<>();
        for (String host : hosts) {
            String name = hostName(host);
            if (name != null && !name.equals("0.0.0.0") && !name.equals("[::]")) {
                names.add(name);
            } else if (name == null) {
                LOG.warn("Ignoring {}={}: not a host name", DevManifest.ALLOWED_HOSTS, host);
            }
        }
        return Set.copyOf(names);
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
