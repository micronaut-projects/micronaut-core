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
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.http.ssl.SslConfigurationException;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.inject.Singleton;
import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;
import java.util.Optional;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.core.util.StringUtils.TRUE;

/**
 * The Netty implementation of {@link SslBuilder} that generates an {@link SslContext} to create a server handler
 * with to SSL support via a generated self signed certificate.
 */
@Requires(property = SslConfiguration.PREFIX + ".enabled", value = TRUE, defaultValue = FALSE)
@Requires(property = SslConfiguration.PREFIX + ".build-self-signed", value = TRUE, defaultValue = FALSE)
@Singleton
@Internal
public class SelfSignedSslBuilder extends SslBuilder<SslContext> implements ServerSslBuilder {

    private final ServerSslConfiguration ssl;

    /**
     * @param ssl              The SSL configuration
     * @param resourceResolver The resource resolver
     */
    public SelfSignedSslBuilder(ServerSslConfiguration ssl, ResourceResolver resourceResolver) {
        super(resourceResolver);
        this.ssl = ssl;
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
        try {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            return Optional.of(SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build());
        } catch (CertificateException | SSLException e) {
            throw new SslConfigurationException("Encountered an error while building a self signed certificate", e);
        }
    }
}
