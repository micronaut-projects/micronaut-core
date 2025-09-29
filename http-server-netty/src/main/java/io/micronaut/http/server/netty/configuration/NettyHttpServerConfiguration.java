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
package io.micronaut.http.server.netty.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolNames;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * The default size of the largest data that can be encoded using the zstd algorithm.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAX_ZSTD_ENCODE_SIZE = 1024 * 1024 * 32;

    /**
     * The default configuration for boolean flag indicating whether to add connection header `keep-alive` to responses with HttpStatus > 499.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_KEEP_ALIVE_ON_SERVER_ERROR = true;

    /**
     * The default value for eager parsing.
     *
     * @since 4.0.0
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_EAGER_PARSING = false;

    /**
     * The default value for eager parsing.
     *
     * @since 4.0.0
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_JSON_BUFFER_MAX_COMPONENTS = 4096;
    /**
     * Default value for {@link Http3Settings#getInitialMaxData()}.
     *
     * @since 4.0.0
     */
    @Experimental
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_HTTP3_INITIAL_MAX_DATA = 10000000;
    /**
     * Default value for {@link Http3Settings#getInitialMaxStreamDataBidirectionalLocal()}.
     *
     * @since 4.0.0
     */
    @Experimental
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_LOCAL = 1000000;
    /**
     * Default value for {@link Http3Settings#getInitialMaxStreamDataBidirectionalRemote()}.
     *
     * @since 4.0.0
     */
    @Experimental
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE = 1000000;
    /**
     * Default value for {@link Http3Settings#getInitialMaxStreamsBidirectional()}.
     *
     * @since 4.0.0
     */
    @Experimental
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL = 100;

    /**
     * Default value for {@link #formMaxFields}.
     *
     * @since 4.6.0
     */
    public static final int DEFAULT_FORM_MAX_FIELDS = 128;

    /**
     * Default value for {@link #formMaxBufferedBytes}.
     *
     * @since 4.6.0
     */
    public static final int DEFAULT_FORM_MAX_BUFFERED_BYTES = 1024;

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServerConfiguration.class);

    private final List<ChannelPipelineListener> pipelineCustomizers;

    private HttpServerType serverType = HttpServerType.STREAMED;

    private Map<ChannelOption, Object> childOptions = Collections.emptyMap();
    private Map<ChannelOption, Object> options = Collections.emptyMap();
    private Worker worker;
    private Parent parent;
    private FileTypeHandlerConfiguration fileTypeHandlerConfiguration = new FileTypeHandlerConfiguration();
    private int maxInitialLineLength = DEFAULT_MAXINITIALLINELENGTH;
    private int maxHeaderSize = DEFAULT_MAXHEADERSIZE;
    private int maxChunkSize = DEFAULT_MAXCHUNKSIZE;
    private int maxH2cUpgradeRequestSize = DEFAULT_MAXCHUNKSIZE; // same default as maxChunkSize, we don't want to buffer super long bodies

    private boolean closeOnExpectationFailed = false;
    private boolean chunkedSupported = DEFAULT_CHUNKSUPPORTED;
    private boolean validateHeaders = DEFAULT_VALIDATEHEADERS;
    private int initialBufferSize = DEFAULT_INITIALBUFFERSIZE;
    private LogLevel logLevel;
    private int compressionThreshold = DEFAULT_COMPRESSIONTHRESHOLD;
    private int compressionLevel = DEFAULT_COMPRESSIONLEVEL;
    private int maxZstdEncodeSize = DEFAULT_MAX_ZSTD_ENCODE_SIZE;
    private boolean useNativeTransport = DEFAULT_USE_NATIVE_TRANSPORT;
    private String fallbackProtocol = ApplicationProtocolNames.HTTP_1_1;
    private AccessLogger accessLogger;
    private Http2Settings http2Settings = new Http2Settings();
    private Http3Settings http3Settings = new Http3Settings();
    private boolean keepAliveOnServerError = DEFAULT_KEEP_ALIVE_ON_SERVER_ERROR;
    private String pcapLoggingPathPattern = null;
    private List<NettyListenerConfiguration> listeners = null;
    private boolean eagerParsing = DEFAULT_EAGER_PARSING;
    private int jsonBufferMaxComponents = DEFAULT_JSON_BUFFER_MAX_COMPONENTS;
    private boolean legacyMultiplexHandlers = false;
    private int formMaxFields = DEFAULT_FORM_MAX_FIELDS;
    private int formMaxBufferedBytes = DEFAULT_FORM_MAX_BUFFERED_BYTES;
    private boolean requestDecompressionEnabled = true;

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
     * @return Sets the server type.
     * @see HttpServerType
     */
    @NonNull
    public HttpServerType getServerType() {
        return serverType;
    }

    /**
     * If a 100-continue response is detected but the content length is too large then true means close the connection. otherwise the connection will remain open and data will be consumed and discarded until the next request is received.
     *
     * <p>only relevant when {@link HttpServerType#FULL_CONTENT} is set</p>
     * @return True if the connection should be closed
     * @see #setServerType(HttpServerType)
     * @see io.netty.handler.codec.http.HttpObjectAggregator
     */
    public boolean isCloseOnExpectationFailed() {
        return closeOnExpectationFailed;
    }

    /**
     * If a 100-continue response is detected but the content length is too large then true means close the connection. otherwise the connection will remain open and data will be consumed and discarded until the next request is received.
     *
     * <p>only relevant when {@link HttpServerType#FULL_CONTENT} is set</p>
     * @param closeOnExpectationFailed  True if the connection should be closed
     * @see #setServerType(HttpServerType)
     * @see io.netty.handler.codec.http.HttpObjectAggregator
     */
    public void setCloseOnExpectationFailed(boolean closeOnExpectationFailed) {
        this.closeOnExpectationFailed = closeOnExpectationFailed;
    }

    /**
     * Set the server type.
     *
     * @param serverType The server type
     */
    public void setServerType(@Nullable HttpServerType serverType) {
        if (serverType != null) {
            this.serverType = serverType;
        }
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
     * Returns the Http3Settings.
     * @return The Http3Settings.
     */
    @Experimental
    public Http3Settings getHttp3() {
        return http3Settings;
    }

    /**
     * Sets the Http3Settings.
     * @param http3Settings The Http3Settings.
     */
    @Experimental
    public void setHttp3Settings(Http3Settings http3Settings) {
        if (http3Settings != null) {
            this.http3Settings = http3Settings;
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
     * The maximum size of the body of the HTTP1.1 request used to upgrade a connection to HTTP2 clear-text (h2c).
     * This initial request cannot be streamed and is instead buffered in full, so the default value
     * ({@value #DEFAULT_MAXCHUNKSIZE}) is relatively small. <i>If this value is too small for your use case,
     * instead consider using an empty initial "upgrade request" (e.g. {@code OPTIONS /}), or switch to normal
     * HTTP2.</i>
     * <p>
     * <i>Does not affect normal HTTP2 (TLS).</i>
     *
     * @return The maximum content length of the request.
     */
    public int getMaxH2cUpgradeRequestSize() {
        return maxH2cUpgradeRequestSize;
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
     * <p>
     * A value {@code < 0} indicates compression should never be enabled.
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
     * The default maximum size of data that can be encoded using the zstd algorithm.
     * Default value ({@value #DEFAULT_MAX_ZSTD_ENCODE_SIZE}).
     *
     * @return The maximum size of data that can be encoded using the zstd algorithm.
     */
    public int getMaxZstdEncodeSize() {
        return maxZstdEncodeSize;
    }

    /**
     * @return The Netty child channel options.
     * @see io.netty.bootstrap.ServerBootstrap#childOption(io.netty.channel.ChannelOption, Object)
     */
    public Map<ChannelOption, Object> getChildOptions() {
        return childOptions;
    }

    /**
     * @return The Netty channel options.
     * @see io.netty.bootstrap.ServerBootstrap#childOption(io.netty.channel.ChannelOption, Object)
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
     * @return The file type handler configuration.
     * @since 3.1.0
     */
    public @NonNull FileTypeHandlerConfiguration getFileTypeHandlerConfiguration() {
        return fileTypeHandlerConfiguration;
    }

    /**
     * Sets the file type handler configuration.
     * @param fileTypeHandlerConfiguration The file type handler configuration
     * @since 3.1.0
     */
    @Inject
    public void setFileTypeHandlerConfiguration(@NonNull FileTypeHandlerConfiguration fileTypeHandlerConfiguration) {
        if (fileTypeHandlerConfiguration != null) {
            this.fileTypeHandlerConfiguration = fileTypeHandlerConfiguration;
        }
    }

    /**
     * @return Configuration for the parent {@link io.netty.channel.EventLoopGroup}
     */
    public Parent getParent() {
        return parent;
    }

    /**
     * @return True if the connection should be kept alive on internal server errors
     */
    public boolean isKeepAliveOnServerError() {
        return keepAliveOnServerError;
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
     * Sets the maximum size of the body of the HTTP1.1 request used to upgrade a connection to HTTP2 clear-text (h2c).
     * This initial request cannot be streamed and is instead buffered in full, so the default value
     * ({@value #DEFAULT_MAXCHUNKSIZE}) is relatively small. <i>If this value is too small for your use case,
     * instead consider using an empty initial "upgrade request" (e.g. {@code OPTIONS /}), or switch to normal
     * HTTP2.</i>
     * <p>
     * <i>Does not affect normal HTTP2 (TLS).</i>
     * @param maxH2cUpgradeRequestSize The maximum content length of the request.
     */
    public void setMaxH2cUpgradeRequestSize(int maxH2cUpgradeRequestSize) {
        this.maxH2cUpgradeRequestSize = maxH2cUpgradeRequestSize;
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
     * Sets the maximum size of data that can be encoded using the zstd algorithm. Default value ({@value #DEFAULT_MAX_ZSTD_ENCODE_SIZE}).
     *
     * @param maxZstdEncodeSize The maximum size of block.
     */
    public void setMaxZstdEncodeSize(int maxZstdEncodeSize) {
        this.maxZstdEncodeSize = maxZstdEncodeSize;
    }

    /**
     * Whether to send connection keep alive on internal server errors. Default value ({@value DEFAULT_KEEP_ALIVE_ON_SERVER_ERROR}).
     * @param keepAliveOnServerError The keep alive on server error flag
     */
    public void setKeepAliveOnServerError(boolean keepAliveOnServerError) {
        this.keepAliveOnServerError = keepAliveOnServerError;
    }

    /**
     * The path pattern to use for logging incoming connections to pcap. This is an unsupported option: Behavior may
     * change, or it may disappear entirely, without notice!
     *
     * @return The path pattern, or {@code null} if logging is disabled.
     */
    @Internal
    public String getPcapLoggingPathPattern() {
        return pcapLoggingPathPattern;
    }

    /**
     * The path pattern to use for logging incoming connections to pcap. This is an unsupported option: Behavior may
     * change, or it may disappear entirely, without notice!
     *
     * @param pcapLoggingPathPattern The path pattern, or {@code null} to disable logging.
     */
    @Internal
    public void setPcapLoggingPathPattern(String pcapLoggingPathPattern) {
        this.pcapLoggingPathPattern = pcapLoggingPathPattern;
    }

    /**
     * Get the explicit netty listener configurations, or {@code null} if they should be implicit.
     * @return The listeners
     */
    public List<NettyListenerConfiguration> getListeners() {
        return listeners;
    }

    /**
     * Set the explicit netty listener configurations, or {@code null} if they should be implicit.
     * @param listeners The listeners
     */
    public void setListeners(List<NettyListenerConfiguration> listeners) {
        this.listeners = listeners;
    }

    /**
     * Parse incoming JSON data eagerly, before route binding. Default value
     * {@value DEFAULT_EAGER_PARSING}.
     *
     * @return Whether to parse incoming JSON data eagerly before route binding
     * @since 4.0.0
     */
    public boolean isEagerParsing() {
        return eagerParsing;
    }

    /**
     * Parse incoming JSON data eagerly, before route binding. Default value
     * {@value DEFAULT_EAGER_PARSING}.
     *
     * @param eagerParsing Whether to parse incoming JSON data eagerly before route binding
     * @since 4.0.0
     */
    public void setEagerParsing(boolean eagerParsing) {
        this.eagerParsing = eagerParsing;
    }

    /**
     * Maximum number of buffers to keep around in JSON parsing before they should be consolidated.
     * Defaults to {@value #DEFAULT_JSON_BUFFER_MAX_COMPONENTS}.
     *
     * @return The maximum number of components
     * @since 4.0.0
     */
    public int getJsonBufferMaxComponents() {
        return jsonBufferMaxComponents;
    }

    /**
     * Maximum number of buffers to keep around in JSON parsing before they should be consolidated.
     * Defaults to {@value #DEFAULT_JSON_BUFFER_MAX_COMPONENTS}.
     *
     * @param jsonBufferMaxComponents The maximum number of components
     * @since 4.0.0
     */
    public void setJsonBufferMaxComponents(int jsonBufferMaxComponents) {
        this.jsonBufferMaxComponents = jsonBufferMaxComponents;
    }

    /**
     * Whether request content decompression is enabled in the Netty HTTP server.
     * When disabled, the server will not decode Content-Encoding or Transfer-Encoding on requests
     * and will pass the compressed body and headers through unchanged.
     * Default: true.
     *
     * @return true if automatic request content decompression is enabled
     */
    public boolean isRequestDecompressionEnabled() {
        return requestDecompressionEnabled;
    }

    /**
     * Enable or disable automatic request content decompression in the Netty HTTP server.
     * Disabling this can be useful for proxy or testing scenarios where the compressed payload
     * and Content-Encoding header need to be observed.
     *
     * @param requestDecompressionEnabled true to enable decompression, false to disable it
     */
    public void setRequestDecompressionEnabled(boolean requestDecompressionEnabled) {
        this.requestDecompressionEnabled = requestDecompressionEnabled;
    }

    /**
     * Prior to 4.4.0, the Micronaut HTTP server used a multi-pipeline approach for handling HTTP/2
     * connections where every request got its own netty pipeline with HTTP/2 to HTTP/1.1
     * converters on the pipeline. This allowed for using mostly unchanged HTTP/1.1 in the request
     * handling and any {@link io.micronaut.http.server.netty.NettyServerCustomizer}s.
     * <p>
     * As of 4.4.0, this approach was replaced with a more performant HTTP/2-specific
     * implementation. This means worse compatibility with HTTP/1.1-based code paths and
     * customizers, however. Setting this option to {@code true} returns to the old behavior.
     *
     * @return Whether to enable the legacy multi-pipeline multiplex handlers for HTTP/2 connections
     */
    public boolean isLegacyMultiplexHandlers() {
        return legacyMultiplexHandlers;
    }

    /**
     * Prior to 4.4.0, the Micronaut HTTP server used a multi-pipeline approach for handling HTTP/2
     * connections where every request got its own netty pipeline with HTTP/2 to HTTP/1.1
     * converters on the pipeline. This allowed for using mostly unchanged HTTP/1.1 in the request
     * handling and any {@link io.micronaut.http.server.netty.NettyServerCustomizer}s.
     * <p>
     * As of 4.4.0, this approach was replaced with a more performant HTTP/2-specific
     * implementation. This means worse compatibility with HTTP/1.1-based code paths and
     * customizers, however. Setting this option to {@code true} returns to the old behavior.
     *
     * @param legacyMultiplexHandlers  Whether to enable the legacy multi-pipeline multiplex
     *                                 handlers for HTTP/2 connections
     */
    public void setLegacyMultiplexHandlers(boolean legacyMultiplexHandlers) {
        this.legacyMultiplexHandlers = legacyMultiplexHandlers;
    }

    /**
     * The maximum number of form fields permitted in a request.
     *
     * @return The maximum number of form fields
     * @since 4.6.0
     */
    public int getFormMaxFields() {
        return formMaxFields;
    }

    /**
     * The maximum number of form fields permitted in a request.
     *
     * @param formMaxFields The maximum number of form fields
     * @since 4.6.0
     */
    public void setFormMaxFields(int formMaxFields) {
        this.formMaxFields = formMaxFields;
    }

    /**
     * The maximum number of bytes the form / multipart decoders are allowed to buffer internally.
     * This sets a limit on form field size.
     *
     * @return The maximum number of buffered bytes
     * @since 4.6.0
     */
    public int getFormMaxBufferedBytes() {
        return formMaxBufferedBytes;
    }

    /**
     * The maximum number of bytes the form / multipart decoders are allowed to buffer internally.
     * This sets a limit on form field size.
     *
     * @param formMaxBufferedBytes The maximum number of buffered bytes
     * @since 4.6.0
     */
    public void setFormMaxBufferedBytes(int formMaxBufferedBytes) {
        this.formMaxBufferedBytes = formMaxBufferedBytes;
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
         * @deprecated The {@code SETTINGS_ENABLE_PUSH} setting makes no sense when sent by the
         * server, and clients must reject any setting except {@code false} (the default) according
         * to the spec.
         */
        @Deprecated
        public Boolean getPushEnabled() {
            return settings.pushEnabled();
        }

        /**
         * Does nothing.
         *
         * @param enabled The {@code SETTINGS_ENABLE_PUSH} value.
         * @deprecated The {@code SETTINGS_ENABLE_PUSH} setting makes no sense when sent by the
         * server, and clients must reject any setting except {@code false} (the default) according
         * to the spec. Netty will refuse to write this setting altogether. To prevent this, this
         * setter now does nothing and will be removed in a future release.
         */
        @Deprecated
        public void setPushEnabled(Boolean enabled) {
            // deprecated
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
     * Configuration for the experimental HTTP/3 server.
     */
    @ConfigurationProperties("http3")
    @Experimental
    public static final class Http3Settings {
        private int initialMaxData = DEFAULT_HTTP3_INITIAL_MAX_DATA;
        private int initialMaxStreamDataBidirectionalLocal = DEFAULT_HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_LOCAL;
        private int initialMaxStreamDataBidirectionalRemote = DEFAULT_HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE;
        private int initialMaxStreamsBidirectional = DEFAULT_HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL;

        /**
         * QUIC initial_max_data setting, see RFC 9000.
         *
         * @return The initial_max_data setting
         */
        public int getInitialMaxData() {
            return initialMaxData;
        }

        /**
         * QUIC initial_max_data setting, see RFC 9000.
         *
         * @param initialMaxData The initial_max_data setting
         */
        public void setInitialMaxData(int initialMaxData) {
            this.initialMaxData = initialMaxData;
        }

        /**
         * QUIC initial_max_stream_data_bidi_local setting, see RFC 9000.
         *
         * @return The initial_max_stream_data_bidi_local setting
         */
        public int getInitialMaxStreamDataBidirectionalLocal() {
            return initialMaxStreamDataBidirectionalLocal;
        }

        /**
         * QUIC initial_max_stream_data_bidi_local setting, see RFC 9000.
         *
         * @param initialMaxStreamDataBidirectionalLocal The initial_max_stream_data_bidi_local setting
         */
        public void setInitialMaxStreamDataBidirectionalLocal(int initialMaxStreamDataBidirectionalLocal) {
            this.initialMaxStreamDataBidirectionalLocal = initialMaxStreamDataBidirectionalLocal;
        }

        /**
         * QUIC initial_max_stream_data_bidi_remote setting, see RFC 9000.
         *
         * @return The initial_max_stream_data_bidi_remote setting
         */
        public int getInitialMaxStreamDataBidirectionalRemote() {
            return initialMaxStreamDataBidirectionalRemote;
        }

        /**
         * QUIC initial_max_stream_data_bidi_remote setting, see RFC 9000.
         *
         * @param initialMaxStreamDataBidirectionalRemote The initial_max_stream_data_bidi_remote setting
         */
        public void setInitialMaxStreamDataBidirectionalRemote(int initialMaxStreamDataBidirectionalRemote) {
            this.initialMaxStreamDataBidirectionalRemote = initialMaxStreamDataBidirectionalRemote;
        }

        /**
         * QUIC initial_max_streams_bidi setting, see RFC 9000.
         *
         * @return The initial_max_streams_bidi setting
         */
        public int getInitialMaxStreamsBidirectional() {
            return initialMaxStreamsBidirectional;
        }

        /**
         * QUIC initial_max_streams_bidi setting, see RFC 9000.
         *
         * @param initialMaxStreamsBidirectional The initial_max_streams_bidi setting
         */
        public void setInitialMaxStreamsBidirectional(int initialMaxStreamsBidirectional) {
            this.initialMaxStreamsBidirectional = initialMaxStreamsBidirectional;
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
        private List<String> exclusions;

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

        /**
         * @return The URI patterns to exclude from the access log.
         */
        public List<String> getExclusions() {
            return exclusions;
        }

        /**
         * Sets the URI patterns to be excluded from the access log.
         *
         * @param exclusions A list of regular expression patterns to be excluded from the access logger if the request URI matches.
         *
         * @see java.util.regex.Pattern#compile(String)
         */
        public void setExclusions(List<String> exclusions) {
            this.exclusions = exclusions;
        }
    }

    /**
     * Configuration for Netty worker.
     */
    @ConfigurationProperties("worker")
    @Named("netty-server-worker-event-loop")
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
    @Requires(missingProperty = EventLoopGroupConfiguration.EVENT_LOOPS + ".parent")
    @Named("netty-server-parent-event-loop")
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
     * Allows configuration of properties for the {@link io.micronaut.http.server.netty.body.AbstractFileBodyWriter}.
     *
     * @author James Kleeh
     * @author graemerocher
     * @since 3.1.0
     * @deprecated Replaced by {@link HttpServerConfiguration.FileTypeHandlerConfiguration}
     */
    @Deprecated(since = "4.8.0", forRemoval = true)
    @ConfigurationProperties("responses.file")
    public static class FileTypeHandlerConfiguration {

        /**
         * The default cache seconds.
         */
        @SuppressWarnings("WeakerAccess")
        public static final int DEFAULT_CACHESECONDS = 60;

        private int cacheSeconds = DEFAULT_CACHESECONDS;
        private CacheControlConfiguration cacheControl = new CacheControlConfiguration();

        /**
         * Default constructor.
         */
        public FileTypeHandlerConfiguration() {
        }

        /**
         * Deprecated constructor.
         *
         * @param cacheSeconds Deprecated constructor parameter
         * @param isPublic Deprecated constructor parameter
         */
        @Deprecated
        @Inject
        public FileTypeHandlerConfiguration(@Nullable @Property(name = "netty.responses.file.cache-seconds") Integer cacheSeconds,
                                            @Nullable @Property(name = "netty.responses.file.cache-control.public") Boolean isPublic) {
        }

        /**
         * @return the cache seconds
         */
        public int getCacheSeconds() {
            return cacheSeconds;
        }

        /**
         * Cache Seconds. Default value ({@value #DEFAULT_CACHESECONDS}).
         * @param cacheSeconds cache seconds
         */
        public void setCacheSeconds(int cacheSeconds) {
            this.cacheSeconds = cacheSeconds;
        }

        /**
         * @return The cache control configuration
         */
        public CacheControlConfiguration getCacheControl() {
            return cacheControl;
        }

        /**
         * Sets the cache control configuration.
         *
         * @param cacheControl The cache control configuration
         */
        public void setCacheControl(CacheControlConfiguration cacheControl) {
            this.cacheControl = cacheControl;
        }

        /**
         * Configuration for the Cache-Control header.
         *
         * @deprecated Replaced by {@link HttpServerConfiguration.FileTypeHandlerConfiguration.CacheControlConfiguration}
         */
        @Deprecated(since = "4.8.0", forRemoval = true)
        @ConfigurationProperties("cache-control")
        public static class CacheControlConfiguration {

            private static final boolean DEFAULT_PUBLIC_CACHE = false;

            private boolean publicCache = DEFAULT_PUBLIC_CACHE;

            /**
             * Sets whether the cache control is public. Default value ({@value #DEFAULT_PUBLIC_CACHE})
             *
             * @param publicCache Public cache value
             */
            public void setPublic(boolean publicCache) {
                this.publicCache = publicCache;
            }

            /**
             * @return True if the cache control should be public
             */
            @NonNull
            public boolean getPublic() {
                return publicCache;
            }
        }
    }

    /**
     * Abstract class for configuring the Netty event loop.
     */
    public abstract static class EventLoopConfig implements EventLoopGroupConfiguration {
        private int threads;
        private double threadCoreRatio = DEFAULT_THREAD_CORE_RATIO;
        private Integer ioRatio;
        private String executor;
        private boolean preferNativeTransport = false;
        private List<String> transport;
        private Duration shutdownQuietPeriod = Duration.ofSeconds(DEFAULT_SHUTDOWN_QUIET_PERIOD);
        private Duration shutdownTimeout = Duration.ofSeconds(DEFAULT_SHUTDOWN_TIMEOUT);
        private String name;
        private boolean loomCarrier = false;

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
         * A named executor service to use for event loop threads
         * (optional). This property is very specialized. In particular,
         * it will <i>not</i> solve read timeouts or fix blocking
         * operations on the event loop, in fact it may do the opposite.
         * Don't use unless you really know what this does.
         *
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
        public double getThreadCoreRatio() {
            return threadCoreRatio;
        }

        /**
         * The number of threads per core to use if {@link #getNumThreads()} is set to 0.
         *
         * @param threadCoreRatio The thread-to-core ratio
         * @since 4.8.0
         */
        public void setThreadCoreRatio(double threadCoreRatio) {
            this.threadCoreRatio = threadCoreRatio;
        }

        @Override
        public boolean isPreferNativeTransport() {
            return preferNativeTransport;
        }

        @Override
        public @NonNull List<String> getTransport() {
            return transport == null ? EventLoopGroupConfiguration.super.getTransport() : transport;
        }

        /**
         * The transports to use for this event loop, in order of preference. Supported values are
         * {@code io_uring,epoll,kqueue,nio}. The first available transport out of those listed will
         * be used (nio is always available). If no listed transport is available, an exception will be
         * thrown.
         * <p>By default, only {@code nio} is used, even if native transports are available. If the
         * legacy {@link #isPreferNativeTransport() prefer-native-transport} property is set to
         * {@code true}, this defaults to {@code io_uring,epoll,kqueue,nio}.
         *
         * @param transport The available transports, in order of preference
         */
        public void setTransport(@NonNull List<String> transport) {
            this.transport = transport;
        }

        @Override
        public Duration getShutdownQuietPeriod() {
            return shutdownQuietPeriod;
        }

        @Override
        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        @Override
        public boolean isLoomCarrier() {
            return loomCarrier;
        }

        /**
         * @param loomCarrier When set to {@code true}, use a special <i>experimental</i> event
         *                    loop that can also execute virtual threads, in order to improve
         *                    virtual thread performance.
         */
        public void setLoomCarrier(boolean loomCarrier) {
            this.loomCarrier = loomCarrier;
        }
    }

    /**
     * Netty listener configuration.
     *
     * @author yawkat
     * @since 3.5.0
     */
    @EachProperty("listeners")
    public static final class NettyListenerConfiguration {
        private final String name;
        private Family family = Family.TCP;
        private boolean ssl;
        private String keyName;
        private String trustName;
        @Nullable
        private String host;
        private int port;
        private String path;
        private boolean exposeDefaultRoutes = true;
        private boolean supportGracefulShutdown = true;
        private Integer fd = null;
        private Integer acceptedFd = null;
        private boolean bind = true;
        private boolean serverSocket = true;

        /**
         * Constructor.
         *
         * @param name The name of this listener
         */
        @Inject
        public NettyListenerConfiguration(@Parameter String name) {
            this.name = name;
        }

        /**
         * Constructor.
         *
         * @deprecated Please pass the listener name to {@link #NettyListenerConfiguration(String)}
         */
        @Deprecated
        public NettyListenerConfiguration() {
            this("unknown");
        }

        /**
         * Create a TCP listener configuration.
         *
         * @param host The host to bind to
         * @param port The port to bind to
         * @param ssl Whether to enable SSL
         * @param keyName Optional certificate name to use
         * @param trustName Optional trust store name to use
         * @return The configuration with the given settings
         */
        @Internal
        public static NettyListenerConfiguration createTcp(@Nullable String host, int port, boolean ssl, @Nullable String keyName, @Nullable String trustName) {
            NettyListenerConfiguration configuration = new NettyListenerConfiguration(host + ":" + port);
            configuration.setFamily(Family.TCP);
            configuration.setHost(host);
            configuration.setPort(port);
            configuration.setSsl(ssl);
            configuration.setKeyName(keyName);
            configuration.setTrustName(trustName);
            return configuration;
        }

        /**
         * Name of the listener.
         *
         * @return Name of the listener
         */
        public String getName() {
            return name;
        }

        /**
         * The address family of this listener.
         * @return The address family of this listener.
         */
        public Family getFamily() {
            return family;
        }

        /**
         * The address family of this listener.
         * @param family The address family of this listener.
         */
        public void setFamily(@NonNull Family family) {
            Objects.requireNonNull(family, "family");
            this.family = family;
        }

        /**
         * Whether to enable SSL on this listener. Also requires {@link io.micronaut.http.ssl.SslConfiguration#isEnabled()}
         * to be set.
         * @return Whether to enable SSL on this listener.
         */
        public boolean isSsl() {
            return ssl;
        }

        /**
         * Whether to enable SSL on this listener. Also requires {@link io.micronaut.http.ssl.SslConfiguration#isEnabled()}
         * to be set.
         * @param ssl Whether to enable SSL on this listener.
         */
        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        /**
         * Name of a {@link io.micronaut.http.ssl.CertificateProvider} bean that supplies the private key and certificate chain
         * for this listener's SSL context. When set, Micronaut resolves the named provider and subscribes to its keystore updates.
         * If not set, the global SSL configuration (e.g. {@link io.micronaut.http.ssl.SslConfiguration#getKeyName()}) or legacy
         * keystore properties may be used instead.
         * @return the name of the certificate provider for key material on this listener or {@code null}
         */
        @Nullable
        public String getKeyName() {
            return keyName;
        }

        /**
         * Name of a {@link io.micronaut.http.ssl.CertificateProvider} bean that supplies the private key and certificate chain
         * for this listener's SSL context. When set, Micronaut resolves the named provider and subscribes to its keystore updates.
         * If not set, the global SSL configuration (e.g. {@link io.micronaut.http.ssl.SslConfiguration#getKeyName()}) or legacy
         * keystore properties may be used instead.
         * @param keyName the name of the certificate provider for key material on this listener or {@code null}
         */
        public void setKeyName(@Nullable String keyName) {
            this.keyName = keyName;
        }

        /**
         * Name of a {@link io.micronaut.http.ssl.CertificateProvider} bean that supplies the trust material (trusted certificates)
         * for this listener's SSL context. When set, Micronaut resolves the named provider and subscribes to its keystore updates.
         * If not set, the global SSL configuration (e.g. {@link io.micronaut.http.ssl.SslConfiguration#getTrustName()}) or legacy
         * trust store properties may be used instead.
         * @return the name of the certificate provider for trust material on this listener or {@code null}
         */
        @Nullable
        public String getTrustName() {
            return trustName;
        }

        /**
         * Name of a {@link io.micronaut.http.ssl.CertificateProvider} bean that supplies the trust material (trusted certificates)
         * for this listener's SSL context. When set, Micronaut resolves the named provider and subscribes to its keystore updates.
         * If not set, the global SSL configuration (e.g. {@link io.micronaut.http.ssl.SslConfiguration#getTrustName()}) or legacy
         * trust store properties may be used instead.
         * @param trustName the name of the certificate provider for trust material on this listener or {@code null}
         */
        public void setTrustName(@Nullable String trustName) {
            this.trustName = trustName;
        }

        /**
         * For TCP listeners, the host to bind to, or {@code null} to bind to all hosts.
         * @return For TCP listeners, the host to bind to, or {@code null} to bind to all hosts.
         */
        @Nullable
        public String getHost() {
            return host;
        }

        /**
         * For TCP listeners, the host to bind to, or {@code null} to bind to all hosts.
         * @param host For TCP listeners, the host to bind to, or {@code null} to bind to all hosts.
         */
        public void setHost(@Nullable String host) {
            this.host = host;
        }

        /**
         * The TCP port to bind to. May be {@code -1} to bind to a random port.
         * @return The TCP port to bind to. May be {@code -1} to bind to a random port.
         */
        public int getPort() {
            return port;
        }

        /**
         * The TCP port to bind to. May be {@code -1} to bind to a random port.
         * @param port The TCP port to bind to. May be {@code -1} to bind to a random port.
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * For UNIX domain sockets, the path of the socket. For abstract domain sockets, this should start with a NUL byte.
         * @return For UNIX domain sockets, the path of the socket. For abstract domain sockets, this should start with a NUL byte.
         */
        public String getPath() {
            return path;
        }

        /**
         * For UNIX domain sockets, the path of the socket. For abstract domain sockets, this should start with a NUL byte.
         * @param path For UNIX domain sockets, the path of the socket. For abstract domain sockets, this should start with a NUL byte.
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Whether to expose default routes on this listener.
         * @return Whether to expose default routes on this listener.
         */
        @Internal
        public boolean isExposeDefaultRoutes() {
            return exposeDefaultRoutes;
        }

        /**
         * Whether to expose default routes on this listener.
         * @param exposeDefaultRoutes Whether to expose default routes on this listener.
         */
        @Internal
        public void setExposeDefaultRoutes(boolean exposeDefaultRoutes) {
            this.exposeDefaultRoutes = exposeDefaultRoutes;
        }

        /**
         * If {@code true} (the default), this listener will stop accepting new connections and
         * terminate any existing ones when a graceful shutdown is initiated. By setting this to
         * {@code false} you can ignore the graceful shutdown, e.g. to keep a management port up
         * while the shutdown of other listeners is in progress.
         *
         * @return Whether to support shutting down gracefully
         */
        public boolean isSupportGracefulShutdown() {
            return supportGracefulShutdown;
        }

        /**
         * If {@code true} (the default), this listener will stop accepting new connections and
         * terminate any existing ones when a graceful shutdown is initiated. By setting this to
         * {@code false} you can ignore the graceful shutdown, e.g. to keep a management port up
         * while the shutdown of other listeners is in progress.
         *
         * @param supportGracefulShutdown Whether to support shutting down gracefully
         */
        public void setSupportGracefulShutdown(boolean supportGracefulShutdown) {
            this.supportGracefulShutdown = supportGracefulShutdown;
        }

        /**
         * The fixed file descriptor for this listener, or {@code null} if a new file descriptor
         * should be opened (the default).
         *
         * @return The file descriptor
         */
        public Integer getFd() {
            return fd;
        }

        /**
         * The fixed file descriptor for this listener, or {@code null} if a new file descriptor
         * should be opened (the default).
         *
         * @param fd The file descriptor
         */
        public void setFd(Integer fd) {
            this.fd = fd;
        }

        /**
         * Whether the server should bind to the socket. {@code true} by default. If set to
         * {@code false}, the socket must already be bound and listening.
         *
         * @return Whether to bind to the socket
         */
        public boolean isBind() {
            return bind;
        }

        /**
         * Whether the server should bind to the socket. {@code true} by default. If set to
         * {@code false}, the socket must already be bound and listening.
         *
         * @param bind Whether to bind to the socket
         */
        public void setBind(boolean bind) {
            this.bind = bind;
        }

        /**
         * Whether to create a server socket. This is on by default. Turning it off only makes sense
         * in combination with {@link #acceptedFd}.
         *
         * @return {@code true} iff a server socket should be created
         */
        public boolean isServerSocket() {
            return serverSocket;
        }

        /**
         * Whether to create a server socket. This is on by default. Turning it off only makes sense
         * in combination with {@link #acceptedFd}.
         *
         * @param serverSocket {@code true} iff a server socket should be created
         */
        public void setServerSocket(boolean serverSocket) {
            this.serverSocket = serverSocket;
        }

        /**
         * An already accepted socket fd that should be registered to this listener.
         *
         * @return The fd to register
         */
        public Integer getAcceptedFd() {
            return acceptedFd;
        }

        /**
         * An already accepted socket fd that should be registered to this listener.
         *
         * @param acceptedFd The fd to register
         */
        public void setAcceptedFd(Integer acceptedFd) {
            this.acceptedFd = acceptedFd;
        }

        /**
         * Address family enum.
         */
        public enum Family {
            /**
             * TCP socket.
             */
            TCP,
            /**
             * UNIX domain socket.
             */
            UNIX,
            /**
             * QUIC (HTTP/3) listener.
             */
            @Experimental
            QUIC,
        }
    }

    /**
     * Sets the manner in which the HTTP server is configured to receive requests.
     */
    public enum HttpServerType {
        /**
         * Requests are streamed on demand with {@link io.netty.handler.flow.FlowControlHandler} used to control back pressure.
         */
        STREAMED,
        /**
         * Execute controllers only once the full content of the request has been received.
         */
        FULL_CONTENT
    }
}
