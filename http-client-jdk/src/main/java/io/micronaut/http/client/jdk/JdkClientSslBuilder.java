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
package io.micronaut.http.client.jdk;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.http.ssl.SslConfigurationException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * The Javanet implementation of {@link SslBuilder} that generates an {@link SSLContext} to create a client that
 * supports SSL.
 *
 * @author Tim Yates
 * @since 4.0.0
 */
@Singleton
@Internal
@Experimental
@BootstrapContextCompatible
public final class JdkClientSslBuilder extends SslBuilder<SSLContext> {

    private static final Logger LOG = LoggerFactory.getLogger(JdkClientSslBuilder.class);

    /**
     * @param resourceResolver The resource resolver
     */
    public JdkClientSslBuilder(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    public Optional<SSLContext> build(SslConfiguration ssl) {
        return build(ssl, HttpVersion.HTTP_1_1);
    }

    @Override
    public Optional<SSLContext> build(SslConfiguration ssl, HttpVersion httpVersion) {
        return Optional.ofNullable(build(ssl, HttpVersionSelection.forLegacyVersion(httpVersion)));
    }

    @Nullable
    public SSLContext build(SslConfiguration ssl, HttpVersionSelection versionSelection) {
        if (!ssl.isEnabled()) {
            return null;
        }
        TrustManagerFactory trustManagerFactory = getTrustManagerFactory(ssl);
        KeyManagerFactory keyManagerFactory = getKeyManagerFactory(ssl);
        try {
            SSLContext tls = SSLContext.getInstance(ssl.getProtocol().orElse("TLS"));
            if (trustManagerFactory == null) {
                trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            }
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (ssl instanceof ClientSslConfiguration clientSslConfiguration && clientSslConfiguration.isInsecureTrustAllCertificates()) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Trust all certificates is enabled. This is insecure and should not be used in production");
                }
                trustManagers = new TrustManager[] { new TrustAllTrustManager() };
            }
            tls.init(keyManagerFactory.getKeyManagers(), trustManagers, null);
            return tls;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new SslConfigurationException("Error initializing SSL context: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("java:S4830") // This is explicitly to turn security off when isInsecureTrustAllCertificates
    private static class TrustAllTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // trust everything
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // trust everything
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
