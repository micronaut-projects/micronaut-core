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
package org.particleframework.http.server;

import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.core.convert.format.ReadableBytes;
import org.particleframework.core.util.Toggleable;
import org.particleframework.http.server.cors.CorsOriginConfiguration;
import org.particleframework.http.server.ssl.ClientAuthentication;
import org.particleframework.runtime.ApplicationConfiguration;

import javax.inject.Inject;
import java.io.File;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * <p>A base {@link ConfigurationProperties} for servers</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(value = "particle.server", cliPrefix = "")
public class HttpServerConfiguration  {

    public static final String LOCALHOST = "localhost";

    private final ApplicationConfiguration applicationConfiguration;
    private Charset defaultCharset;
    protected int port = -1; // default to random port
    protected Optional<String> host = Optional.empty();
    protected Optional<Integer> readTimeout;
    @ReadableBytes
    protected long maxRequestSize = 1024 * 1024 * 10; // 10MB
    protected Duration readIdleTime = Duration.of(60, ChronoUnit.SECONDS);
    protected Duration writeIdleTime = Duration.of(60, ChronoUnit.SECONDS);
    protected SslConfiguration ssl = new SslConfiguration();
    protected MultipartConfiguration multipart =  new MultipartConfiguration();
    protected CorsConfiguration cors = new CorsConfiguration();

    public HttpServerConfiguration() {
        this.applicationConfiguration = new ApplicationConfiguration();
    }

    @Inject
    public HttpServerConfiguration(ApplicationConfiguration applicationConfiguration) {
        if(applicationConfiguration != null)
            this.defaultCharset = applicationConfiguration.getDefaultCharset();

        this.applicationConfiguration = applicationConfiguration;
    }

    /**
     * @return The application configuration instance
     */
    public ApplicationConfiguration getApplicationConfiguration() {
        return applicationConfiguration;
    }

    /**
     * @return The default charset to use
     */
    public Charset getDefaultCharset() {
        return defaultCharset;
    }

    public void setDefaultCharset(Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    /**
     * The default server port
     */
    public int getPort() {
        return port;
    }

    /**
     * The default host
     */
    public Optional<String> getHost() {
        return host;
    }

    /**
     * @return The read timeout setting for the server
     */
    public Optional<Integer> getReadTimeout() {
        return readTimeout;
    }

    /**
     * @return The HTTPS/SSL configuration
     */
    public SslConfiguration getSsl() {
        return ssl;
    }

    /**
     * @return Configuration for multipart / file uploads
     */
    public MultipartConfiguration getMultipart() {
        return multipart;
    }

    /**
     * @return Configuration for CORS
     */
    public CorsConfiguration getCors() { return cors; }

    /**
     * @return The maximum request body size
     */
    public long getMaxRequestSize() {
        return maxRequestSize;
    }

    /**
     * @return The default amount of time to allow read operation connections  to remain idle
     */
    public Duration getReadIdleTime() {
        return readIdleTime;
    }

    /**
     * @return The default amount of time to allow write operation connections to remain idle
     */
    public Duration getWriteIdleTime() {
        return writeIdleTime;
    }

    /**
     * Configuration properties for SSL handling
     *
     * TODO
     */
    @ConfigurationProperties("ssl")
    public static class SslConfiguration implements Toggleable {
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

        /**
         * @return Whether SSL is enabled
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
    }

    /**
     * Configuration for multipart handling
     */
    @ConfigurationProperties("multipart")
    public static class MultipartConfiguration implements Toggleable {
        protected Optional<File> location = Optional.empty();
        @ReadableBytes
        protected long maxFileSize = 1024 * 1024; // 1MB
        protected boolean enabled = true;
        protected boolean disk = false;

        /**
         * @return The location to store temporary files
         */
        public Optional<File> getLocation() {
            return location;
        }

        /**
         * @return The max file size. Defaults to 1MB
         */
        public long getMaxFileSize() {
            return maxFileSize;
        }

        /**
         * @return Whether file uploads are enabled. Defaults to true.
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @return Whether to use disk. Defaults to false.
         */
        public boolean isDisk() {
            return disk;
        }
    }

    @ConfigurationProperties("cors")
    public static class CorsConfiguration implements Toggleable {

        protected boolean enabled = false;

        protected Map<String, CorsOriginConfiguration> configurations = Collections.emptyMap();

        private Map<String, CorsOriginConfiguration> defaultConfiguration = new LinkedHashMap<>(1);

        /**
         * @return Whether cors is enabled. Defaults to false.
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @return The cors configurations
         */
        public Map<String, CorsOriginConfiguration> getConfigurations() {
            if (enabled && configurations.isEmpty()) {
                if (defaultConfiguration.isEmpty()) {
                    defaultConfiguration.put("default", new CorsOriginConfiguration());
                }
                return defaultConfiguration;
            }
            return configurations;
        }
    }

}
