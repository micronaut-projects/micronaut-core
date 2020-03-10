/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.ssl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.*;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;

import javax.inject.Singleton;
import javax.net.ssl.SSLException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Optional;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.core.util.StringUtils.TRUE;

/**
 * The Netty implementation of {@link SslBuilder} that generates an {@link SslContext} to create a server handle with
 * SSL support via user configuration.
 */
@Requires(property = SslConfiguration.PREFIX + ".enabled", value = TRUE, defaultValue = FALSE)
@Requires(property = SslConfiguration.PREFIX + ".build-self-signed", value = FALSE, defaultValue = FALSE)
@Singleton
@Internal
public class CertificateProvidedSslBuilder extends SslBuilder<SslContext> implements ServerSslBuilder {

    private final ServerSslConfiguration ssl;
    private final HttpServerConfiguration httpServerConfiguration;
    private KeyStore keyStoreCache = null;
    private KeyStore trustStoreCache = null;

    /**
     * @param httpServerConfiguration The HTTP server configuration
     * @param ssl                     The ssl configuration
     * @param resourceResolver        The resource resolver
     */
    public CertificateProvidedSslBuilder(
            HttpServerConfiguration httpServerConfiguration,
            ServerSslConfiguration ssl,
            ResourceResolver resourceResolver) {
        super(resourceResolver);
        this.ssl = ssl;
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public ServerSslConfiguration getSslConfiguration() {
        return ssl;
    }

    @Override
    public Optional<SslContext> build() {
        return build(ssl);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public Optional<SslContext> build(SslConfiguration ssl) {
        final HttpVersion httpVersion = httpServerConfiguration.getHttpVersion();
        return build(ssl, httpVersion);
    }

    @Override
    public Optional<SslContext> build(SslConfiguration ssl, HttpVersion httpVersion) {
        SslContextBuilder sslBuilder = SslContextBuilder
                .forServer(getKeyManagerFactory(ssl))
                .trustManager(getTrustManagerFactory(ssl));

        if (ssl.getProtocols().isPresent()) {
            sslBuilder.protocols(ssl.getProtocols().get());
        }
        final boolean isHttp2 = httpVersion == HttpVersion.HTTP_2_0;
        if (ssl.getCiphers().isPresent()) {
            sslBuilder = sslBuilder.ciphers(Arrays.asList(ssl.getCiphers().get()));
        } else if (isHttp2) {
            sslBuilder.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE);
        }
        if (ssl.getClientAuthentication().isPresent()) {
            ClientAuthentication clientAuth = ssl.getClientAuthentication().get();
            if (clientAuth == ClientAuthentication.NEED) {
                sslBuilder = sslBuilder.clientAuth(ClientAuth.REQUIRE);
            } else if (clientAuth == ClientAuthentication.WANT) {
                sslBuilder = sslBuilder.clientAuth(ClientAuth.OPTIONAL);
            }
        }

        if (isHttp2) {
            SslProvider provider = SslProvider.isAlpnSupported(SslProvider.OPENSSL) ? SslProvider.OPENSSL : SslProvider.JDK;
            sslBuilder.sslProvider(provider);
            sslBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_1_1,
                    ApplicationProtocolNames.HTTP_2
            ));
        }
        try {
            return Optional.of(sslBuilder.build());
        } catch (SSLException ex) {
            throw new SslConfigurationException("An error occurred while setting up SSL", ex);
        }
    }

    @Override
    protected Optional<KeyStore> getTrustStore(SslConfiguration ssl) throws Exception {
        if (trustStoreCache == null) {
            super.getTrustStore(ssl).ifPresent(trustStore -> trustStoreCache = trustStore);
        }
        return Optional.ofNullable(trustStoreCache);
    }

    @Override
    protected Optional<KeyStore> getKeyStore(SslConfiguration ssl) throws Exception {
        if (keyStoreCache == null) {
            super.getKeyStore(ssl).ifPresent(keyStore -> keyStoreCache = keyStore);
        }
        return Optional.ofNullable(keyStoreCache);
    }
}
