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

package io.micronaut.http.ssl;

import io.micronaut.core.io.ResourceResolver;

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

    protected final SslConfiguration ssl;
    private final ResourceResolver resourceResolver;
    private KeyStore keyStoreCache = null;
    private KeyStore trustStoreCache = null;

    /**
     * @param ssl              The SSL configuration
     * @param resourceResolver The resource resolver
     */
    public SslBuilder(SslConfiguration ssl, ResourceResolver resourceResolver) {
        this.ssl = ssl;
        this.resourceResolver = resourceResolver;
    }

    /**
     * @return Builds the SSL configuration wrapped inside an optional
     */
    public abstract Optional<T> build();

    /**
     * @return The {@link TrustManagerFactory}
     */
    protected TrustManagerFactory getTrustManagerFactory() {
        try {
            Optional<KeyStore> store = getTrustStore();
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(store.orElse(null));
            return trustManagerFactory;
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }

    /**
     * @return An optional {@link KeyStore}
     * @throws Exception if there is an error
     */
    protected Optional<KeyStore> getTrustStore() throws Exception {
        if (trustStoreCache == null) {
            SslConfiguration.TrustStoreConfiguration trustStore = ssl.getTrustStore();
            if (!trustStore.getPath().isPresent()) {
                return Optional.empty();
            }
            trustStoreCache = load(trustStore.getType(),
                trustStore.getPath().get(), trustStore.getPassword());
        }
        return Optional.of(trustStoreCache);
    }

    /**
     * @return The {@link KeyManagerFactory}
     */
    protected KeyManagerFactory getKeyManagerFactory() {
        try {
            Optional<KeyStore> keyStore = getKeyStore();
            KeyManagerFactory keyManagerFactory = KeyManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            Optional<String> password = ssl.getKey().getPassword();
            char[] keyPassword = password.map(String::toCharArray).orElse(null);
            if (keyPassword == null && ssl.getKeyStore().getPassword().isPresent()) {
                keyPassword = ssl.getKeyStore().getPassword().get().toCharArray();
            }
            keyManagerFactory.init(keyStore.orElse(null), keyPassword);
            return keyManagerFactory;
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }

    /**
     * @return An optional {@link KeyStore}
     * @throws Exception if there is an error
     */
    protected Optional<KeyStore> getKeyStore() throws Exception {
        if (keyStoreCache == null) {
            SslConfiguration.KeyStoreConfiguration keyStore = ssl.getKeyStore();
            if (!keyStore.getPath().isPresent()) {
                return Optional.empty();
            }
            keyStoreCache = load(keyStore.getType(),
                keyStore.getPath().get(), keyStore.getPassword());
        }
        return Optional.of(keyStoreCache);
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
