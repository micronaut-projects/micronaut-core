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
package io.micronaut.http.ssl;

import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpVersion;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.URL;
import java.security.KeyStore;
import java.util.Optional;

/**
 * A class to build a key store and a trust store for use in adding SSL support to a server.
 *
 * @param <T> The server specific type to be built
 * @author James Kleeh
 * @since 1.0
 */
public abstract class SslBuilder<T> {

    private final ResourceResolver resourceResolver;

    /**
     * @param resourceResolver The resource resolver
     */
    public SslBuilder(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    /**
     * @param ssl The ssl configuration
     *
     * @return Builds the SSL configuration wrapped inside an optional
     */
    public abstract Optional<T> build(SslConfiguration ssl);

    /**
     * @param ssl The ssl configuration
     * @param httpVersion  The http version
     * @return Builds the SSL configuration wrapped inside an optional
     */
    public abstract Optional<T> build(SslConfiguration ssl, HttpVersion httpVersion);

    /**
     * @param ssl The ssl configuration
     *
     * @return The {@link TrustManagerFactory}
     */
    protected TrustManagerFactory getTrustManagerFactory(SslConfiguration ssl) {
        Optional<KeyStore> store;
        try {
            store = getTrustStore(ssl);
        } catch (Exception e) {
            throw new SslConfigurationException(e);
        }
        return getTrustManagerFactory(store.orElse(null));
    }

    /**
     * @param store The key store
     *
     * @return The {@link TrustManagerFactory}
     */
    protected TrustManagerFactory getTrustManagerFactory(KeyStore store) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(store);
            return trustManagerFactory;
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }

    /**
     * @param ssl The ssl configuration
     *
     * @return An optional {@link KeyStore}
     * @throws Exception if there is an error
     */
    protected Optional<KeyStore> getTrustStore(SslConfiguration ssl) throws Exception {
        SslConfiguration.TrustStoreConfiguration trustStore = ssl.getTrustStore();
        Optional<String> path = trustStore.getPath();
        if (path.isPresent()) {
            return Optional.of(load(trustStore.getType(),
                path.get(), trustStore.getPassword()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * @param ssl The ssl configuration
     *
     * @return The {@link KeyManagerFactory}
     */
    protected KeyManagerFactory getKeyManagerFactory(SslConfiguration ssl) {
        try {
            Optional<KeyStore> keyStore = getKeyStore(ssl);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            Optional<String> password = ssl.getKey().getPassword();
            char[] keyPassword = password.map(String::toCharArray).orElse(null);
            Optional<String> pwd = ssl.getKeyStore().getPassword();
            if (keyPassword == null && pwd.isPresent()) {
                keyPassword = pwd.get().toCharArray();
            }
            keyManagerFactory.init(keyStore.orElse(null), keyPassword);
            return keyManagerFactory;
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }

    /**
     * @param ssl The ssl configuration
     *
     * @return An optional {@link KeyStore}
     * @throws Exception if there is an error
     */
    protected Optional<KeyStore> getKeyStore(SslConfiguration ssl) throws Exception {
        SslConfiguration.KeyStoreConfiguration keyStore = ssl.getKeyStore();
        Optional<String> path = keyStore.getPath();
        if (path.isPresent()) {
            return Optional.of(load(keyStore.getType(),
                path.get(), keyStore.getPassword()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * @param optionalType     The optional type
     * @param resource         The resource
     * @param optionalPassword The optional password
     * @return A {@link KeyStore}
     * @throws Exception if there is an error
     */
    protected KeyStore load(Optional<String> optionalType,
                            String resource,
                            Optional<String> optionalPassword) throws Exception {
        String type = optionalType.orElse("JKS");
        String password = optionalPassword.orElse(null);
        KeyStore store = KeyStore.getInstance(type);

        Optional<URL> url = resourceResolver.getResource(resource);
        if (url.isPresent()) {
            store.load(url.get().openStream(), password == null ? null : password.toCharArray());
            return store;
        } else {
            throw new SslConfigurationException("The resource " + resource + " could not be found");
        }
    }
}
