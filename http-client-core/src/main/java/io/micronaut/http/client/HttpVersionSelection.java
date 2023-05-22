/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.client;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.annotation.Client;

import java.util.Arrays;

/**
 * This class collects information about HTTP client protocol version settings, such as the
 * {@link PlaintextMode} and the ALPN configuration.
 *
 * @author Jonas Konrad
 * @since 4.0
 */
public final class HttpVersionSelection {
    /**
     * ALPN protocol ID for HTTP/1.1.
     */
    public static final String ALPN_HTTP_1 = "http/1.1";
    /**
     * ALPN protocol ID for HTTP/2.
     */
    public static final String ALPN_HTTP_2 = "h2";
    /**
     * ALPN protocol ID for HTTP/3. When this is selected, it must be the only ALPN ID, since we
     * will connect via UDP.
     */
    public static final String ALPN_HTTP_3 = "h3";

    private static final HttpVersionSelection LEGACY_1 = new HttpVersionSelection(
        PlaintextMode.HTTP_1,
        false,
        new String[]{ALPN_HTTP_1},
        false
    );

    private static final HttpVersionSelection LEGACY_2 = new HttpVersionSelection(
        PlaintextMode.H2C,
        true,
        new String[]{ALPN_HTTP_1, ALPN_HTTP_2},
        true
    );

    private final PlaintextMode plaintextMode;
    private final boolean alpn;
    private final String[] alpnSupportedProtocols;
    private final boolean http2CipherSuites;
    private final boolean http3;

    private HttpVersionSelection(@NonNull PlaintextMode plaintextMode, boolean alpn, @NonNull String[] alpnSupportedProtocols, boolean http2CipherSuites) {
        this.plaintextMode = plaintextMode;
        this.alpn = alpn;
        this.alpnSupportedProtocols = alpnSupportedProtocols;
        this.http2CipherSuites = http2CipherSuites;
        this.http3 = Arrays.asList(alpnSupportedProtocols).contains(ALPN_HTTP_3);
        if (http3 && alpnSupportedProtocols.length != 1) {
            throw new IllegalArgumentException("When using HTTP 3, h3 must be the only ALPN protocol");
        }
    }

    /**
     * Get the {@link HttpVersionSelection} that matches Micronaut HTTP client 3.x behavior for the
     * given version setting.
     *
     * @param httpVersion The HTTP version as configured for Micronaut HTTP client 3.x
     * @return The version selection
     */
    @NonNull
    public static HttpVersionSelection forLegacyVersion(@NonNull HttpVersion httpVersion) {
        switch (httpVersion) {
            case HTTP_1_0:
            case HTTP_1_1:
                return LEGACY_1;
            case HTTP_2_0:
                return LEGACY_2;
            default:
                throw new IllegalArgumentException("HTTP version " + httpVersion + " not supported here");
        }
    }

    /**
     * Construct a version selection from the given client configuration.
     *
     * @param clientConfiguration The client configuration
     * @return The configured version selection
     */
    public static HttpVersionSelection forClientConfiguration(HttpClientConfiguration clientConfiguration) {
        @SuppressWarnings("deprecation")
        HttpVersion legacyHttpVersion = clientConfiguration.getHttpVersion();
        if (legacyHttpVersion != null) {
            return forLegacyVersion(legacyHttpVersion);
        } else {
            String[] alpnModes = clientConfiguration.getAlpnModes().toArray(new String[0]);
            return new HttpVersionSelection(
                clientConfiguration.getPlaintextMode(),
                true,
                alpnModes,
                Arrays.asList(alpnModes).contains(ALPN_HTTP_2)
            );
        }
    }

    /**
     * Infer the version selection for the given {@link Client} annotation, if any version settings
     * are set.
     *
     * @param metadata The annotation metadata possibly containing a {@link Client} annotation
     * @return The configured version selection, or {@code null} if the version is not explicitly
     * set and should be inherited from the normal configuration instead.
     */
    @Internal
    @Nullable
    public static HttpVersionSelection forClientAnnotation(AnnotationMetadata metadata) {
        HttpVersion legacyHttpVersion =
            metadata.enumValue(Client.class, "httpVersion", HttpVersion.class).orElse(null);
        if (legacyHttpVersion != null) {
            return forLegacyVersion(legacyHttpVersion);
        } else {
            String[] alpnModes = metadata.stringValues(Client.class, "alpnModes");
            PlaintextMode plaintextMode = metadata.enumValue(Client.class, "plaintextMode", PlaintextMode.class)
                .orElse(null);
            if (alpnModes.length == 0 && plaintextMode == null) {
                // nothing set at all, default to client configuration
                return null;
            }

            // defaults
            if (alpnModes.length == 0) {
                alpnModes = new String[]{ALPN_HTTP_2, ALPN_HTTP_1};
            }
            if (plaintextMode == null) {
                plaintextMode = PlaintextMode.HTTP_1;
            }
            return new HttpVersionSelection(
                plaintextMode,
                true,
                alpnModes,
                Arrays.asList(alpnModes).contains(ALPN_HTTP_2)
            );
        }
    }

    /**
     * @return Connection mode to use for plaintext connections
     */
    @Internal
    public PlaintextMode getPlaintextMode() {
        return plaintextMode;
    }

    /**
     * @return Protocols that should be shown as supported via ALPN
     */
    @Internal
    public String[] getAlpnSupportedProtocols() {
        return alpnSupportedProtocols;
    }

    /**
     * @return Whether ALPN should be used
     */
    @Internal
    public boolean isAlpn() {
        return alpn;
    }

    /**
     * @return Whether TLS cipher suites should be constrained to those defined by the HTTP/2 spec
     */
    @Internal
    public boolean isHttp2CipherSuites() {
        return http2CipherSuites;
    }

    @Internal
    public boolean isHttp3() {
        return http3;
    }

    /**
     * The connection mode to use for plaintext (non-TLS) connections.
     */
    public enum PlaintextMode {
        /**
         * Normal HTTP/1.1 connection.
         */
        HTTP_1,
        /**
         * HTTP/2 cleartext upgrade from HTTP/1.1.
         */
        H2C,
    }
}
