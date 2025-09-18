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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.util.List;

public class NettySslContextBuilder {
    private final boolean server;

    @Nullable
    private KeyStore keyStore;
    @Nullable
    private String keyPassword;
    @Nullable
    private KeyStore trustStore;

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

    public NettySslContextBuilder(boolean server) {
        this.server = server;
    }

    public final @NonNull NettySslContextBuilder openssl(boolean openssl) {
        this.openssl = openssl;
        return this;
    }

    protected final boolean openssl() {
        return openssl;
    }

    protected final @Nullable KeyStore keyStore() {
        return keyStore;
    }

    public final @NonNull NettySslContextBuilder keyStore(@Nullable KeyStore keyStore) {
        this.keyStore = keyStore;
        return this;
    }

    protected final @Nullable String keyPassword() {
        return keyPassword;
    }

    public final @NonNull NettySslContextBuilder keyPassword(@Nullable String keyPassword) {
        this.keyPassword = keyPassword;
        return this;
    }

    protected final @Nullable KeyStore trustStore() {
        return trustStore;
    }

    public final @NonNull NettySslContextBuilder trustStore(@Nullable KeyStore trustStore) {
        this.trustStore = trustStore;
        return this;
    }

    protected final @Nullable List<String> protocols() {
        return protocols;
    }

    public final @NonNull NettySslContextBuilder protocols(@Nullable List<String> protocols) {
        this.protocols = protocols;
        return this;
    }

    protected final @Nullable List<String> ciphers() {
        return ciphers;
    }

    public final @NonNull NettySslContextBuilder ciphers(@Nullable List<String> ciphers, boolean ignoreUnsupportedCiphers) {
        this.ciphers = ciphers;
        this.ignoreUnsupportedCiphers = ignoreUnsupportedCiphers;
        return this;
    }

    protected final @Nullable List<String> alpnProtocols() {
        return alpnProtocols;
    }

    public final @NonNull NettySslContextBuilder alpnProtocols(@Nullable List<String> alpnProtocols) {
        this.alpnProtocols = alpnProtocols;
        return this;
    }

    public final @NonNull NettySslContextBuilder http2() {
        if (ciphers == null) {
            ciphers(Http2SecurityUtil.CIPHERS, true);
        }
        if (alpnProtocols == null) {
            alpnProtocols(List.of(ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1));
        }
        return this;
    }

    protected final @Nullable ClientAuthentication clientAuthentication() {
        return clientAuthentication;
    }

    public final @NonNull NettySslContextBuilder clientAuthentication(@Nullable ClientAuthentication clientAuthentication) {
        this.clientAuthentication = clientAuthentication;
        return this;
    }

    protected @NonNull TrustManagerFactory createTrustManagerFactory() throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory;
    }

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
