/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.http.ssl.*;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.inject.Singleton;
import javax.net.ssl.SSLException;
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

    /**
     * @param ssl              The SSL configuration
     * @param resourceResolver The resource resolver
     */
    public CertificateProvidedSslBuilder(ServerSslConfiguration ssl, ResourceResolver resourceResolver) {
        super(ssl, resourceResolver);
    }

    /**
     * @return The SSL configuration
     */
    @Override
    public ServerSslConfiguration getSslConfiguration() {
        return (ServerSslConfiguration) ssl;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public Optional<SslContext> build() {
        SslContextBuilder sslBuilder = SslContextBuilder
            .forServer(getKeyManagerFactory())
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
}
