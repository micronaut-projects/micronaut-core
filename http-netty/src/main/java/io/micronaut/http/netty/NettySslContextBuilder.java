/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.netty;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.ssl.ClientAuthentication;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.OpenSslCachingX509KeyManagerFactory;
import io.netty.handler.ssl.OpenSslX509KeyManagerFactory;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.util.List;

/**
 * Builder for Netty {@link SslContext} (TCP/HTTP/1.1 and HTTP/2) and {@link QuicSslContext} (HTTP/3).
 * Consumes {@link java.security.KeyStore} and trust store material and applies Micronaut SSL configuration
 * such as ciphers, protocols, ALPN, client authentication, and provider selection (JDK vs OpenSSL).
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
public class NettySslContextBuilder {
    private final boolean server;

    @Nullable
    private KeyStore keyStore;
    @Nullable
    private String keyPassword;
    @Nullable
    private KeyStore trustStore;
    private boolean trustAll;

    private boolean openssl = false;
    @Nullable
    private List<String> protocols;
    @Nullable
    private List<String> ciphers;
    private boolean ignoreUnsupportedCiphers = false;
    @Nullable
    private List<String> alpnProtocols;
    @Nullable
    private ClientAuthentication clientAuthentication;

    /**
     * Create a builder for client or server mode.
     *
     * @param server whether to build server-side contexts (true) or client-side (false)
     */
    public NettySslContextBuilder(boolean server) {
        this.server = server;
    }

    /**
     * Select the underlying SSL provider.
     *
     * @param openssl true to prefer OpenSSL (via Netty), false for JDK provider
     * @return this builder
     */
    public final @NonNull NettySslContextBuilder openssl(boolean openssl) {
        this.openssl = openssl;
        return this;
    }

    /**
     * Whether OpenSSL has been requested.
     *
     * @return true if OpenSSL should be used
     */
    protected final boolean openssl() {
        return openssl;
    }

    /**
     * Current key store set on this builder.
     *
     * @return the key store or {@code null}
     */
    protected final @Nullable KeyStore keyStore() {
        return keyStore;
    }

    /**
     * Set the key store containing the private key and certificate chain (if any).
     *
     * @param keyStore the key store or {@code null}
     * @return this builder
     */
    public final @NonNull NettySslContextBuilder keyStore(@Nullable KeyStore keyStore) {
        this.keyStore = keyStore;
        return this;
    }

    /**
     * Key password currently configured.
     *
     * @return the password or {@code null}
     */
    protected final @Nullable String keyPassword() {
        return keyPassword;
    }

    /**
     * Set the password used to unlock the private key in the key store (if required).
     *
     * @param keyPassword the password or {@code null}
     * @return this builder
     */
    public final @NonNull NettySslContextBuilder keyPassword(@Nullable String keyPassword) {
        this.keyPassword = keyPassword;
        return this;
    }

    /**
     * Current trust store set on this builder.
     *
     * @return the trust store or {@code null}
     */
    protected final @Nullable KeyStore trustStore() {
        return trustStore;
    }

    /**
     * Set the trust store containing trusted certificates.
     *
     * @param trustStore the trust store or {@code null}
     * @return this builder
     */
    public final @NonNull NettySslContextBuilder trustStore(@Nullable KeyStore trustStore) {
        this.trustStore = trustStore;
        return this;
    }

    /**
     * Whether to trust all certificates instead of relying on the trust store.
     *
     * @return {@code true} to trust all certificates
     */
    protected final boolean trustAll() {
        return trustAll;
    }

    /**
     * Whether to trust all certificates instead of relying on the trust store.
     * <b>This is insecure, so handle with care.</b>
     *
     * @param trustAll {@code true} to trust all certificates
     * @return this builder
     */
    @NonNull
    public final NettySslContextBuilder trustAll(boolean trustAll) {
        this.trustAll = trustAll;
        return this;
    }

    /**
     * Enabled TLS protocols configured on this builder.
     *
     * @return list of protocol names or {@code null} for defaults
     */
    protected final @Nullable List<String> protocols() {
        return protocols;
    }

    /**
     * Set enabled TLS protocol names (e.g. TLSv1.3).
     *
     * @param protocols list of protocol names or {@code null} to use defaults
     * @return this builder
     */
    public final @NonNull NettySslContextBuilder protocols(@Nullable List<String> protocols) {
        this.protocols = protocols;
        return this;
    }

    /**
     * Cipher suites configured on this builder.
     *
     * @return list of ciphers or {@code null} for defaults
     */
    protected final @Nullable List<String> ciphers() {
        return ciphers;
    }

    /**
     * Set cipher suites.
     *
     * @param ciphers list of cipher names or {@code null} to use defaults
     * @param ignoreUnsupportedCiphers whether to ignore unsupported ciphers (true) or fail (false)
     * @return this builder
     */
    public final @NonNull NettySslContextBuilder ciphers(@Nullable List<String> ciphers, boolean ignoreUnsupportedCiphers) {
        this.ciphers = ciphers;
        this.ignoreUnsupportedCiphers = ignoreUnsupportedCiphers;
        return this;
    }

    /**
     * ALPN protocol names configured on this builder.
     *
     * @return list of protocol names or {@code null}
     */
    protected final @Nullable List<String> alpnProtocols() {
        return alpnProtocols;
    }

    /**
     * Set ALPN protocol names in preference order.
     *
     * @param alpnProtocols ALPN protocols (e.g. h2, http/1.1) or {@code null}
     * @return this builder
     */
    public final @NonNull NettySslContextBuilder alpnProtocols(@Nullable List<String> alpnProtocols) {
        this.alpnProtocols = alpnProtocols;
        return this;
    }

    /**
     * Convenience to enable HTTP/2 defaults (recommended ciphers and ALPN protocols).
     *
     * @return this builder
     */
    public final @NonNull NettySslContextBuilder http2() {
        if (ciphers == null) {
            ciphers(Http2SecurityUtil.CIPHERS, true);
        }
        if (alpnProtocols == null) {
            alpnProtocols(List.of(ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1));
        }
        return this;
    }

    /**
     * Client authentication policy currently configured.
     *
     * @return {@link io.micronaut.http.ssl.ClientAuthentication} or {@code null}
     */
    protected final @Nullable ClientAuthentication clientAuthentication() {
        return clientAuthentication;
    }

    /**
     * Set client authentication policy for mutual TLS.
     *
     * @param clientAuthentication NEED, WANT, or {@code null} for none
     * @return this builder
     */
    public final @NonNull NettySslContextBuilder clientAuthentication(@Nullable ClientAuthentication clientAuthentication) {
        this.clientAuthentication = clientAuthentication;
        return this;
    }

    /**
     * Create and initialize a {@link TrustManagerFactory} from the configured trust store.
     *
     * @return initialized trust manager factory
     */
    protected @NonNull TrustManagerFactory createTrustManagerFactory() throws Exception {
        if (trustAll) {
            if (trustStore != null) {
                throw new ConfigurationException("If you want to trust all certificates, please don't also specify a trust store");
            }
            return InsecureTrustManagerFactory.INSTANCE;
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory;
    }

    /**
     * Create and initialize a {@link KeyManagerFactory} from the configured key store.
     *
     * @return initialized key manager factory
     */
    protected @NonNull KeyManagerFactory createKeyManagerFactory() throws Exception {
        KeyManagerFactory keyManagerFactory;
        if (openssl && keyStore != null) {
            // I don't understand why, but netty uses this logic, so we will too.
            if (keyStore.aliases().hasMoreElements()) {
                keyManagerFactory = new OpenSslX509KeyManagerFactory();
            } else {
                keyManagerFactory = new OpenSslCachingX509KeyManagerFactory(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()));
            }
        } else {
            keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        }
        keyManagerFactory.init(keyStore, keyPassword == null ? null : keyPassword.toCharArray());
        return keyManagerFactory;
    }

    /**
     * Build a Netty {@link SslContext} for TCP-based protocols (HTTP/1.1, HTTP/2).
     *
     * @return the built SSL context
     */
    public @NonNull SslContext buildTcp() throws Exception {
        SslContextBuilder sslBuilder;
        if (server) {
            sslBuilder = SslContextBuilder.forServer(createKeyManagerFactory());
        } else {
            sslBuilder = SslContextBuilder.forClient().keyManager(createKeyManagerFactory());
        }
        sslBuilder.trustManager(createTrustManagerFactory());
        sslBuilder.sslProvider(openssl ? SslProvider.OPENSSL_REFCNT : SslProvider.JDK);

        if (protocols != null) {
            sslBuilder.protocols(protocols);
        }
        if (ciphers != null) {
            sslBuilder.ciphers(ciphers, ignoreUnsupportedCiphers ? SupportedCipherSuiteFilter.INSTANCE : IdentityCipherSuiteFilter.INSTANCE);
        }
        if (clientAuthentication == ClientAuthentication.NEED) {
            sslBuilder.clientAuth(ClientAuth.REQUIRE);
        } else if (clientAuthentication == ClientAuthentication.WANT) {
            sslBuilder.clientAuth(ClientAuth.OPTIONAL);
        }
        if (alpnProtocols != null) {
            sslBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                alpnProtocols
            ));
        }
        return sslBuilder.build();
    }

    /**
     * Build a Netty {@link QuicSslContext} for HTTP/3 over QUIC.
     *
     * @return the built QUIC SSL context
     */
    public @NonNull QuicSslContext buildHttp3() throws Exception {
        QuicSslContextBuilder sslBuilder;
        if (server) {
            sslBuilder = QuicSslContextBuilder.forServer(createKeyManagerFactory(), keyPassword);
        } else {
            sslBuilder = QuicSslContextBuilder.forClient().keyManager(createKeyManagerFactory(), keyPassword);
        }
        sslBuilder.trustManager(createTrustManagerFactory());
        sslBuilder.applicationProtocols(Http3.supportedApplicationProtocols());

        if (clientAuthentication == ClientAuthentication.NEED) {
            sslBuilder.clientAuth(ClientAuth.REQUIRE);
        } else if (clientAuthentication == ClientAuthentication.WANT) {
            sslBuilder.clientAuth(ClientAuth.OPTIONAL);
        }
        return sslBuilder.build();
    }
}
