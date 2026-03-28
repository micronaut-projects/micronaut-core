/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.util;

import io.micronaut.core.naming.NameUtils;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Parsed representation of protocol-based connection strings.
 *
 * <p>Supported syntax:</p>
 * <pre>{@code
 * [optional:]<protocol>://[username[:password]@][host[:port][,host[:port]...]/]<path>[?key=value[&key=value...]]
 * }</pre>
 *
 * <p>Examples:</p>
 * <ul>
 *     <li>{@code file:///etc/app/config.yml}</li>
 *     <li>{@code optional:file://config/extra}</li>
 *     <li>{@code consul://user:pass@localhost:8500/app/config?dc=local}</li>
 *     <li>{@code env://APP_IMPORT.properties}</li>
 * </ul>
 *
 * @param rawValue      The original unparsed connection string
 * @param parseMode     The parse mode used when parsing
 * @param prefix        Optional prefix (e.g. {@code optional})
 * @param protocol      The protocol component
 * @param username      Optional username from authority
 * @param password      Optional password from authority
 * @param hosts         Parsed list of host/port pairs
 * @param path          The path component
 * @param options       Parsed query options
 * @param canonicalForm Normalized canonical representation
 * @since 5.0
 */
public record ConnectionString(String rawValue,
                               ParseMode parseMode,
                               @Nullable String prefix,
                               String protocol,
                               @Nullable String username,
                               @Nullable String password,
                               List<HostPort> hosts,
                               String path,
                               Map<String, String> options,
                               String canonicalForm) {

    private static final String OPTIONAL_PREFIX = "optional:";
    private static final String OPTIONAL_LABEL = "optional";

    public ConnectionString {
        Objects.requireNonNull(rawValue, "rawValue");
        Objects.requireNonNull(parseMode, "parseMode");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(hosts, "hosts");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        hosts = List.copyOf(hosts);
        options = Map.copyOf(options);
        canonicalForm = buildCanonicalForm(prefix, protocol, username, password, hosts, path, options);
    }

    private ConnectionString(String rawValue,
                             ParseMode parseMode,
                             @Nullable String prefix,
                             String protocol,
                             @Nullable String username,
                             @Nullable String password,
                             List<HostPort> hosts,
                             String path,
                             Map<String, String> options) {
        this(rawValue,
            parseMode,
            prefix,
            protocol,
            username,
            password,
            hosts,
            path,
            options,
            buildCanonicalForm(prefix, protocol, username, password, List.copyOf(hosts), path, Map.copyOf(options)));
    }

    /**
     * Parse a connection string.
     *
     * @param value The value
     * @return The parsed connection string
     */
    public static ConnectionString parse(String value) {
        return parse(value, ParseMode.PATH);
    }

    /**
     * Parse a connection string with the provided mode.
     *
     * @param value The raw value
     * @param parseMode The parse mode that defines required components
     * @return The parsed connection string
     */
    public static ConnectionString parse(String value, ParseMode parseMode) {
        Objects.requireNonNull(value, "Connection string cannot be null");
        Objects.requireNonNull(parseMode, "Parse mode cannot be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Connection string cannot be empty");
        }

        String prefix = null;
        String toParse = value;
        if (toParse.startsWith(OPTIONAL_PREFIX)) {
            prefix = OPTIONAL_LABEL;
            toParse = toParse.substring(OPTIONAL_PREFIX.length());
            if (toParse.isEmpty()) {
                throw new IllegalArgumentException("Connection string cannot be empty after optional prefix");
            }
        }

        int protocolSeparator = toParse.indexOf("://");
        if (protocolSeparator < 1) {
            throw new IllegalArgumentException("Connection string must declare protocol:// : " + value);
        }

        String protocol = toParse.substring(0, protocolSeparator);
        validateProtocol(protocol, value);
        protocol = protocol.toLowerCase(Locale.ROOT);

        String remainder = toParse.substring(protocolSeparator + 3);
        String target = remainder;
        String query = "";
        int queryIndex = remainder.indexOf('?');
        if (queryIndex >= 0) {
            target = remainder.substring(0, queryIndex);
            query = remainder.substring(queryIndex + 1);
        }

        ParseTarget parseTarget = splitTarget(target, value, parseMode);
        ParseAuthority parseAuthority = parseAuthority(parseTarget.authority(), value);
        validateProtocolAuthority(protocol, parseAuthority.hosts(), value);
        validateRequiredComponents(parseMode, parseTarget.path(), parseAuthority.hosts(), value);
        Map<String, String> options = parseOptions(query, value);

        return new ConnectionString(
            value,
            parseMode,
            prefix,
            protocol,
            parseAuthority.username(),
            parseAuthority.password(),
            parseAuthority.hosts(),
            parseTarget.path(),
            options
        );
    }

    public String getRawValue() {
        return rawValue;
    }

    /**
     * @return Returns the parse mode used to parse this value
     */
    public ParseMode getParseMode() {
        return parseMode;
    }

    /**
     * @return Returns optional prefix, for example {@code optional}
     */
    public Optional<String> getPrefix() {
        return Optional.ofNullable(prefix);
    }

    /**
     * @return Returns whether the connection string starts with {@code optional:}
     */
    public boolean isOptional() {
        return "optional".equals(prefix);
    }

    /**
     * @return Returns the normalized lower-case protocol
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * @return Returns optional username from the authority section
     */
    public Optional<String> getUsername() {
        return Optional.ofNullable(username);
    }

    /**
     * @return Returns optional password from the authority section
     */
    public Optional<String> getPassword() {
        return Optional.ofNullable(password);
    }

    /**
     * @return Returns immutable list of host/port entries from the authority section
     */
    public List<HostPort> getHosts() {
        return hosts;
    }

    /**
     * @return Returns parsed path component (may be empty depending on parse mode)
     */
    public String getPath() {
        return path;
    }

    /**
     * @return Returns normalized path with duplicate separators and dot segments removed
     */
    public String getCanonicalPath() {
        return canonicalizePath(path);
    }

    /**
     * @return Returns resource path for file/classpath-like use cases
     */
    public String getResourcePath() {
        if (path.isBlank()) {
            throw new IllegalArgumentException("Connection string path is empty: " + rawValue);
        }
        return getCanonicalPath();
    }

    /**
     * @return Returns optional file extension derived from {@link #getPath()}
     */
    public Optional<String> getExtension() {
        String extension = NameUtils.extension(path);
        return extension.isEmpty() ? Optional.empty() : Optional.of(extension);
    }

    /**
     * @return Returns immutable parsed query/options map
     */
    public Map<String, String> getOptions() {
        return options;
    }

    /**
     * @return Returns canonical representation preserving semantic components
     */
    public String getCanonicalForm() {
        return canonicalForm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectionString that)) {
            return false;
        }
        return parseMode == that.parseMode
            && Objects.equals(prefix, that.prefix)
            && Objects.equals(protocol, that.protocol)
            && Objects.equals(username, that.username)
            && Objects.equals(password, that.password)
            && Objects.equals(hosts, that.hosts)
            && Objects.equals(path, that.path)
            && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parseMode, prefix, protocol, username, password, hosts, path, options);
    }

    @Override
    public String toString() {
        return rawValue;
    }

    private static ParseTarget splitTarget(String target, String originalValue, ParseMode parseMode) {
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Connection string target cannot be empty: " + originalValue);
        }
        if (target.startsWith("/")) {
            return new ParseTarget("", target);
        }
        int firstSlash = target.indexOf('/');
        if (firstSlash < 0) {
            if (looksLikeAuthority(target)) {
                return new ParseTarget(target, "");
            }
            return new ParseTarget("", target);
        }
        String candidateAuthority = target.substring(0, firstSlash);
        String candidatePath = target.substring(firstSlash + 1);
        if (candidatePath.isEmpty() && parseMode.pathRequired()) {
            throw new IllegalArgumentException("Connection string path cannot be empty: " + originalValue);
        }
        if (looksLikeAuthority(candidateAuthority)) {
            return new ParseTarget(candidateAuthority, candidatePath);
        }
        return new ParseTarget("", target);
    }

    private static boolean looksLikeAuthority(String value) {
        if (value.isEmpty()) {
            return false;
        }
        if (value.indexOf('@') >= 0 || value.indexOf(',') >= 0) {
            return true;
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && colon < value.length() - 1) {
            String possiblePort = value.substring(colon + 1);
            if (StringUtils.isDigits(possiblePort)) {
                return true;
            }
        }
        // Recognize localhost as a hostname without an explicit port
        return "localhost".equalsIgnoreCase(value);
    }

    private static ParseAuthority parseAuthority(String authority, String originalValue) {
        if (authority.isEmpty()) {
            return new ParseAuthority(null, null, Collections.emptyList());
        }

        String username = null;
        String password = null;
        String hostsPart = authority;
        int atIndex = authority.lastIndexOf('@');
        if (atIndex >= 0) {
            if (atIndex == 0 || atIndex == authority.length() - 1) {
                throw new IllegalArgumentException("Malformed authentication segment in connection string: " + originalValue);
            }
            String userInfo = authority.substring(0, atIndex);
            hostsPart = authority.substring(atIndex + 1);
            int colon = userInfo.indexOf(':');
            if (colon >= 0) {
                username = userInfo.substring(0, colon);
                password = userInfo.substring(colon + 1);
            } else {
                username = userInfo;
            }
        }

        if (hostsPart.isEmpty()) {
            throw new IllegalArgumentException("Missing host segment in connection string: " + originalValue);
        }
        List<HostPort> hosts = new ArrayList<>();
        for (String hostPart : hostsPart.split(",")) {
            hosts.add(parseHostPart(hostPart.trim(), originalValue));
        }
        return new ParseAuthority(username, password, hosts);
    }

    private static HostPort parseHostPart(String trimmed, String originalValue) {
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Malformed host list in connection string: " + originalValue);
        }
        String host = trimmed;
        Integer port = null;
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1) {
            String possiblePort = trimmed.substring(colon + 1);
            if (StringUtils.isDigits(possiblePort)) {
                host = trimmed.substring(0, colon);
                port = Integer.parseInt(possiblePort);
            }
        }
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be empty in connection string: " + originalValue);
        }
        return new HostPort(host, port);
    }

    private static Map<String, String> parseOptions(String query, String originalValue) {
        if (query.isEmpty()) {
            return Map.of();
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (String part : query.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) {
                throw new IllegalArgumentException("Malformed option '" + part + "' in connection string: " + originalValue);
            }
            String key = part.substring(0, eq);
            String val = part.substring(eq + 1);
            if (options.putIfAbsent(key, val) != null) {
                throw new IllegalArgumentException("Duplicate option '" + key + "' in connection string: " + originalValue);
            }
        }
        return Map.copyOf(options);
    }

    private static void validateProtocol(String protocol, String originalValue) {
        if (protocol.isEmpty()) {
            throw new IllegalArgumentException("Protocol cannot be empty in connection string: " + originalValue);
        }
        if (!Character.isLetter(protocol.charAt(0))) {
            throw new IllegalArgumentException("Protocol must start with a letter in connection string: " + originalValue);
        }
        boolean wildcardSeen = false;
        for (int i = 1; i < protocol.length(); i++) {
            char c = protocol.charAt(i);
            if (c == '*') {
                if (wildcardSeen || i != protocol.length() - 1) {
                    throw new IllegalArgumentException("Invalid protocol character '" + c + "' in connection string: " + originalValue);
                }
                wildcardSeen = true;
            } else if (!isValidProtocolChar(c)) {
                throw new IllegalArgumentException("Invalid protocol character '" + c + "' in connection string: " + originalValue);
            }
        }
    }

    private static boolean isValidProtocolChar(char c) {
        return Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.';
    }

    private static void validateProtocolAuthority(String protocol, List<HostPort> hosts, String originalValue) {
        if (("file".equals(protocol) || "classpath".equals(protocol) || "classpath*".equals(protocol))
            && hosts.stream().anyMatch(host -> host.port() != null)) {
            throw new IllegalArgumentException("Port is not supported for " + protocol + " protocol in connection string: " + originalValue);
        }
    }

    private static void validateRequiredComponents(ParseMode parseMode,
                                                   String path,
                                                   List<HostPort> hosts,
                                                   String originalValue) {
        if (parseMode.pathRequired() && path.isEmpty()) {
            throw new IllegalArgumentException("Connection string path is required for parse mode " + parseMode + ": " + originalValue);
        }
        if (parseMode.hostRequired() && hosts.isEmpty()) {
            throw new IllegalArgumentException("Connection string host is required for parse mode " + parseMode + ": " + originalValue);
        }
    }

    private static String canonicalizePath(String rawPath) {
        if (rawPath.isEmpty()) {
            return rawPath;
        }
        String normalized = rawPath.replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        Path p = Paths.get(normalized).normalize();
        validateNoParentTraversal(p, rawPath);
        return p.toString().replace('\\', '/');
    }

    private static void validateNoParentTraversal(Path normalizedPath, String rawPath) {
        for (Path segment : normalizedPath) {
            if ("..".equals(segment.toString())) {
                throw new IllegalArgumentException("Parent path segments are not allowed in connection string path: " + rawPath);
            }
        }
    }

    private static String buildCanonicalForm(@Nullable String prefix,
                                             String protocol,
                                             @Nullable String username,
                                             @Nullable String password,
                                             List<HostPort> hosts,
                                             String path,
                                             Map<String, String> options) {
        StringBuilder out = new StringBuilder();
        if (OPTIONAL_LABEL.equals(prefix)) {
            out.append(OPTIONAL_PREFIX);
        }
        out.append(protocol).append("://");
        if (username != null) {
            out.append(username);
            if (password != null) {
                out.append(':').append(password);
            }
            out.append('@');
        }
        if (!hosts.isEmpty()) {
            for (int i = 0; i < hosts.size(); i++) {
                HostPort hostPort = hosts.get(i);
                if (i > 0) {
                    out.append(',');
                }
                out.append(hostPort.host());
                if (hostPort.port() != null) {
                    out.append(':').append(hostPort.port());
                }
            }
            if (!path.isEmpty()) {
                out.append('/');
            }
        }
        out.append(path);
        if (!options.isEmpty()) {
            out.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : options.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                if (!first) {
                    out.append('&');
                }
                out.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }
        return out.toString();
    }

    private record ParseTarget(String authority, String path) {
    }

    private record ParseAuthority(@Nullable String username,
                                  @Nullable String password,
                                  List<HostPort> hosts) {
    }

    /**
     * Host and optional port.
     *
     * @param host The host value
     * @param port The optional port
     */
    public record HostPort(String host, @Nullable Integer port) {
        public HostPort {
            Objects.requireNonNull(host, "Host cannot be null");
            if (host.isEmpty()) {
                throw new IllegalArgumentException("Host cannot be empty");
            }
        }
    }

    /**
     * Parse mode that defines which components are required.
     */
    public enum ParseMode {
        /**
         * Require path and allow hosts.
         */
        PATH(true, false),
        /**
         * Require host and allow empty path.
         */
        HOST(false, true);

        private final boolean pathRequired;
        private final boolean hostRequired;

        ParseMode(boolean pathRequired, boolean hostRequired) {
            this.pathRequired = pathRequired;
            this.hostRequired = hostRequired;
        }

        /**
         * @return Returns whether the path is required
         */
        public boolean pathRequired() {
            return pathRequired;
        }

        /**
         * @return Returns whether at least one host is required
         */
        public boolean hostRequired() {
            return hostRequired;
        }
    }
}
