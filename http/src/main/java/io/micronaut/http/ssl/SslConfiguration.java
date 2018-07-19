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

import io.micronaut.core.util.Toggleable;

import java.util.Optional;

/**
 * Configuration properties for SSL handling.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class SslConfiguration implements Toggleable {
    /**
     * The prefix used to resolve this configuration.
     */
    public static final String PREFIX = "micronaut.ssl";
    protected boolean enabled = false;
    protected int port = 8443;
    protected boolean buildSelfSigned = false;

    protected KeyConfiguration key = new KeyConfiguration();
    protected KeyStoreConfiguration keyStore = new KeyStoreConfiguration();
    protected TrustStoreConfiguration trustStore = new TrustStoreConfiguration();
    protected ClientAuthentication clientAuthentication;
    protected String[] ciphers;
    protected String[] protocols;
    protected String protocol = "TLS";

    /**
     * @return Whether SSL is enabled.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether SSL is enabled.
     *
     * @param enabled True if SSL is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
        return Optional.ofNullable(clientAuthentication);
    }

    /**
     * @return Which SSL ciphers to use
     */
    public Optional<String[]> getCiphers() {
        return Optional.ofNullable(ciphers);
    }

    /**
     * @return Which protocols to use
     */
    public Optional<String[]> getProtocols() {
        return Optional.ofNullable(protocols);
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
        return Optional.ofNullable(protocol);
    }

    /**
     * Reads an existing config.
     *
     * @param defaultSslConfiguration The default SSL config
     * @param defaultKeyConfiguration The default key config
     * @param defaultKeyStoreConfiguration The default keystore config
     * @param defaultTrustStoreConfiguration The Default truststore config
     */
    protected final void readExisting(
            SslConfiguration defaultSslConfiguration,
            KeyConfiguration defaultKeyConfiguration,
            KeyStoreConfiguration defaultKeyStoreConfiguration,
            TrustStoreConfiguration defaultTrustStoreConfiguration) {
        if (defaultKeyConfiguration != null) {
            this.key = defaultKeyConfiguration;
        }
        if (defaultKeyStoreConfiguration != null) {
            this.keyStore = defaultKeyStoreConfiguration;
        }
        if (defaultKeyConfiguration != null) {
            this.trustStore = defaultTrustStoreConfiguration;
        }
        if (defaultSslConfiguration != null) {
            this.port = defaultSslConfiguration.getPort();
            this.enabled = defaultSslConfiguration.isEnabled();
            this.buildSelfSigned = defaultSslConfiguration.buildSelfSigned();
            defaultSslConfiguration.getProtocols().ifPresent(strings -> this.protocols = strings);
            defaultSslConfiguration.getProtocol().ifPresent(protocol -> this.protocol = protocol);
            defaultSslConfiguration.getCiphers().ifPresent(ciphers -> this.ciphers = ciphers);
            defaultSslConfiguration.getClientAuthentication().ifPresent(ca -> this.clientAuthentication = ca);
        }
    }

    /**
     * Configuration properties for SSL key.
     */

    public static class KeyConfiguration {
        public static final String PREFIX = "key";
        protected String password;
        protected String alias;

        /**
         * @return The password of the key
         */
        public Optional<String> getPassword() {
            return Optional.ofNullable(password);
        }

        /**
         * @return The alias of the key
         */
        public Optional<String> getAlias() {
            return Optional.ofNullable(alias);
        }
    }

    /**
     * Configuration properties for SSL key store.
     */
    public static class KeyStoreConfiguration {
        public static final String PREFIX = "key-store";
        protected String path;
        protected String password;
        protected String type;
        protected String provider;

        /**
         * @return The path to the key store (typically .jks). Can use classpath: and file:.
         */
        public Optional<String> getPath() {
            return Optional.ofNullable(path);
        }

        /**
         * @return The password to the keyStore
         */
        public Optional<String> getPassword() {
            return Optional.ofNullable(password);
        }

        /**
         * @return The key store type
         */
        public Optional<String> getType() {
            return Optional.ofNullable(type);
        }

        /**
         * @return Provider for the key store.
         */
        public Optional<String> getProvider() {
            return Optional.ofNullable(provider);
        }
    }

    /**
     * Configuration properties for SSL trust store.
     */
    public static class TrustStoreConfiguration {
        public static final String PREFIX = "trust-store";
        protected String path;
        protected String password;
        protected String type;
        protected String provider;

        /**
         * @return The path to the trust store (typically .jks). Can use classpath: and file:.
         */
        public Optional<String> getPath() {
            return Optional.ofNullable(path);
        }

        /**
         * @return The password to the keyStore
         */
        public Optional<String> getPassword() {
            return Optional.ofNullable(password);
        }

        /**
         * @return The key store type
         */
        public Optional<String> getType() {
            return Optional.ofNullable(type);
        }

        /**
         * @return Provider for the key store.
         */
        public Optional<String> getProvider() {
            return Optional.ofNullable(provider);
        }
    }
}
