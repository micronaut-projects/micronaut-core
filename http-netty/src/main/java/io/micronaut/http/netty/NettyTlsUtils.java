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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.ssl.SslConfiguration;
import io.netty.handler.ssl.OpenSslCachingX509KeyManagerFactory;
import io.netty.handler.ssl.OpenSslX509KeyManagerFactory;
import io.netty.handler.ssl.SslProvider;

import javax.net.ssl.KeyManagerFactory;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Optional;

/**
 * Common utilities for netty TLS support.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public final class NettyTlsUtils {
    private static boolean useOpenssl(SslConfiguration sslConfiguration) {
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
    @NonNull
    public static KeyManagerFactory storeToFactory(@NonNull SslConfiguration ssl, @Nullable KeyStore keyStore) throws Exception {
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
    @NonNull
    private static KeyStore extractKeystoreAlias(@NonNull KeyStore rootKeystore, @NonNull String alias, @Nullable char[] password) throws Exception {
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
}
