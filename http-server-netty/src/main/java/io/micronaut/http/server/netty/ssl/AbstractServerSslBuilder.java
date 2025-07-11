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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.netty.NettyTlsUtils;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.http.ssl.SslConfigurationException;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import java.util.Arrays;
import java.util.Optional;

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
        SslContextBuilder sslBuilder = SslContextBuilder
            .forServer(getKeyManagerFactory(ssl))
            .trustManager(getTrustManagerFactory(ssl));

        setupSslBuilder(sslBuilder, ssl, httpVersion);
        processBuilder(sslBuilder, ssl, httpVersion);
        try {
            return Optional.of(sslBuilder.build());
        } catch (SSLException ex) {
            throw new SslConfigurationException("An error occurred while setting up SSL", ex);
        }
    }

    /**
     * Post-process the context builder. This is used by the ACME ALPN challenge. Note that this is
     * <i>not</i> called for QUIC, so it should only be used sparingly.
     *
     * @param sslBuilder  The ssl context builder to post-process
     * @param ssl         The ssl configuration
     * @param httpVersion The http version
     */
    protected void processBuilder(@NonNull SslContextBuilder sslBuilder, @NonNull SslConfiguration ssl, @NonNull HttpVersion httpVersion) {
        // no additional processing by default
    }

    private static void setupSslBuilder(SslContextBuilder sslBuilder, SslConfiguration ssl, HttpVersion httpVersion) {
        sslBuilder.sslProvider(NettyTlsUtils.sslProvider(ssl));
        Optional<String[]> protocols = ssl.getProtocols();
        if (protocols.isPresent()) {
            sslBuilder.protocols(protocols.get());
        }
        final boolean isHttp2 = httpVersion == HttpVersion.HTTP_2_0;
        Optional<String[]> ciphers = ssl.getCiphers();
        if (ciphers.isPresent()) {
            sslBuilder = sslBuilder.ciphers(Arrays.asList(ciphers.get()));
        } else if (isHttp2) {
            sslBuilder.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE);
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

        if (isHttp2) {
            sslBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1
            ));
        }
    }

    @Override
    public final Optional<QuicSslContext> buildQuic() {
        ServerSslConfiguration ssl = getSslConfiguration();
        QuicSslContextBuilder sslBuilder = QuicSslContextBuilder.forServer(getKeyManagerFactory(ssl), ssl.getKeyStore().getPassword().orElse(null))
            .trustManager(getTrustManagerFactory(ssl))
            .applicationProtocols(Http3.supportedApplicationProtocols());
        Optional<ClientAuthentication> clientAuthentication = ssl.getClientAuthentication();
        if (clientAuthentication.isPresent()) {
            ClientAuthentication clientAuth = clientAuthentication.get();
            if (clientAuth == ClientAuthentication.NEED) {
                sslBuilder.clientAuth(ClientAuth.REQUIRE);
            } else if (clientAuth == ClientAuthentication.WANT) {
                sslBuilder.clientAuth(ClientAuth.OPTIONAL);
            }
        }
        return Optional.of(sslBuilder.build());
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
