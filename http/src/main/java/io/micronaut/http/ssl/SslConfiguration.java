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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;

import java.util.Optional;

/**
 * Configuration properties for SSL handling.
 *
 * @author James Kleeh
 * @since 1.0
 */
@ConfigurationProperties("micronaut.ssl")
public class SslConfiguration implements Toggleable {
    protected boolean enabled = false;
    protected int port = 8443;
    protected boolean buildSelfSigned = false;

    protected KeyConfiguration key = new KeyConfiguration();
    protected KeyStoreConfiguration keyStore = new KeyStoreConfiguration();
    protected TrustStoreConfiguration trustStore = new TrustStoreConfiguration();
    protected Optional<ClientAuthentication> clientAuthentication = Optional.empty();
    protected Optional<String[]> ciphers = Optional.empty();
    protected Optional<String[]> protocols = Optional.empty();
    protected Optional<String> protocol = Optional.of("TLS");

    /**
     * @return Whether SSL is enabled.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return The default SSL port
     */
    public int getPort() {
        return port;
    }

    /**
     * @return Whether or not to build a self signed certificate
     */
    public boolean buildSelfSigned() {
        return buildSelfSigned;
    }

    /**
     * @return The type of client authentication
     */
    public Optional<ClientAuthentication> getClientAuthentication() {
        return clientAuthentication;
    }

    /**
     * @return Which SSL ciphers to use
     */
    public Optional<String[]> getCiphers() {
        return ciphers;
    }

    /**
     * @return Which protocols to use
     */
    public Optional<String[]> getProtocols() {
        return protocols;
    }

    /**
     * @return The configuration for the key
     */
    public KeyConfiguration getKey() {
        return key;
    }

    /**
     * @return The configuration for the key store
     */
    public KeyStoreConfiguration getKeyStore() {
        return keyStore;
    }

    /**
     * @return The configuration for the trust store
     */
    public TrustStoreConfiguration getTrustStore() {
        return trustStore;
    }

    /**
     * @return The protocol to use
     */
    public Optional<String> getProtocol() {
        return protocol;
    }

    /**
     * Configuration properties for SSL key.
     */
    @ConfigurationProperties("key")
    public static class KeyConfiguration {
        protected Optional<String> password = Optional.empty();
        protected Optional<String> alias = Optional.empty();

        /**
         * @return The password of the key
         */
        public Optional<String> getPassword() {
            return password;
        }

        /**
         * @return The alias of the key
         */
        public Optional<String> getAlias() {
            return alias;
        }
    }

    /**
     * Configuration properties for SSL key store.
     */
    @ConfigurationProperties("keyStore")
    public static class KeyStoreConfiguration {
        protected Optional<String> path = Optional.empty();
        protected Optional<String> password = Optional.empty();
        protected Optional<String> type = Optional.empty();
        protected Optional<String> provider = Optional.empty();

        /**
         * @return The path to the key store (typically .jks). Can use classpath: and file:.
         */
        public Optional<String> getPath() {
            return path;
        }

        /**
         * @return The password to the keyStore
         */
        public Optional<String> getPassword() {
            return password;
        }

        /**
         * @return The key store type
         */
        public Optional<String> getType() {
            return type;
        }

        /**
         * @return Provider for the key store.
         */
        public Optional<String> getProvider() {
            return provider;
        }
    }

    /**
     * Configuration properties for SSL trust store.
     */
    @ConfigurationProperties("trustStore")
    public static class TrustStoreConfiguration {
        protected Optional<String> path = Optional.empty();
        protected Optional<String> password = Optional.empty();
        protected Optional<String> type = Optional.empty();
        protected Optional<String> provider = Optional.empty();

        /**
         * @return The path to the trust store (typically .jks). Can use classpath: and file:.
         */
        public Optional<String> getPath() {
            return path;
        }

        /**
         * @return The password to the keyStore
         */
        public Optional<String> getPassword() {
            return password;
        }

        /**
         * @return The key store type
         */
        public Optional<String> getType() {
            return type;
        }

        /**
         * @return Provider for the key store.
         */
        public Optional<String> getProvider() {
            return provider;
        }
    }
}
