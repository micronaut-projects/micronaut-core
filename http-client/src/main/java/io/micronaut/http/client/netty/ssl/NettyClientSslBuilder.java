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
package io.micronaut.http.client.netty.ssl;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.netty.NettyTlsUtils;
import io.micronaut.http.ssl.AbstractClientSslConfiguration;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.http.ssl.SslConfigurationException;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Optional;

/**
 * The Netty implementation of {@link SslBuilder} that generates an {@link SslContext} to create a client that
 * supports SSL.<br>
 * This class is not final, so you can extend and replace it to implement alternate mechanisms for loading the
 * key and trust stores.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@BootstrapContextCompatible
@Secondary
public class NettyClientSslBuilder extends SslBuilder<SslContext> implements ClientSslBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(NettyClientSslBuilder.class);

    /**
     * @param resourceResolver The resource resolver
     */
    public NettyClientSslBuilder(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public final Optional<SslContext> build(SslConfiguration ssl) {
        return build(ssl, HttpVersion.HTTP_1_1);
    }

    @Override
    public final Optional<SslContext> build(SslConfiguration ssl, HttpVersion httpVersion) {
        if (!ssl.isEnabled()) {
            return Optional.empty();
        }
        return Optional.of(build(ssl, HttpVersionSelection.forLegacyVersion(httpVersion)));
    }

    @NonNull
    @Override
    public final SslContext build(SslConfiguration ssl, HttpVersionSelection versionSelection) {
        SslContextBuilder sslBuilder = SslContextBuilder
            .forClient()
            .keyManager(getKeyManagerFactory(ssl))
            .trustManager(getTrustManagerFactory(ssl))
            .sslProvider(NettyTlsUtils.sslProvider(ssl));
        Optional<String[]> protocols = ssl.getProtocols();
        if (protocols.isPresent()) {
            sslBuilder.protocols(protocols.get());
        }
        Optional<String[]> ciphers = ssl.getCiphers();
        if (ciphers.isPresent()) {
            sslBuilder = sslBuilder.ciphers(Arrays.asList(ciphers.get()));
        } else if (versionSelection.isHttp2CipherSuites()) {
            sslBuilder.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE);
        }
        Optional<ClientAuthentication> clientAuthentication = ssl.getClientAuthentication();
        if (clientAuthentication.isPresent()) {
            ClientAuthentication clientAuth = clientAuthentication.get();
            if (clientAuth == ClientAuthentication.NEED) {
                sslBuilder = sslBuilder.clientAuth(ClientAuth.REQUIRE);
            } else if (clientAuth == ClientAuthentication.WANT) {
                sslBuilder = sslBuilder.clientAuth(ClientAuth.OPTIONAL);
            }
        }
        if (versionSelection.isAlpn()) {
            SslProvider provider = SslProvider.isAlpnSupported(SslProvider.OPENSSL) ? SslProvider.OPENSSL : SslProvider.JDK;
            sslBuilder.sslProvider(provider);
            sslBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                versionSelection.getAlpnSupportedProtocols()
            ));
        }

        try {
            return sslBuilder.build();
        } catch (SSLException ex) {
            throw new SslConfigurationException("An error occurred while setting up SSL", ex);
        }
    }

    @Override
    public final QuicSslContext buildHttp3(SslConfiguration ssl) {
        QuicSslContextBuilder sslBuilder = QuicSslContextBuilder.forClient()
            .keyManager(getKeyManagerFactory(ssl), ssl.getKeyStore().getPassword().orElse(null))
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

        return sslBuilder.build();
    }

    @Override
    protected KeyManagerFactory getKeyManagerFactory(SslConfiguration ssl) {
        try {
            Optional<KeyStore> ks = this.getKeyStore(ssl);
            if (ks.isPresent()) {
                return NettyTlsUtils.storeToFactory(ssl, ks.orElse(null));
            } else {
                return null;
            }
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }

    @Override
    protected TrustManagerFactory getTrustManagerFactory(SslConfiguration ssl) {
        try {
            Optional<KeyStore> trustStore = getTrustStore(ssl);
            if (trustStore.isPresent()) {
                return super.getTrustManagerFactory(trustStore.get());
            } else {
                if (ssl instanceof AbstractClientSslConfiguration configuration && configuration.isInsecureTrustAllCertificates()) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("HTTP Client is configured to trust all certificates ('insecure-trust-all-certificates' is set to true). Trusting all certificates is not secure and should not be used in production.");
                    }
                    return InsecureTrustManagerFactory.INSTANCE;
                } else {
                    // netty will use the JDK trust store
                    return null;
                }
            }
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }
}
