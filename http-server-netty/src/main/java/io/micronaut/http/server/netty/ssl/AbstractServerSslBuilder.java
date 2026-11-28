/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.netty.NettyTlsUtils;
import io.micronaut.http.netty.QuicTlsUtils;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.http.ssl.SslConfigurationException;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.KeyManagerFactory;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLException;
import java.security.KeyStore;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Base class for {@link ServerSslBuilder} implementations. This class implements the various build
 * methods for {@link ServerSslBuilder} and {@link SslBuilder} using
 * {@link #getTrustManagerFactory} and {@link #getKeyManagerFactory}. Subclasses can override those
 * methods with their own implementation that will be called on each ssl context build.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
public abstract class AbstractServerSslBuilder extends SslBuilder<SslContext> implements ServerSslBuilder {
    private final HttpServerConfiguration httpServerConfiguration;

    /**
     * Create a new server SSL builder.
     *
     * @param resourceResolver        Resource resolver for default key/trust store loading implementation
     * @param httpServerConfiguration Server configuration for determining HTTP version
     */
    public AbstractServerSslBuilder(ResourceResolver resourceResolver, HttpServerConfiguration httpServerConfiguration) {
        super(resourceResolver);
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public final Optional<SslContext> build() {
        return build(getSslConfiguration());
    }

    @SuppressWarnings("Duplicates")
    @Override
    public final Optional<SslContext> build(SslConfiguration ssl) {
        final HttpVersion httpVersion = httpServerConfiguration.getHttpVersion();
        return build(ssl, httpVersion);
    }

    @Override
    public final Optional<SslContext> build(SslConfiguration ssl, HttpVersion httpVersion) {
        try {
            return Optional.of(NettyTlsUtils.withKeyExchangeGroups(createSslContext(ssl, httpVersion), ssl));
        } catch (SSLException ex) {
            throw new SslConfigurationException("An error occurred while setting up SSL", ex);
        }
    }

    /**
     * Builds the TCP context, retaining material loading and stock builder processing by default.
     * Alternative implementations must preserve configured TLS policy and any required
     * {@link #processBuilder} customization, including ACME ALPN challenges.
     *
     * @param ssl The SSL configuration
     * @param httpVersion The HTTP version
     * @return The SSL context
     * @throws SSLException If the context cannot be built
     * @since 5.3.0
     */
    protected SslContext createSslContext(SslConfiguration ssl, HttpVersion httpVersion) throws SSLException {
        SslContextBuilder sslBuilder = SslContextBuilder
            .forServer(getKeyManagerFactory(ssl))
            .trustManager(getTrustManagerFactory(ssl));
        NettyTlsUtils.setupServerBuilder(sslBuilder, ssl, httpVersion);
        processBuilder(sslBuilder, ssl, httpVersion);
        return sslBuilder.build();
    }

    /**
     * Post-process the context builder. This is used by the ACME ALPN challenge. Note that this is
     * <i>not</i> called for QUIC, so it should only be used sparingly.
     *
     * @param sslBuilder  The ssl context builder to post-process
     * @param ssl         The ssl configuration
     * @param httpVersion The http version
     */
    protected void processBuilder(SslContextBuilder sslBuilder, SslConfiguration ssl, HttpVersion httpVersion) {
        // no additional processing by default
    }

    @Override
    public final Optional<QuicSslContext> buildQuic() {
        return buildQuic(null);
    }

    /**
     * Build the QUIC context, optionally selecting another context by the server name of the
     * client hello (SNI).
     *
     * @param sni The context for a server name, or {@code null} for this context; or {@code null}
     *            to serve this context to every client
     * @return The context
     * @since 5.3.0
     */
    @Internal
    public final Optional<QuicSslContext> buildQuic(@Nullable Function<String, @Nullable QuicSslContext> sni) {
        ServerSslConfiguration ssl = getSslConfiguration();
        QuicSslContextBuilder sslBuilder = QuicSslContextBuilder.forServer(getKeyManagerFactory(ssl), ssl.getKeyStore().getPassword().orElse(null))
            .trustManager(getTrustManagerFactory(ssl))
            .applicationProtocols(Http3.supportedApplicationProtocols());
        QuicTlsUtils.applyKeyExchangeGroups(sslBuilder, QuicTlsUtils.keyExchangeGroups(ssl));
        AtomicReference<@Nullable QuicSslContext> built = new AtomicReference<>();
        if (sni != null) {
            // a name without a context of its own is served the context built here
            sslBuilder.sni(name -> {
                QuicSslContext selected = sni.apply(name);
                return selected != null ? selected : Objects.requireNonNull(built.get());
            });
        }
        Optional<ClientAuthentication> clientAuthentication = ssl.getClientAuthentication();
        if (clientAuthentication.isPresent()) {
            ClientAuthentication clientAuth = clientAuthentication.get();
            if (clientAuth == ClientAuthentication.NEED) {
                sslBuilder.clientAuth(ClientAuth.REQUIRE);
            } else if (clientAuth == ClientAuthentication.WANT) {
                sslBuilder.clientAuth(ClientAuth.OPTIONAL);
            }
        }
        QuicSslContext context = sslBuilder.build();
        built.set(context);
        return Optional.of(context);
    }

    /**
     * The trust store of the key store based configuration ({@code micronaut.server.ssl.trust-store}),
     * which the contexts built from certificate providers use as well when no trust provider is
     * named, so that every context of a listener trusts the same client certificates.
     *
     * @return The trust store, or empty for the JVM default
     * @since 5.3.0
     */
    @Internal
    public final Optional<KeyStore> legacyTrustStore() {
        try {
            return getTrustStore(getSslConfiguration());
        } catch (SslConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }

    @Override
    protected KeyManagerFactory getKeyManagerFactory(SslConfiguration ssl) {
        try {
            return NettyTlsUtils.storeToFactory(ssl, getKeyStore(ssl).orElseThrow(() -> new SslConfigurationException("No key store configured")));
        } catch (SslConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }
}
