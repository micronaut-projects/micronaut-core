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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.ssl.KeyExchangeGroups;
import io.micronaut.http.ssl.SslConfiguration;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.OpenSslCachingX509KeyManagerFactory;
import io.netty.handler.ssl.OpenSslContextOption;
import io.netty.handler.ssl.OpenSslX509KeyManagerFactory;
import io.netty.handler.ssl.ReferenceCountedOpenSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.util.ReferenceCountUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Common utilities for netty TLS support.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public final class NettyTlsUtils {
    private static final Map<String, Boolean> OPENSSL_GROUP_SUPPORT = new ConcurrentHashMap<>();

    public static boolean useOpenssl(SslConfiguration sslConfiguration) {
        return sslConfiguration.isPreferOpenssl() && SslProvider.isAlpnSupported(SslProvider.OPENSSL_REFCNT);
    }

    /**
     * The SSL provider to use.
     *
     * @param sslConfiguration The ssl configuration
     *
     * @return The provider
     */
    public static SslProvider sslProvider(SslConfiguration sslConfiguration) {
        return useOpenssl(sslConfiguration) ? SslProvider.OPENSSL_REFCNT : SslProvider.JDK;
    }

    /**
     * Create a {@link KeyManagerFactory} from a {@link KeyStore}. This is basically like
     * {@link io.micronaut.http.ssl.SslBuilder#getKeyManagerFactory(SslConfiguration)}, except it
     * uses factories optimized for netty openssl if possible.
     *
     * @param ssl The ssl configuration
     * @param keyStore The key store, i.e. the return value of
     * {@link io.micronaut.http.ssl.SslBuilder#getKeyStore(SslConfiguration)}
     * @return The {@link KeyManagerFactory} containing the key store
     */
    public static KeyManagerFactory storeToFactory(SslConfiguration ssl, @Nullable KeyStore keyStore) throws Exception {
        KeyManagerFactory keyManagerFactory;
        if (useOpenssl(ssl)) {
            // I don't understand why, but netty uses this logic, so we will too.
            if (keyStore == null || keyStore.aliases().hasMoreElements()) {
                keyManagerFactory = new OpenSslX509KeyManagerFactory();
            } else {
                keyManagerFactory = new OpenSslCachingX509KeyManagerFactory(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()));
            }
        } else {
            keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        }
        Optional<String> password = ssl.getKey().getPassword();
        char[] keyPassword = password.map(String::toCharArray).orElse(null);
        Optional<String> pwd = ssl.getKeyStore().getPassword();
        if (keyPassword == null && pwd.isPresent()) {
            keyPassword = pwd.get().toCharArray();
        }
        if (keyStore != null && ssl.getKey().getAlias().isPresent()) {
            keyStore = extractKeystoreAlias(keyStore, ssl.getKey().getAlias().get(), keyPassword);
        }
        keyManagerFactory.init(keyStore, keyPassword);
        return keyManagerFactory;
    }

    /**
     * Creates a new {@link KeyStore} from the original containing only the selected alias.
     *
     * @param rootKeystore The original keystore
     * @param alias The selected alias
     * @param password Password of Alias
     * @return {@link KeyStore} containing only the selected alias
     */
    private static KeyStore extractKeystoreAlias(KeyStore rootKeystore, String alias, char @Nullable [] password) throws Exception {
        if (!rootKeystore.containsAlias(alias)) {
            throw new IllegalArgumentException("Alias " + alias + " not found in keystore");
        }
        Key key = rootKeystore.getKey(alias, password);
        if (key == null) {
            throw new IllegalStateException("There are no keys associated with the alias " + alias);
        }
        Certificate[] certChain = rootKeystore.getCertificateChain(alias);
        KeyStore aliasKeystore = KeyStore.getInstance(rootKeystore.getType());
        aliasKeystore.load(null, null);
        aliasKeystore.setKeyEntry(alias, key, password, certChain);
        return aliasKeystore;
    }

    /**
     * Resolve the configured TLS key exchange groups and check that the selected engine supports them.
     *
     * @param ssl The SSL configuration
     * @param openssl Whether the OpenSSL engine is used, otherwise the JDK engine of
     * {@link SslConfiguration#getSslContext()} or the default {@link SSLContext}
     * @return The groups to apply, or {@code null} to keep the engine defaults
     * @throws io.micronaut.http.ssl.SslConfigurationException If the groups are invalid or unsupported
     * @since 5.3.0
     */
    public static @Nullable List<String> keyExchangeGroups(SslConfiguration ssl, boolean openssl) {
        if (!KeyExchangeGroups.isConfigured(ssl)) {
            return null;
        }
        if (openssl) {
            return KeyExchangeGroups.resolve(ssl, NettyTlsUtils::opensslGroupSupported, "OpenSSL");
        }
        return KeyExchangeGroups.resolve(ssl, KeyExchangeGroups.jdkSupport(ssl.getSslContext()), "JDK");
    }

    /**
     * Configure the key exchange groups on an OpenSSL context builder. Contexts of the JDK engine
     * receive the groups through {@link #withKeyExchangeGroups(SslContext, List)} instead.
     *
     * @param sslBuilder The builder
     * @param groups The groups from {@link #keyExchangeGroups(SslConfiguration, boolean)}
     * @param openssl Whether the builder uses the OpenSSL engine
     * @since 5.3.0
     */
    public static void applyKeyExchangeGroups(SslContextBuilder sslBuilder, @Nullable List<String> groups, boolean openssl) {
        if (groups != null && openssl) {
            sslBuilder.option(OpenSslContextOption.GROUPS, groups.toArray(String[]::new));
        }
    }

    /**
     * Apply the key exchange groups to every engine of a JDK engine context. OpenSSL contexts are
     * returned unchanged, they are configured by {@link #applyKeyExchangeGroups}.
     *
     * @param context The context
     * @param groups The groups from {@link #keyExchangeGroups(SslConfiguration, boolean)}
     * @return The context applying the groups
     * @since 5.3.0
     */
    public static SslContext withKeyExchangeGroups(SslContext context, @Nullable List<String> groups) {
        if (groups == null || context instanceof ReferenceCountedOpenSslContext) {
            return context;
        }
        String[] names = groups.toArray(String[]::new);
        return new DelegatingSslContext(context) {
            @Override
            protected void initEngine(SSLEngine engine) {
                SSLParameters parameters = engine.getSSLParameters();
                parameters.setNamedGroups(names);
                engine.setSSLParameters(parameters);
            }
        };
    }

    /**
     * Apply the configured key exchange groups to a built context. OpenSSL contexts are returned
     * unchanged, they receive the groups from their builder. Other contexts are JDK engine
     * contexts: the groups are validated against that engine and applied to each of its engines.
     *
     * @param context The built context
     * @param ssl The SSL configuration
     * @return The context applying the groups
     * @since 5.3.0
     */
    public static SslContext withKeyExchangeGroups(SslContext context, SslConfiguration ssl) {
        if (context instanceof ReferenceCountedOpenSslContext || !KeyExchangeGroups.isConfigured(ssl)) {
            return context;
        }
        SSLContext jdkContext = context instanceof JdkSslContext jdk ? jdk.context() : ssl.getSslContext();
        return withKeyExchangeGroups(context, KeyExchangeGroups.resolve(ssl, KeyExchangeGroups.jdkSupport(jdkContext), "JDK"));
    }

    private static boolean opensslGroupSupported(String group) {
        return OPENSSL_GROUP_SUPPORT.computeIfAbsent(group, NettyTlsUtils::probeOpensslGroup);
    }

    private static boolean probeOpensslGroup(String group) {
        SslContext context = null;
        try {
            context = SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL_REFCNT)
                .option(OpenSslContextOption.GROUPS, new String[]{group})
                .build();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            ReferenceCountUtil.release(context);
        }
    }

    public static void setupServerBuilder(SslContextBuilder sslBuilder, SslConfiguration ssl, HttpVersion httpVersion) {
        sslBuilder.sslProvider(sslProvider(ssl));
        applyKeyExchangeGroups(sslBuilder, keyExchangeGroups(ssl, useOpenssl(ssl)), useOpenssl(ssl));
        Optional<String[]> protocols = ssl.getProtocols();
        if (protocols.isPresent()) {
            sslBuilder.protocols(protocols.get());
        }
        final boolean isHttp2 = httpVersion == HttpVersion.HTTP_2_0;
        Optional<String[]> ciphers = ssl.getCiphers();
        if (ciphers.isPresent()) {
            sslBuilder = sslBuilder.ciphers(Arrays.asList(ciphers.get()));
        } else if (isHttp2) {
            sslBuilder.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE);
        }
        Optional<ClientAuthentication> clientAuthentication = ssl.getClientAuthentication();
        if (clientAuthentication.isPresent()) {
            ClientAuthentication clientAuth = clientAuthentication.get();
            if (clientAuth == ClientAuthentication.NEED) {
                sslBuilder.clientAuth(ClientAuth.REQUIRE);
            } else if (clientAuth == ClientAuthentication.WANT) {
                sslBuilder.clientAuth(ClientAuth.OPTIONAL);
            }
        }

        if (isHttp2) {
            sslBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1
            ));
        }
    }
}
