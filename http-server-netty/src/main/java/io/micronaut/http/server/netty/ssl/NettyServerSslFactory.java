/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.server.netty.ssl;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.netty.NettySslContextBuilder;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ServerSslConfiguration;
import jakarta.inject.Singleton;

/**
 * Factory for creating server-side Netty SSL context builders.
 * Used by the Netty HTTP server to construct TCP/HTTP and QUIC/HTTP/3
 * SSL contexts based on Micronaut configuration.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Singleton
public class NettyServerSslFactory {
    /**
     * Create a server-side SSL context builder for the given listener configuration.
     * Current implementation delegates to {@link #serverBuilder()}.
     *
     * @param listenerConfiguration the listener configuration for which to create the builder
     * @return a server-mode {@link NettySslContextBuilder}
     */
    @Experimental
    public NettySslContextBuilder serverBuilder(NettyHttpServerConfiguration. NettyListenerConfiguration listenerConfiguration) {
        return serverBuilder();
    }

    /**
     * Creates a legacy SSL builder for an explicitly configured server. The supplied configuration
     * can differ from the application context's SSL configuration. Replacements must preserve its
     * TLS policy or reject unsupported settings rather than fall back to another provider.
     *
     * @param configuration The server configuration
     * @param sslConfiguration The explicit SSL configuration
     * @param resourceResolver The resolver for key and trust material
     * @return A builder for the supplied configuration
     * @since 5.3.0
     */
    @Experimental
    public ServerSslBuilder legacyBuilder(HttpServerConfiguration configuration,
                                          ServerSslConfiguration sslConfiguration,
                                          ResourceResolver resourceResolver) {
        return sslConfiguration.buildSelfSigned()
            ? new SelfSignedSslBuilder(configuration, sslConfiguration, resourceResolver)
            : new CertificateProvidedSslBuilder(configuration, sslConfiguration, resourceResolver);
    }

    /**
     * Create a server-side SSL context builder.
     *
     * @return a server-mode {@link NettySslContextBuilder}
     */
    public NettySslContextBuilder serverBuilder() {
        return new NettySslContextBuilder(true);
    }
}
