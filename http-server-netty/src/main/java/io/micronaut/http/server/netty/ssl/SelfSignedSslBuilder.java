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
package io.micronaut.http.server.netty.ssl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.http.ssl.SslConfigurationException;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;
import java.util.Optional;

/**
 * The Netty implementation of {@link SslBuilder} that generates an {@link SslContext} to create a server handler
 * with SSL support via a generated self signed certificate.
 */
@Requires(condition = SslEnabledCondition.class)
@Requires(condition = SelfSignedSslBuilder.SelfSignedConfigured.class)
@Singleton
@Internal
public class SelfSignedSslBuilder extends SslBuilder<SslContext> implements ServerSslBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(SelfSignedSslBuilder.class);
    private final ServerSslConfiguration ssl;
    private final HttpServerConfiguration serverConfiguration;

    /**
     * @param serverConfiguration The server configuration
     * @param ssl                 The SSL configuration
     * @param resourceResolver    The resource resolver
     */
    public SelfSignedSslBuilder(
            HttpServerConfiguration serverConfiguration,
            ServerSslConfiguration ssl,
            ResourceResolver resourceResolver) {
        super(resourceResolver);
        this.ssl = ssl;
        this.serverConfiguration = serverConfiguration;
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
        final HttpVersion httpVersion = serverConfiguration.getHttpVersion();
        return build(ssl, httpVersion);
    }

    @Override
    public Optional<SslContext> build(SslConfiguration ssl, HttpVersion httpVersion) {
        try {
            if (LOG.isWarnEnabled()) {
                LOG.warn("HTTP Server is configured to use a self-signed certificate ('build-self-signed' is set to true). This configuration should not be used in a production environment as self-signed certificates are inherently insecure.");
            }
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            final SslContextBuilder sslBuilder = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .trustManager(getTrustManagerFactory(ssl));
            CertificateProvidedSslBuilder.setupSslBuilder(sslBuilder, ssl, httpVersion);
            return Optional.of(sslBuilder.build());
        } catch (CertificateException | SSLException e) {
            throw new SslConfigurationException("Encountered an error while building a self signed certificate", e);
        }
    }

    @Override
    public Optional<QuicSslContext> buildQuic() {
        SelfSignedCertificate ssc;
        try {
            ssc = new SelfSignedCertificate();
        } catch (CertificateException e) {
            throw new SslConfigurationException("Encountered an error while building a self signed certificate", e);
        }
        return Optional.of(QuicSslContextBuilder.forServer(ssc.privateKey(), null, ssc.certificate())
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build());
    }

    static class SelfSignedConfigured extends BuildSelfSignedCondition {
        @Override
        protected boolean validate(ConditionContext context, boolean deprecatedPropertyFound, boolean newPropertyFound) {
            if (!deprecatedPropertyFound && !newPropertyFound) {
                context.fail("Neither the old deprecated " + SslConfiguration.PREFIX + ".build-self-signed, nor the new " + ServerSslConfiguration.PREFIX + ".build-self-signed were enabled.");
                return false;
            } else {
                return true;
            }
        }
    }
}
