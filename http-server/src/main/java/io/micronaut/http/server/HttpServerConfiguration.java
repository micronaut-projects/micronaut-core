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

package io.micronaut.http.server;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.server.cors.CorsOriginConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.inject.Inject;
import java.io.File;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * <p>A base {@link ConfigurationProperties} for servers.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(value = HttpServerConfiguration.PREFIX, cliPrefix = "")
public class HttpServerConfiguration {

    /**
     * The prefix used for configuration.
     */

    public static final String PREFIX = "micronaut.server";

    protected int port = -1; // default to random port
    protected Optional<String> host = Optional.empty();
    protected Optional<Integer> readTimeout;
    @ReadableBytes
    protected long maxRequestSize = 1024 * 1024 * 10; // 10MB
    protected Duration readIdleTime = Duration.of(60, ChronoUnit.SECONDS);
    protected Duration writeIdleTime = Duration.of(60, ChronoUnit.SECONDS);
    protected Duration idleTime = Duration.of(60, ChronoUnit.SECONDS);
    protected MultipartConfiguration multipart = new MultipartConfiguration();
    protected CorsConfiguration cors = new CorsConfiguration();
    protected Optional<String> serverHeader = Optional.empty();
    protected boolean dateHeader = true;

    private final ApplicationConfiguration applicationConfiguration;
    private Charset defaultCharset;

    /**
     * Default constructor.
     */
    public HttpServerConfiguration() {
        this.applicationConfiguration = new ApplicationConfiguration();
    }

    /**
     * @param applicationConfiguration The application configuration
     */
    @Inject
    public HttpServerConfiguration(ApplicationConfiguration applicationConfiguration) {
        if (applicationConfiguration != null) {
            this.defaultCharset = applicationConfiguration.getDefaultCharset();
        }

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

    /**
     * @param defaultCharset The default charset to use
     */
    public void setDefaultCharset(Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    /**
     * @return The default server port
     */
    public int getPort() {
        return port;
    }

    /**
     * @return The default host
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
     * @return Configuration for multipart / file uploads
     */
    public MultipartConfiguration getMultipart() {
        return multipart;
    }

    /**
     * @return Configuration for CORS
     */
    public CorsConfiguration getCors() {
        return cors;
    }

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
     * @return The time to allow an idle connection for
     */
    public Duration getIdleTime() {
        return idleTime;
    }

    /**
     * @return The optional server header value
     */
    public Optional<String> getServerHeader() {
        return serverHeader;
    }

    /**
     * @return True if the date header should be set
     */
    public boolean isDateHeader() {
        return dateHeader;
    }

    /**
     * Configuration for multipart handling.
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

    /**
     * Configuration for CORS.
     */
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
