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
package io.micronaut.http.server.netty.configuration;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolNames;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Allows configuring Netty within {@link io.micronaut.http.server.netty.NettyHttpServer}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("netty")
@Replaces(HttpServerConfiguration.class)
public class NettyHttpServerConfiguration extends HttpServerConfiguration {

    /**
     * The default use netty's native transport flag.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_USE_NATIVE_TRANSPORT = false;

    /**
     * The default max initial line length.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAXINITIALLINELENGTH = 4096;

    /**
     * The default max header size.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAXHEADERSIZE = 8192;

    /**
     * The default max chunk size.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAXCHUNKSIZE = 8192;

    /**
     * The default chunk supported value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_CHUNKSUPPORTED = true;

    /**
     * The default validate headers value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_VALIDATEHEADERS = true;

    /**
     * The default initial buffer size value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_INITIALBUFFERSIZE = 128;

    /**
     * The default compression threshold.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_COMPRESSIONTHRESHOLD = 1024;

    /**
     * The default compression level.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_COMPRESSIONLEVEL = 6;
    private final List<ChannelPipelineListener> pipelineCustomizers;

    private Map<ChannelOption, Object> childOptions = Collections.emptyMap();
    private Map<ChannelOption, Object> options = Collections.emptyMap();
    private Worker worker;
    private Parent parent;
    private int maxInitialLineLength = DEFAULT_MAXINITIALLINELENGTH;
    private int maxHeaderSize = DEFAULT_MAXHEADERSIZE;
    private int maxChunkSize = DEFAULT_MAXCHUNKSIZE;
    private boolean chunkedSupported = DEFAULT_CHUNKSUPPORTED;
    private boolean validateHeaders = DEFAULT_VALIDATEHEADERS;
    private int initialBufferSize = DEFAULT_INITIALBUFFERSIZE;
    private LogLevel logLevel;
    private int compressionThreshold = DEFAULT_COMPRESSIONTHRESHOLD;
    private int compressionLevel = DEFAULT_COMPRESSIONLEVEL;
    private boolean useNativeTransport = DEFAULT_USE_NATIVE_TRANSPORT;
    private String fallbackProtocol = ApplicationProtocolNames.HTTP_1_1;
    private AccessLogger accessLogger;
    private Http2Settings http2Settings = new Http2Settings();

    /**
     * Default empty constructor.
     */
    public NettyHttpServerConfiguration() {
        this(null, Collections.emptyList());
    }

    /**
     * @param applicationConfiguration The application configuration
     */
    public NettyHttpServerConfiguration(ApplicationConfiguration applicationConfiguration) {
        this(applicationConfiguration, Collections.emptyList());
    }

    /**
     * @param applicationConfiguration The application configuration
     * @param pipelineCustomizers A list of pipeline customizers
     */
    @Inject
    public NettyHttpServerConfiguration(
            ApplicationConfiguration applicationConfiguration,
            List<ChannelPipelineListener> pipelineCustomizers) {
        super(applicationConfiguration);
        this.pipelineCustomizers = pipelineCustomizers;
    }

    /**
     * Returns the AccessLogger configuration.
     * @return The AccessLogger configuration.
     */
    public AccessLogger getAccessLogger() {
        return accessLogger;
    }

    /**
     * Sets the AccessLogger configuration.
     * @param accessLogger The configuration .
     */
    public void setAccessLogger(AccessLogger accessLogger) {
        this.accessLogger = accessLogger;
    }

    /**
     * Returns the Http2Settings.
     * @return The Http2Settings.
     */
    public Http2Settings getHttp2() {
        return http2Settings;
    }

    /**
     * Sets the Http2Settings.
     * @param http2 The Http2Settings.
     */
    public void setHttp2(Http2Settings http2) {
        if (http2 != null) {
            this.http2Settings = http2;
        }
    }

    /**
     * @return The pipeline customizers
     */
    public List<ChannelPipelineListener> getPipelineCustomizers() {
        return pipelineCustomizers;
    }

    /**
     * @return The fallback protocol to use when negotiating via ALPN
     * @see ApplicationProtocolNames
     */
    public String getFallbackProtocol() {
        return fallbackProtocol;
    }

    /**
     * Sets the fallback protocol to use when negotiating via ALPN.
     *
     * @param fallbackProtocol The fallback protocol to use when negotiating via ALPN
     * @see ApplicationProtocolNames
     */
    public void setFallbackProtocol(String fallbackProtocol) {
        if (fallbackProtocol != null) {
            this.fallbackProtocol = fallbackProtocol;
        }
    }

    /**
     * The server {@link LogLevel} to enable.
     *
     * @return The server {@link LogLevel} to enable
     */
    public Optional<LogLevel> getLogLevel() {
        return Optional.ofNullable(logLevel);
    }

    /**
     * The maximum length of the initial HTTP request line. Defaults to 4096.
     *
     * @return The maximum length of the initial HTTP request line
     */
    public int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    /**
     * The maximum size of an individual HTTP setter. Defaults to 8192.
     *
     * @return The maximum size of an individual HTTP setter
     */
    public int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    /**
     * The maximum chunk size. Defaults to 8192.
     *
     * @return The maximum chunk size
     */
    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    /**
     * Whether chunked requests are supported.
     *
     * @return Whether chunked requests are supported.
     */
    public boolean isChunkedSupported() {
        return chunkedSupported;
    }

    /**
     * Whether to use netty's native transport (epoll or kqueue) if available.
     *
     * @return To use netty's native transport (epoll or kqueue) if available.
     */
    public boolean isUseNativeTransport() {
        return useNativeTransport;
    }

    /**
     * Whether to validate headers.
     *
     * @return Whether to validate headers
     */
    public boolean isValidateHeaders() {
        return validateHeaders;
    }

    /**
     * The initial buffer size. Defaults to 128.
     *
     * @return The initial buffer size.
     */
    public int getInitialBufferSize() {
        return initialBufferSize;
    }

    /**
     * The default compression threshold. Defaults to 1024.
     *
     * @return The compression threshold.
     */
    public int getCompressionThreshold() {
        return compressionThreshold;
    }

    /**
     * The default compression level. Default value ({@value #DEFAULT_COMPRESSIONLEVEL}).
     *
     * @return The compression level.
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * @return The Netty child channel options.
     * @see io.netty.bootstrap.ServerBootstrap#childOptions()
     */
    public Map<ChannelOption, Object> getChildOptions() {
        return childOptions;
    }

    /**
     * @return The Netty channel options.
     * @see io.netty.bootstrap.ServerBootstrap#options()
     */
    public Map<ChannelOption, Object> getOptions() {
        return options;
    }

    /**
     * @return Configuration for the worker {@link io.netty.channel.EventLoopGroup}
     */
    public Worker getWorker() {
        return worker;
    }

    /**
     * @return Configuration for the parent {@link io.netty.channel.EventLoopGroup}
     */
    public Parent getParent() {
        return parent;
    }

    /**
     * Sets the Netty child worker options.
     *
     * @param childOptions The options
     */
    public void setChildOptions(Map<ChannelOption, Object> childOptions) {
        this.childOptions = childOptions;
    }

    /**
     * Sets the channel options.
     * @param options The channel options
     */
    public void setOptions(Map<ChannelOption, Object> options) {
        this.options = options;
    }

    /**
     * Sets the worker event loop configuration.
     * @param worker The worker config
     */
    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    /**
     * Sets the parent event loop configuration.
     * @param parent The parent config
     */
    public void setParent(Parent parent) {
        this.parent = parent;
    }

    /**
     * Sets the maximum initial line length for the HTTP request. Default value ({@value #DEFAULT_MAXINITIALLINELENGTH}).
     * @param maxInitialLineLength The max length
     */
    public void setMaxInitialLineLength(@ReadableBytes int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
    }

    /**
     * Sets the maximum size of any one header. Default value ({@value #DEFAULT_MAXHEADERSIZE}).
     * @param maxHeaderSize The max header size
     */
    public void setMaxHeaderSize(@ReadableBytes int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    /**
     * Sets the maximum size of any single request chunk. Default value ({@value #DEFAULT_MAXCHUNKSIZE}).
     * @param maxChunkSize The max chunk size
     */
    public void setMaxChunkSize(@ReadableBytes int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    /**
     * Sets whether chunked transfer encoding is supported. Default value ({@value #DEFAULT_CHUNKSUPPORTED}).
     * @param chunkedSupported True if it is supported
     */
    public void setChunkedSupported(boolean chunkedSupported) {
        this.chunkedSupported = chunkedSupported;
    }

    /**
     * Sets whether to use netty's native transport (epoll or kqueue) if available . Default value ({@value #DEFAULT_USE_NATIVE_TRANSPORT}).
     * @param useNativeTransport True if netty's native transport should be use if available.
     */
    public void setUseNativeTransport(boolean useNativeTransport) {
        this.useNativeTransport = useNativeTransport;
    }

    /**
     * Sets whether to validate incoming headers. Default value ({@value #DEFAULT_VALIDATEHEADERS}).
     * @param validateHeaders True if headers should be validated.
     */
    public void setValidateHeaders(boolean validateHeaders) {
        this.validateHeaders = validateHeaders;
    }

    /**
     * Sets the initial buffer size. Default value ({@value #DEFAULT_INITIALBUFFERSIZE}).
     * @param initialBufferSize The initial buffer size
     */
    public void setInitialBufferSize(int initialBufferSize) {
        this.initialBufferSize = initialBufferSize;
    }

    /**
     * Sets the Netty log level.
     * @param logLevel The log level
     */
    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * Sets the minimum size of a request body must be in order to be compressed. Default value ({@value #DEFAULT_COMPRESSIONTHRESHOLD}).
     * @param compressionThreshold The size request bodies must be in order to be a candidate for compression.
     */
    public void setCompressionThreshold(@ReadableBytes int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    /**
     * Sets the compression level (0-9). Default value ({@value #DEFAULT_COMPRESSIONLEVEL}).
     *
     * @param compressionLevel The compression level.
     */
    public void setCompressionLevel(@ReadableBytes int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    /**
     * Http2 settings.
     */
    @ConfigurationProperties("http2")
    public static class Http2Settings {
        private final io.netty.handler.codec.http2.Http2Settings settings = io.netty.handler.codec.http2.Http2Settings.defaultSettings();

        /**
         * Returns netty's http2 settings.
         *
         * @return io.netty.handler.codec.http2.Http2Settings.
         */
        public io.netty.handler.codec.http2.Http2Settings http2Settings() {
            return settings;
        }

        /**
         * Gets the {@code SETTINGS_HEADER_TABLE_SIZE} value. If unavailable, returns {@code null}.
         *
         * @return The header table size or {@code null}.
         */
        public Long getHeaderTableSize() {
            return settings.headerTableSize();
        }

        /**
         * Sets the {@code SETTINGS_HEADER_TABLE_SIZE} value.
         *
         * @param value The header table size.
         * @throws IllegalArgumentException if verification of the setting fails.
         */
        public void setHeaderTableSize(Long value) {
            if (value != null) {
                settings.headerTableSize(value);
            }
        }

        /**
         * Gets the {@code SETTINGS_ENABLE_PUSH} value. If unavailable, returns {@code null}.
         *
         * @return The {@code SETTINGS_ENABLE_PUSH} value. If unavailable, returns {@code null}.
         */
        public Boolean getPushEnabled() {
            return settings.pushEnabled();
        }

        /**
         * Sets the {@code SETTINGS_ENABLE_PUSH} value.
         *
         * @param enabled The {@code SETTINGS_ENABLE_PUSH} value.
         */
        public void setPushEnabled(Boolean enabled) {
            if (enabled != null) {
                settings.pushEnabled(enabled);
            }
        }

        /**
         * Gets the {@code SETTINGS_MAX_CONCURRENT_STREAMS} value. If unavailable, returns {@code null}.
         *
         * @return The {@code SETTINGS_MAX_CONCURRENT_STREAMS} value. If unavailable, returns {@code null}.
         */
        public Long getMaxConcurrentStreams() {
            return settings.maxConcurrentStreams();
        }

        /**
         * Sets the {@code SETTINGS_MAX_CONCURRENT_STREAMS} value.
         *
         * @param value The {@code SETTINGS_MAX_CONCURRENT_STREAMS} value.
         * @throws IllegalArgumentException if verification of the setting fails.
         */
        public void setMaxConcurrentStreams(Long value) {
            if (value != null) {
                settings.maxConcurrentStreams(value);
            }
        }

        /**
         * Gets the {@code SETTINGS_INITIAL_WINDOW_SIZE} value. If unavailable, returns {@code null}.
         *
         * @return The {@code SETTINGS_INITIAL_WINDOW_SIZE} value. If unavailable, returns {@code null}.
         */
        public Integer getInitialWindowSize() {
            return settings.initialWindowSize();
        }

        /**
         * Sets the {@code SETTINGS_INITIAL_WINDOW_SIZE} value.
         *
         * @param value The {@code SETTINGS_INITIAL_WINDOW_SIZE} value.
         * @throws IllegalArgumentException if verification of the setting fails.
         */
        public void setInitialWindowSize(Integer value) {
            if (value != null) {
                settings.initialWindowSize(value);
            }
        }

        /**
         * Gets the {@code SETTINGS_MAX_FRAME_SIZE} value. If unavailable, returns {@code null}.
         *
         * @return The {@code SETTINGS_MAX_FRAME_SIZE} value. If unavailable, returns {@code null}.
         */
        public Integer getMaxFrameSize() {
            return settings.maxFrameSize();
        }

        /**
         * Sets the {@code SETTINGS_MAX_FRAME_SIZE} value.
         *
         * @param value The {@code SETTINGS_MAX_FRAME_SIZE} value.
         * @throws IllegalArgumentException if verification of the setting fails.
         */
        public void setMaxFrameSize(Integer value) {
            if (value != null) {
                settings.maxFrameSize(value);
            }
        }

        /**
         * Gets the {@code SETTINGS_MAX_HEADER_LIST_SIZE} value. If unavailable, returns {@code null}.
         *
         * @return The {@code SETTINGS_MAX_HEADER_LIST_SIZE} value. If unavailable, returns {@code null}.
         */
        public Long getMaxHeaderListSize() {
            return settings.maxHeaderListSize();
        }

        /**
         * Sets the {@code SETTINGS_MAX_HEADER_LIST_SIZE} value.
         *
         * @param value The {@code SETTINGS_MAX_HEADER_LIST_SIZE} value.
         * @throws IllegalArgumentException if verification of the setting fails.
         */
        public void setMaxHeaderListSize(Long value) {
            if (value != null) {
                settings.maxHeaderListSize(value);
            }
        }
    }

    /**
     * Access logger configuration.
     */
    @ConfigurationProperties("access-logger")
    public static class AccessLogger {
        private boolean enabled;
        private String loggerName;
        private String logFormat;

        /**
         * Returns whether the access logger is enabled.
         * @return Whether the access logger is enabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or Disables the access logger.
         * @param enabled The flag.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * The logger name to use. Access logs will be logged at info level.
         * @return The logger name.
         */
        public String getLoggerName() {
            return loggerName;
        }

        /**
         * Sets the logger name to use. If not specified 'HTTP_ACCESS_LOGGER' will be used.
         * @param loggerName A logger name,
         */
        public void setLoggerName(String loggerName) {
            this.loggerName = loggerName;
        }

        /**
         * Returns the log format to use.
         * @return The log format.
         */
        public String getLogFormat() {
            return logFormat;
        }

        /**
         * Sets the log format to use. When not specified, the Common Log Format (CLF) will be used.
         * @param logFormat The log format.
         */
        public void setLogFormat(String logFormat) {
            this.logFormat = logFormat;
        }

    }

    /**
     * Configuration for Netty worker.
     */
    @ConfigurationProperties("worker")
    public static class Worker extends EventLoopConfig {
        /**
         * Default constructor.
         */
        Worker() {
            super(DEFAULT);
        }
    }

    /**
     * Configuration for Netty parent.
     */
    @ConfigurationProperties(Parent.NAME)
    public static class Parent extends EventLoopConfig {

        public static final String NAME = "parent";

        /**
         * Default constructor.
         */
        Parent() {
            super(NAME);
        }
    }

    /**
     * Abstract class for configuring the Netty event loop.
     */
    public abstract static class EventLoopConfig implements EventLoopGroupConfiguration {
        private int threads;
        private Integer ioRatio;
        private String executor;
        private boolean preferNativeTransport = false;
        private Duration shutdownQuietPeriod = Duration.ofSeconds(DEFAULT_SHUTDOWN_QUIET_PERIOD);
        private Duration shutdownTimeout = Duration.ofSeconds(DEFAULT_SHUTDOWN_TIMEOUT);
        private String name;

        /**
         * @param name The name;
         */
        EventLoopConfig(String name) {
            this.name = name;
        }

        @NonNull
        @Override
        public String getName() {
            return name;
        }

        /**
         * Sets the name to use.
         * @param name The name
         */
        public void setEventLoopGroup(String name) {
            if (StringUtils.isNotEmpty(name)) {
                this.name = name;
            }
        }

        /**
         * Sets the number of threads for the event loop group.
         * @param threads The number of threads
         */
        public void setThreads(int threads) {
            this.threads = threads;
        }

        /**
         * Sets the I/O ratio.
         * @param ioRatio The I/O ratio
         */
        public void setIoRatio(Integer ioRatio) {
            this.ioRatio = ioRatio;
        }

        /**
         * Sets the name of the executor.
         * @param executor The executor
         */
        public void setExecutor(String executor) {
            this.executor = executor;
        }

        /**
         * @param preferNativeTransport Set whether to prefer the native transport if available
         */
        public void setPreferNativeTransport(boolean preferNativeTransport) {
            this.preferNativeTransport = preferNativeTransport;
        }

        /**
         * @param shutdownQuietPeriod Set the shutdown quiet period
         */
        public void setShutdownQuietPeriod(Duration shutdownQuietPeriod) {
            if (shutdownQuietPeriod != null) {
                this.shutdownQuietPeriod = shutdownQuietPeriod;
            }
        }

        /**
         * @param shutdownTimeout Set the shutdown timeout (must be >= shutdownQuietPeriod)
         */
        public void setShutdownTimeout(Duration shutdownTimeout) {
            if (shutdownTimeout != null) {
                this.shutdownTimeout = shutdownTimeout;
            }
        }

        /**
         * @return The number of threads to use
         */
        public int getNumOfThreads() {
            return threads;
        }

        /**
         * @return The I/O ratio to use
         */
        @Override
        public Optional<Integer> getIoRatio() {
            if (ioRatio != null) {
                return Optional.of(ioRatio);
            }
            return Optional.empty();
        }

        /**
         * @return The name of the configured executor to use
         */
        @Override
        public Optional<String> getExecutorName() {
            if (executor != null) {
                return Optional.of(executor);
            }
            return Optional.empty();
        }

        @Override
        public int getNumThreads() {
            return threads;
        }

        @Override
        public boolean isPreferNativeTransport() {
            return preferNativeTransport;
        }

        @Override
        public Duration getShutdownQuietPeriod() {
            return shutdownQuietPeriod;
        }

        @Override
        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }
    }
}
