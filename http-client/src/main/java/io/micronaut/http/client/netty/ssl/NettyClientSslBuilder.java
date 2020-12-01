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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.ssl.*;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.util.Arrays;
import java.util.Optional;

/**
 * The Netty implementation of {@link SslBuilder} that generates an {@link SslContext} to create a client that
 * supports SSL.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Internal
@BootstrapContextCompatible
public class NettyClientSslBuilder extends SslBuilder<SslContext> {

    /**
     * @param resourceResolver The resource resolver
     */
    public NettyClientSslBuilder(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public Optional<SslContext> build(SslConfiguration ssl) {
        return build(ssl, HttpVersion.HTTP_1_1);
    }

    @Override
    public Optional<SslContext> build(SslConfiguration ssl, HttpVersion httpVersion) {
        if (!ssl.isEnabled()) {
            return Optional.empty();
        }
        final boolean isHttp2 = httpVersion == HttpVersion.HTTP_2_0;
        SslContextBuilder sslBuilder = SslContextBuilder
                .forClient()
                .keyManager(getKeyManagerFactory(ssl))
                .trustManager(getTrustManagerFactory(ssl));
        if (ssl.getProtocols().isPresent()) {
            sslBuilder.protocols(ssl.getProtocols().get());
        }
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
    protected KeyManagerFactory getKeyManagerFactory(SslConfiguration ssl) {
        try {
            if (this.getKeyStore(ssl).isPresent()) {
                return super.getKeyManagerFactory(ssl);
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
            if (this.getTrustStore(ssl).isPresent()) {
                return super.getTrustManagerFactory(ssl);
            } else {
                return InsecureTrustManagerFactory.INSTANCE;
            }
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }
}
