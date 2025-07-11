/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(SslConfiguration.class);

    private boolean enabled = DEFAULT_ENABLED;
    protected int port = DEFAULT_PORT;
    protected boolean buildSelfSigned = DEFAULT_BUILDSELFSIGNED;
    private KeyConfiguration key = new KeyConfiguration();
    private KeyStoreConfiguration keyStore = new KeyStoreConfiguration();
    private TrustStoreConfiguration trustStore = new TrustStoreConfiguration();
    private ClientAuthentication clientAuthentication;
    private String[] ciphers;
    private String[] protocols;
    private String protocol = DEFAULT_PROTOCOL;
    private Duration handshakeTimeout = Duration.ofSeconds(10);
    private boolean preferOpenssl = true;

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
     * @return Whether to build a self-signed certificate
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
     * @return The timeout for the SSL handshake
     */
    @NonNull
    public Duration getHandshakeTimeout() {
        return handshakeTimeout;
    }

    /**
     * Sets the SSL port. Default value ({@value io.micronaut.http.ssl.SslConfiguration#DEFAULT_PORT}).
     *
     * @param port The port
     *
     * @deprecated Please use {@code micronaut.server.ssl.port} instead ({@link ServerSslConfiguration#setPort(int)}).
     */
    @Deprecated
    public void setPort(int port) {
        LOGGER.warn("The configuration micronaut.ssl.port is deprecated. Use micronaut.server.ssl.port instead.");
        this.port = port;
    }

    /**
     * Sets whether to build a self-signed certificate. Default value ({@value io.micronaut.http.ssl.SslConfiguration#DEFAULT_BUILDSELFSIGNED}).
     *
     * @param buildSelfSigned True if a certificate should be built
     *
     * @deprecated Please use {@code micronaut.server.ssl.build-self-signed} instead ({@link ServerSslConfiguration#buildSelfSigned()}).
     */
    @Deprecated
    public void setBuildSelfSigned(boolean buildSelfSigned) {
        LOGGER.warn("The configuration micronaut.ssl.build-self-signed is deprecated. Use micronaut.server.ssl.build-self-signed instead.");
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
        if (StringUtils.isNotEmpty(protocol)) {
            this.protocol = protocol;
        }
    }

    /**
     * @param handshakeTimeout The timeout for the SSL handshake
     */
    public void setHandshakeTimeout(@NonNull Duration handshakeTimeout) {
        this.handshakeTimeout = Objects.requireNonNull(handshakeTimeout, "handshakeTimeout");
    }

    /**
     * Whether an OpenSSL-backed TLS implementation should be preferred if it's on the classpath.
     * {@code true} by default.
     *
     * @return Whether OpenSSL should be preferred
     */
    @Experimental
    public boolean isPreferOpenssl() {
        return preferOpenssl;
    }

    /**
     * Whether an OpenSSL-backed TLS implementation should be preferred if it's on the classpath.
     * {@code true} by default.
     *
     * @param preferOpenssl Whether OpenSSL should be preferred
     */
    @Experimental
    public void setPreferOpenssl(boolean preferOpenssl) {
        this.preferOpenssl = preferOpenssl;
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
            this.port = defaultSslConfiguration.port;
            this.enabled = defaultSslConfiguration.isEnabled();
            this.buildSelfSigned = defaultSslConfiguration.buildSelfSigned;
            defaultSslConfiguration.getProtocols().ifPresent(strings -> this.protocols = strings);
            defaultSslConfiguration.getProtocol().ifPresent(protocol -> this.protocol = protocol);
            defaultSslConfiguration.getCiphers().ifPresent(ciphers -> this.ciphers = ciphers);
            defaultSslConfiguration.getClientAuthentication().ifPresent(ca -> this.clientAuthentication = ca);
            this.handshakeTimeout = defaultSslConfiguration.getHandshakeTimeout();
            this.preferOpenssl = defaultSslConfiguration.isPreferOpenssl();
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
        private String keyPath;
        private String certificatePath;
        private String password;
        private String type;
        private String provider;

        /**
         * The path to the key store (typically .jks). Can also point to a PEM file containing
         * a private key followed by the X.509 trust chain. Can use {@code classpath:},
         * {@code file:}, {@code string:} or {@code base64:}.
         *
         * @return The resource containing the key store
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
         * The path to the key store (typically .jks). Can also point to a PEM file containing
         * a private key followed by the X.509 trust chain. Can use {@code classpath:},
         * {@code file:}, {@code string:} or {@code base64:}.
         *
         * @param path The resource containing the key store
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

        /**
         * A path to a PEM file containing the private key of the server. Can use
         * {@code classpath:}, {@code file:}, {@code string:} or {@code base64:}. Cannot be set at
         * the same time as the {@code path} property.
         *
         * @return The key path
         * @since 4.10.0
         */
        @Nullable
        public String getKeyPath() {
            return keyPath;
        }

        /**
         * A path to a PEM file containing the private key. Can use
         * {@code classpath:}, {@code file:}, {@code string:} or {@code base64:}. Cannot be set at
         * the same time as the {@code path} property.
         *
         * @param keyPath The key path
         * @since 4.10.0
         */
        public void setKeyPath(@Nullable String keyPath) {
            this.keyPath = keyPath;
        }

        /**
         * A path to a PEM file containing the certificate chain. Can use
         * {@code classpath:}, {@code file:}, {@code string:} or {@code base64:}. Cannot be set at
         * the same time as the {@code path} property.
         *
         * @return The certificate chain path
         * @since 4.10.0
         */
        @Nullable
        public String getCertificatePath() {
            return certificatePath;
        }

        /**
         * A path to a PEM file containing the certificate chain. Can use
         * {@code classpath:}, {@code file:}, {@code string:} or {@code base64:}. Cannot be set at
         * the same time as the {@code path} property.
         *
         * @param certificatePath The certificate chain path
         * @since 4.10.0
         */
        public void setCertificatePath(@Nullable String certificatePath) {
            this.certificatePath = certificatePath;
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
         * The path to the trust store (typically .jks). Can also point to a PEM file containing
         * one or more X.509 certificates. Can use {@code classpath:}, {@code file:},
         * {@code string:} or {@code base64:}.
         *
         * @return The resource containing the trust store
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
         * The path to the trust store (typically .jks). Can also point to a PEM file containing
         * one or more X.509 certificates. Can use {@code classpath:}, {@code file:},
         * {@code string:} or {@code base64:}.
         *
         * @param path The resource containing the trust store
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
