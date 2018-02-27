/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.ssl;

import org.particleframework.core.io.ResourceLoader;
import org.particleframework.http.server.HttpServerConfiguration.SslConfiguration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.URL;
import java.security.KeyStore;
import java.util.Optional;

/**
 * A class to build a key store and a trust store for use
 * in adding SSL support to a server
 *
 * @param <T> The server specific type to be built
 *
 * @author James Kleeh
 * @since 1.0
 */
abstract public class SslBuilder<T> {

    protected final SslConfiguration ssl;

    public SslBuilder(SslConfiguration ssl) {
        this.ssl = ssl;
    }

    abstract public Optional<T> build();

    protected TrustManagerFactory getTrustManagerFactory() {
        try {
            Optional<KeyStore> store = getTrustStore();
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(store.orElse(null));
            return trustManagerFactory;
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected Optional<KeyStore> getTrustStore() throws Exception {
        SslConfiguration.TrustStoreConfiguration trustStore = ssl.getTrustStore();
        if (!trustStore.getPath().isPresent()) {
            return Optional.empty();
        }
        return Optional.of(load(trustStore.getType(),
                trustStore.getPath().get(), trustStore.getPassword()));
    }

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
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected Optional<KeyStore> getKeyStore() throws Exception {
        SslConfiguration.KeyStoreConfiguration keyStore = ssl.getKeyStore();
        if (!keyStore.getPath().isPresent()) {
            return Optional.empty();
        }
        return Optional.of(load(keyStore.getType(),
                keyStore.getPath().get(), keyStore.getPassword()));
    }

    protected KeyStore load(Optional<String> optionalType,
                            String resource,
                            Optional<String> optionalPassword) throws Exception {
        String type = optionalType.orElse("JKS");
        String password = optionalPassword.orElse(null);
        KeyStore store = KeyStore.getInstance(type);
        Optional<ResourceLoader> resourceLoader = ResourceLoader.forResource(resource, this.getClass().getClassLoader());
        if (resourceLoader.isPresent()) {
            Optional<URL> url = resourceLoader.get().getResource(resource);
            if (url.isPresent()) {
                store.load(url.get().openStream(), password == null ? null : password.toCharArray());
                return store;
            } else {
                throw new SslConfigurationException("The resource " + resource + " could not be found");
            }
        } else {
            throw new SslConfigurationException("No resource loader could be found for the path: " + resource);
        }
    }

}
