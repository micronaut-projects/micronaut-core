/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.client.ssl;

import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.ssl.*;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
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
public class NettyClientSslBuilder extends SslBuilder<SslContext> {

    /**
     * @param ssl              The SSL configuration
     * @param resourceResolver The resource resolver
     */
    @Inject
    public NettyClientSslBuilder(ClientSslConfiguration ssl, ResourceResolver resourceResolver) {
        super(ssl, resourceResolver);
    }

    /**
     * @param ssl The SSL configuration
     */
    public NettyClientSslBuilder(SslConfiguration ssl/*, ResourceResolver resourceResolver*/) {
        super(ssl, new ResourceResolver());
    }


    /**
     * @return The SSL configuration
     */
    public SslConfiguration getSslConfiguration() {
        return ssl;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public Optional<SslContext> build() {
        if (!ssl.isEnabled()) {
            return Optional.empty();
        }
        SslContextBuilder sslBuilder = SslContextBuilder
            .forClient()
            .keyManager(getKeyManagerFactory())
            .trustManager(getTrustManagerFactory());
        if (ssl.getProtocols().isPresent()) {
            sslBuilder.protocols(ssl.getProtocols().get());
        }
        if (ssl.getCiphers().isPresent()) {
            sslBuilder = sslBuilder.ciphers(Arrays.asList(ssl.getCiphers().get()));
        }
        if (ssl.getClientAuthentication().isPresent()) {
            ClientAuthentication clientAuth = ssl.getClientAuthentication().get();
            if (clientAuth == ClientAuthentication.NEED) {
                sslBuilder = sslBuilder.clientAuth(ClientAuth.REQUIRE);
            } else if (clientAuth == ClientAuthentication.WANT) {
                sslBuilder = sslBuilder.clientAuth(ClientAuth.OPTIONAL);
            }
        }

        try {
            return Optional.of(sslBuilder.build());
        } catch (SSLException ex) {
            throw new SslConfigurationException("An error occurred while setting up SSL", ex);
        }
    }

    @Override
    protected KeyManagerFactory getKeyManagerFactory() {
        try {
            if (this.getKeyStore().isPresent()) {
                return super.getKeyManagerFactory();
            } else {
                return null;
            }
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }

    @Override
    protected TrustManagerFactory getTrustManagerFactory() {
        try {
            if (this.getTrustStore().isPresent()) {
                return super.getTrustManagerFactory();
            } else {
                return InsecureTrustManagerFactory.INSTANCE;
            }
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }
}
