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

import io.micronaut.core.util.StringUtils;
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

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    /**
     * The default port value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_PORT = 8443;

    /**
     * The default build self signed value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_BUILDSELFSIGNED = false;

    /**
     * The default protocol.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_PROTOCOL = "TLS";

    private boolean enabled = DEFAULT_ENABLED;
    private int port = DEFAULT_PORT;
    private boolean buildSelfSigned = DEFAULT_BUILDSELFSIGNED;
    private KeyConfiguration key = new KeyConfiguration();
    private KeyStoreConfiguration keyStore = new KeyStoreConfiguration();
    private TrustStoreConfiguration trustStore = new TrustStoreConfiguration();
    private ClientAuthentication clientAuthentication;
    private String[] ciphers;
    private String[] protocols;
    private String protocol = DEFAULT_PROTOCOL;

    /**
     * @return Whether SSL is enabled.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether SSL is enabled. Default value ({@value io.micronaut.http.ssl.SslConfiguration#DEFAULT_ENABLED}).
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
     * Sets the SSL port. Default value ({@value io.micronaut.http.ssl.SslConfiguration#DEFAULT_PORT}).
     *
     * @param port The port
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Sets whether to build a self signed certificate. Default value ({@value io.micronaut.http.ssl.SslConfiguration#DEFAULT_BUILDSELFSIGNED}).
     *
     * @param buildSelfSigned True if a certificate should be built
     */
    public void setBuildSelfSigned(boolean buildSelfSigned) {
        this.buildSelfSigned = buildSelfSigned;
    }

    /**
     * Sets the key configuration.
     * @param key The key configuration
     */
    public void setKey(KeyConfiguration key) {
        if (key != null) {
            this.key = key;
        }
    }

    /**
     * Sets the keystore configuration.
     *
     * @param keyStore The keystore configuration
     */
    public void setKeyStore(KeyStoreConfiguration keyStore) {
        if (keyStore != null) {
            this.keyStore = keyStore;
        }
    }

    /**
     * Sets the trust store configuration.
     *
     * @param trustStore The trust store.
     */
    public void setTrustStore(TrustStoreConfiguration trustStore) {
        this.trustStore = trustStore;
    }

    /**
     * Sets the client authentication mode.
     *
     * @param clientAuthentication The client authentication mode
     */
    public void setClientAuthentication(ClientAuthentication clientAuthentication) {
        this.clientAuthentication = clientAuthentication;
    }

    /**
     * Sets the ciphers to use.
     *
     * @param ciphers The ciphers
     */
    public void setCiphers(String[] ciphers) {
        this.ciphers = ciphers;
    }

    /**
     * Sets the protocols to use.
     *
     * @param protocols The protocols
     */
    public void setProtocols(String[] protocols) {
        this.protocols = protocols;
    }

    /**
     * Sets the protocol to use. Default value ({@value io.micronaut.http.ssl.SslConfiguration#DEFAULT_PROTOCOL}).
     *
     * @param protocol The protocol
     */
    public void setProtocol(String protocol) {
        if (!StringUtils.isNotEmpty(protocol)) {
            this.protocol = protocol;
        }
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
        private String password;
        private String alias;

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

        /**
         * Sets the password.
         *
         * @param password The password
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * Sets the alias.
         *
         * @param alias The alias
         */
        public void setAlias(String alias) {
            this.alias = alias;
        }
    }

    /**
     * Configuration properties for SSL key store.
     */
    public static class KeyStoreConfiguration {
        public static final String PREFIX = "key-store";
        private String path;
        private String password;
        private String type;
        private String provider;

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

        /**
         * Sets the path.
         *
         * @param path The path
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Sets the password to use for the keystore.
         *
         * @param password The password
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * Sets the type of keystore.
         *
         * @param type The keystore type
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * Sets the keystore provider name.
         *
         * @param provider The provider
         */
        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    /**
     * Configuration properties for SSL trust store.
     */
    public static class TrustStoreConfiguration {
        public static final String PREFIX = "trust-store";
        private String path;
        private String password;
        private String type;
        private String provider;

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

        /**
         * Sets the path.
         *
         * @param path The path
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Sets the password to use for the keystore.
         *
         * @param password The password
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * Sets the type of keystore.
         *
         * @param type The keystore type
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * Sets the keystore provider name.
         *
         * @param provider The provider
         */
        public void setProvider(String provider) {
            this.provider = provider;
        }
    }
}
