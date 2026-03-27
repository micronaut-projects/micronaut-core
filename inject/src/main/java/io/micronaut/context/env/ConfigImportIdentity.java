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
package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.env.PropertySource.Origin;
import io.micronaut.core.util.ConnectionString;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;

/**
 * Computes canonical import identities used for deduplication and cycle detection.
 */
final class ConfigImportIdentity {

    private static final String FILE_PROTOCOL_PREFIX = "file:";
    private static final String FILE_PROTOCOL_AUTHORITY_PREFIX = "file://";
    private static final String OPTIONAL_PREFIX = "optional:";

    private ConfigImportIdentity() {
    }

    /**
     * Compute canonical import location for a parsed declaration.
     *
     * @param connectionString The parsed declaration
     * @param parentOrigin The parent property source origin when resolving relative imports
     * @return Canonical location
     */
    static String canonicalLocation(ConnectionString connectionString, @Nullable Origin parentOrigin) {
        String protocol = connectionString.getProtocol().toLowerCase(Locale.ROOT);
        return switch (protocol) {
            case "file" -> canonicalFileLocation(connectionString, parentOrigin);
            case "classpath", "classpath*" -> canonicalClasspathLocation(connectionString, parentOrigin, protocol);
            case "configtree" -> protocol + "://" + connectionString.getCanonicalPath();
            case "env" -> stripOptional(connectionString.getCanonicalForm());
            default -> stripOptional(connectionString.getCanonicalForm());
        };
    }

    /**
     * Build display string for cycle diagnostics.
     *
     * @param current Current import
     * @param next Next import
     * @return Display value
     */
    static String cycleDisplay(String current, String next) {
        return current + " -> " + next;
    }

    /**
     * Build identity tuple from import declaration.
     *
     * @param connectionString Parsed declaration
     * @param parentOrigin Parent origin
     * @param order Tier order
     * @return Canonical identity
     */
    static ImportIdentity identity(ConnectionString connectionString,
                                   @Nullable Origin parentOrigin,
                                   int order) {
        return new ImportIdentity(canonicalLocation(connectionString, parentOrigin), order);
    }

    private static String canonicalFileLocation(ConnectionString connectionString, @Nullable Origin parentOrigin) {
        verifyNoPort(connectionString, "file");
        String path = connectionString.getCanonicalPath();
        if (path.startsWith("/")) {
            return FILE_PROTOCOL_PREFIX + normalizeFilePath(Paths.get(path).normalize());
        }
        if (!connectionString.getHosts().isEmpty()) {
            StringBuilder authority = new StringBuilder();
            for (int i = 0; i < connectionString.getHosts().size(); i++) {
                ConnectionString.HostPort hostPort = connectionString.getHosts().get(i);
                if (i > 0) {
                    authority.append(',');
                }
                authority.append(hostPort.host());
                if (hostPort.port() != null) {
                    authority.append(':').append(hostPort.port());
                }
            }
            return FILE_PROTOCOL_AUTHORITY_PREFIX + authority + "/" + path;
        }
        String base = requireParent(parentOrigin, "file");
        Path basePath = Paths.get(base).normalize();
        Path baseDirectory;
        if (base.endsWith("/")) {
            baseDirectory = basePath;
        } else {
            Path parent = basePath.getParent();
            baseDirectory = parent == null ? basePath : parent;
        }
        Path resolved = baseDirectory.resolve(path).normalize();
        return FILE_PROTOCOL_PREFIX + normalizeFilePath(resolved);
    }

    private static String normalizeFilePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String canonicalClasspathLocation(ConnectionString connectionString,
                                                     @Nullable Origin parentOrigin,
                                                     String protocol) {
        verifyNoPort(connectionString, protocol);
        String path = connectionString.getCanonicalPath();
        if (path.startsWith("/")) {
            throw new ConfigurationException("Absolute classpath imports are not supported for security reasons: " + connectionString.getRawValue());
        }
        if (!connectionString.getHosts().isEmpty()) {
            throw new ConfigurationException("Classpath imports do not support authority hosts: " + connectionString.getRawValue());
        }
        String normalizedParent = resolveClasspathBase(parentOrigin);
        int slash = normalizedParent.lastIndexOf('/');
        String base = slash >= 0 ? normalizedParent.substring(0, slash + 1) : "";
        return protocol + ":" + normalizeClasspath(base + path);
    }

    private static String resolveClasspathBase(@Nullable Origin parentOrigin) {
        if (parentOrigin == null) {
            return "";
        }
        String location = parentOrigin.location();
        if (!location.startsWith("classpath:")) {
            return "";
        }
        return normalizeClasspath(extractResourcePath(location));
    }

    private static void verifyNoPort(ConnectionString connectionString, String protocol) {
        for (ConnectionString.HostPort hostPort : connectionString.getHosts()) {
            if (hostPort.port() != null) {
                throw new ConfigurationException("Port is not supported for " + protocol + " imports: " + connectionString.getRawValue());
            }
        }
    }

    private static String requireParent(@Nullable Origin parentOrigin, String protocol) {
        if (parentOrigin == null) {
            throw new ConfigurationException("Relative " + protocol + " import requires parent origin");
        }
        String location = parentOrigin.location();
        if (protocol.equals("file")) {
            if (location.startsWith(FILE_PROTOCOL_PREFIX)) {
                return extractResourcePath(location);
            }
            if (Paths.get(location).isAbsolute()) {
                return location;
            }
        } else if (protocol.equals("classpath")) {
            if (location.startsWith("classpath:")) {
                return extractResourcePath(location);
            }
            throw new ConfigurationException("Relative classpath import requires classpath: parent origin but found: " + location);
        }
        throw new ConfigurationException("Relative " + protocol + " import requires " + protocol + ": parent origin but found: " + location);
    }

    private static String normalizeClasspath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        Path p = Paths.get(normalized).normalize();
        return p.toString().replace('\\', '/');
    }

    private static String stripOptional(String value) {
        if (value.startsWith(OPTIONAL_PREFIX)) {
            return value.substring(OPTIONAL_PREFIX.length());
        }
        return value;
    }

    /**
     * Extract the resource path from a canonical protocol location.
     *
     * @param location Canonical location such as {@code file:/tmp/app.properties} or {@code classpath:config/app.yml}
     * @return Resource path without protocol prefix
     */
    static String extractResourcePath(String location) {
        Objects.requireNonNull(location, "Location cannot be null");
        int index = location.indexOf(':');
        if (index < 0 || index == location.length() - 1) {
            throw new IllegalArgumentException("Location does not contain protocol path separator: " + location);
        }
        return location.substring(index + 1);
    }

    record ImportIdentity(String canonicalLocation, int tierOrder) {
    }
}
