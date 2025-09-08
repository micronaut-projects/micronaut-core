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
package io.micronaut.http.client;

import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.logging.LogLevel;
import io.micronaut.runtime.ApplicationConfiguration;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ThreadFactory;

/**
 * Configuration for the {@link HttpClient}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class HttpClientConfiguration {

    /**
     * The default read timeout in seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_READ_TIMEOUT_SECONDS = 10;

    /**
     * The default read idle timeout in minutes.
     */
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_READ_IDLE_TIMEOUT_MINUTES = 5;

    /**
     * The default pool idle timeout in seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_CONNECTION_POOL_IDLE_TIMEOUT_SECONDS = 0;

    /**
     * The default shutdown timeout in millis.
     */
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_SHUTDOWN_QUIET_PERIOD_MILLISECONDS = 1;

    /**
     * The default shutdown timeout in millis.
     */
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_SHUTDOWN_TIMEOUT_MILLISECONDS = 100;

    /**
     * The default max content length in bytes.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAX_CONTENT_LENGTH = 1024 * 1024 * 10; // 10MiB;

    /**
     * The default max header size in bytes.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAX_HEADER_SIZE = 8192;

    /**
     * The default max initial line length for HTTP client codec in bytes.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = 4096;

    /**
     * The default max chunk size for HTTP client codec in bytes.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_MAX_CHUNK_SIZE = 8192;

    /**
     * The default follow redirects value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_FOLLOW_REDIRECTS = true;

    /**
     * The default value.
     */
    public static final boolean DEFAULT_EXCEPTION_ON_ERROR_STATUS = true;

    /**
     * The default value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ALLOW_BLOCK_EVENT_LOOP = false;

    /**
     * The default value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final DnsResolutionMode DEFAULT_DNS_RESOLUTION_MODE = DnsResolutionMode.DEFAULT;

    private Map<String, Object> channelOptions = Collections.emptyMap();

    private Integer numOfThreads = null;

    /**
     * The thread factory to use for creating threads.
     */
    private Class<? extends ThreadFactory> threadFactory;

    private Duration connectTimeout;

    private Duration connectTtl;

    private Duration readTimeout = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS);

    private Duration requestTimeout = null;

    private Duration readIdleTimeout = Duration.of(DEFAULT_READ_IDLE_TIMEOUT_MINUTES, ChronoUnit.MINUTES);

    private Duration connectionPoolIdleTimeout = DEFAULT_CONNECTION_POOL_IDLE_TIMEOUT_SECONDS == 0 ? null : Duration.ofSeconds(DEFAULT_CONNECTION_POOL_IDLE_TIMEOUT_SECONDS);

    private Duration shutdownQuietPeriod = Duration.ofMillis(DEFAULT_SHUTDOWN_QUIET_PERIOD_MILLISECONDS);

    private Duration shutdownTimeout = Duration.ofMillis(DEFAULT_SHUTDOWN_TIMEOUT_MILLISECONDS);

    private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;

    private int maxHeaderSize = DEFAULT_MAX_HEADER_SIZE;

    private Proxy.Type proxyType = Proxy.Type.DIRECT;

    private SocketAddress proxyAddress;

    private String proxyUsername;

    private String proxyPassword;

    private ProxySelector proxySelector;

    private Charset defaultCharset = StandardCharsets.UTF_8;

    private boolean followRedirects = DEFAULT_FOLLOW_REDIRECTS;

    private boolean exceptionOnErrorStatus = DEFAULT_EXCEPTION_ON_ERROR_STATUS;

    private SslConfiguration sslConfiguration = new ClientSslConfiguration();

    private String loggerName;

    private String eventLoopGroup = "default";

    @Deprecated
    @Nullable
    private HttpVersion httpVersion = null;

    private HttpVersionSelection.PlaintextMode plaintextMode = HttpVersionSelection.PlaintextMode.HTTP_1;

    private List<String> alpnModes = Arrays.asList(
        HttpVersionSelection.ALPN_HTTP_2,
        HttpVersionSelection.ALPN_HTTP_1
    );

    private LogLevel logLevel;

    private boolean allowBlockEventLoop = DEFAULT_ALLOW_BLOCK_EVENT_LOOP;

    private DnsResolutionMode dnsResolutionMode = DEFAULT_DNS_RESOLUTION_MODE;

    @Nullable
    private String addressResolverGroupName = null;

    private String pcapLoggingPathPattern = null;

    /**
     * Default constructor.
     */
    public HttpClientConfiguration() {
    }

    /**
     * @param applicationConfiguration The application configuration
     */
    public HttpClientConfiguration(ApplicationConfiguration applicationConfiguration) {
        if (applicationConfiguration != null) {
            this.defaultCharset = applicationConfiguration.getDefaultCharset();
        }
    }

    /**
     * Copy constructor.
     *
     * @param copy The client configuration to copy settings from
     */
    public HttpClientConfiguration(HttpClientConfiguration copy) {
        if (copy != null) {
            this.channelOptions = copy.channelOptions;
            this.numOfThreads = copy.numOfThreads;
            this.connectTimeout = copy.connectTimeout;
            this.connectTtl = copy.connectTtl;
            this.defaultCharset = copy.defaultCharset;
            this.exceptionOnErrorStatus = copy.exceptionOnErrorStatus;
            this.eventLoopGroup = copy.eventLoopGroup;
            this.followRedirects = copy.followRedirects;
            this.logLevel = copy.logLevel;
            this.loggerName = copy.loggerName;
            this.maxContentLength = copy.maxContentLength;
            this.maxHeaderSize = copy.maxHeaderSize;
            this.proxyAddress = copy.proxyAddress;
            this.proxyPassword = copy.proxyPassword;
            this.proxySelector = copy.proxySelector;
            this.proxyType = copy.proxyType;
            this.proxyUsername = copy.proxyUsername;
            this.readIdleTimeout = copy.readIdleTimeout;
            this.connectionPoolIdleTimeout = copy.connectionPoolIdleTimeout;
            this.readTimeout = copy.readTimeout;
            this.shutdownTimeout = copy.shutdownTimeout;
            this.shutdownQuietPeriod = copy.shutdownQuietPeriod;
            this.sslConfiguration = copy.sslConfiguration;
            this.threadFactory = copy.threadFactory;
            this.httpVersion = copy.httpVersion;
        }
    }

    /**
     * The HTTP version to use. Defaults to {@link HttpVersion#HTTP_1_1}.
     * @return The http version
     * @deprecated There are now separate settings for HTTP and HTTPS connections. To configure
     * HTTP connections (e.g. for h2c), use {@link #plaintextMode}. To configure ALPN, set
     * {@link #alpnModes}.
     */
    @Deprecated
    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    /**
     * Sets the HTTP version to use. Defaults to {@link HttpVersion#HTTP_1_1}.
     * @param httpVersion The http version
     * @deprecated There are now separate settings for HTTP and HTTPS connections. To configure
     * HTTP connections (e.g. for h2c), use {@link #plaintextMode}. To configure ALPN, set
     * {@link #alpnModes}.
     */
    @Deprecated
    public void setHttpVersion(HttpVersion httpVersion) {
        if (httpVersion != null) {
            this.httpVersion = httpVersion;
        }
    }

    /**
     * [available in the Netty HTTP client].
     *
     * @return The trace logging level
     */
    public Optional<LogLevel> getLogLevel() {
        return Optional.ofNullable(logLevel);
    }

    /**
     * Sets the level to enable trace logging at. Depending on the implementation this
     * may activate additional handlers. For example in Netty this will activate {@code LoggingHandler} at the given level.
     * @param logLevel The trace logging level
     */
    public void setLogLevel(@Nullable LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * [available in the Netty HTTP client].
     *
     * @return The event loop group to use.
     */
    public String getEventLoopGroup() {
        return eventLoopGroup;
    }

    /**
     * @param eventLoopGroup Sets the event loop group to use for the client.
     */
    public void setEventLoopGroup(@NonNull String eventLoopGroup) {
        ArgumentUtils.requireNonNull("eventLoopGroup", eventLoopGroup);
        this.eventLoopGroup = eventLoopGroup;
    }

    /**
     * Obtains the connection pool configuration.
     *
     * @return The connection pool configuration.
     */
    public abstract ConnectionPoolConfiguration getConnectionPoolConfiguration();

    /**
     * @return The {@link SslConfiguration} for the client
     */
    public SslConfiguration getSslConfiguration() {
        return sslConfiguration;
    }

    /**
     * Sets the SSL configuration for the client.
     *
     * @param sslConfiguration The SSL configuration
     */
    public void setSslConfiguration(SslConfiguration sslConfiguration) {
        this.sslConfiguration = sslConfiguration;
    }

    /**
     * Obtains the WebSocket compression configuration.
     *
     * @return The WebSocket compression configuration.
     * @since 4.3.0
     */
    @Nullable
    public WebSocketCompressionConfiguration getWebSocketCompressionConfiguration() {
        return null;
    }

    /**
     * @return Whether redirects should be followed
     */
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    /**
     * @return Whether throwing an exception upon HTTP error status (&gt;= 400) is preferred.
     */
    public boolean isExceptionOnErrorStatus() {
        return exceptionOnErrorStatus;
    }

    /**
     * Sets whether throwing an exception upon HTTP error status (&gt;= 400) is preferred. Default value ({@link io.micronaut.http.client.HttpClientConfiguration#DEFAULT_EXCEPTION_ON_ERROR_STATUS})
     *
     * @param exceptionOnErrorStatus Whether
     */
    public void setExceptionOnErrorStatus(boolean exceptionOnErrorStatus) {
        this.exceptionOnErrorStatus = exceptionOnErrorStatus;
    }

    /**
     * @return The client-specific logger name if configured
     */
    public Optional<String> getLoggerName() {
        return Optional.ofNullable(loggerName);
    }

    /**
     * Sets the client-specific logger name.
     *
     * @param loggerName The name of the logger.
     */
    public void setLoggerName(@Nullable String loggerName) {
        this.loggerName = loggerName;
    }

    /**
     * Sets whether redirects should be followed. Default value ({@link io.micronaut.http.client.HttpClientConfiguration#DEFAULT_FOLLOW_REDIRECTS}).
     *
     * @param followRedirects Whether redirects should be followed
     */
    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    /**
     * [available in the Netty HTTP client].
     *
     * @return The default charset to use
     */
    public Charset getDefaultCharset() {
        return defaultCharset;
    }

    /**
     * Sets the default charset to use. Default value (UTF-8);
     *
     * @param defaultCharset The charset to use
     */
    public void setDefaultCharset(Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    /**
     * [available in the Netty HTTP client].
     *
     * @return The Client channel options.
     */
    public Map<String, Object> getChannelOptions() {
        return channelOptions;
    }

    /**
     * @param channelOptions The Client channel options
     */
    public void setChannelOptions(Map<String, Object> channelOptions) {
        this.channelOptions = channelOptions;
    }

    /**
     * @return The default read timeout. Defaults to 10 seconds.
     */
    public Optional<Duration> getReadTimeout() {
        return Optional.ofNullable(readTimeout);
    }

    /**
     * The request timeout for non-streaming requests. This is the maximum time until the response
     * must be completely received. Defaults to one second more than read-timeout.
     *
     * @return The request timeout
     * @since 4.6.0
     */
    @Nullable
    @NextMajorVersion("Set a default that isn't just requestTimeout+1 in DefaultHttpClient")
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * The request timeout for non-streaming requests. This is the maximum time until the response
     * must be completely received. Defaults to one second more than read-timeout.
     *
     * @param requestTimeout The request timeout
     */
    public void setRequestTimeout(@Nullable Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    /**
     * For WebSockets, the {@link #getReadTimeout()} method does not apply instead a configurable
     * idle timeout is applied.
     * [available in the Netty HTTP client]
     *
     * @return The default amount of time to allow read operation connections  to remain idle
     */
    @NextMajorVersion("Rename to websocket-idle-timeout")
    public Optional<Duration> getReadIdleTimeout() {
        return Optional.ofNullable(readIdleTimeout);
    }

    /**
     * [available in the Netty HTTP client].
     *
     * @return The idle timeout for connection in the client connection pool. Defaults to 0.
     */
    public Optional<Duration> getConnectionPoolIdleTimeout() {
        return Optional.ofNullable(connectionPoolIdleTimeout);
    }

    /**
     * @return The default connect timeout. Defaults to Netty default.
     */
    public Optional<Duration> getConnectTimeout() {
        return Optional.ofNullable(connectTimeout);
    }

    /**
     * [available in the Netty HTTP client].
     *
     * @return The connectTtl.
     */
    public Optional<Duration> getConnectTtl() {
        return Optional.ofNullable(connectTtl);
    }

    /**
     * The amount of quiet period for shutdown.
     * [available in the Netty HTTP client]
     *
     * @return The shutdown timeout
     */
    public Optional<Duration> getShutdownQuietPeriod() {
        return Optional.ofNullable(shutdownQuietPeriod);
    }

    /**
     * The amount of time to wait for shutdown.
     * [available in the Netty HTTP client]
     *
     * @return The shutdown timeout
     */
    public Optional<Duration> getShutdownTimeout() {
        return Optional.ofNullable(shutdownTimeout);
    }

    /**
     * Sets the amount of quiet period for shutdown of client thread pools. Default value ({@value io.micronaut.http.client.HttpClientConfiguration#DEFAULT_SHUTDOWN_QUIET_PERIOD_MILLISECONDS} milliseconds).
     *
     * If a task is submitted during the quiet period, it will be accepted and the quiet period will start over.
     *
     * @param shutdownQuietPeriod The shutdown quiet period
     */
    public void setShutdownQuietPeriod(@Nullable Duration shutdownQuietPeriod) {
        this.shutdownQuietPeriod = shutdownQuietPeriod;
    }

    /**
     * Sets the amount of time to wait for shutdown of client thread pools. Default value ({@value io.micronaut.http.client.HttpClientConfiguration#DEFAULT_SHUTDOWN_TIMEOUT_MILLISECONDS} milliseconds).
     *
     * @param shutdownTimeout The shutdown time
     */
    public void setShutdownTimeout(@Nullable Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    /**
     * Sets the read timeout. Default value ({@value io.micronaut.http.client.HttpClientConfiguration#DEFAULT_READ_TIMEOUT_SECONDS} seconds).
     *
     * @param readTimeout The read timeout
     */
    public void setReadTimeout(@Nullable Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * For WebSockets, the {@link #getReadTimeout()} method does not apply instead a configurable
     * idle timeout is applied.
     *
     * @param readIdleTimeout The read idle time
     */
    public void setReadIdleTimeout(@Nullable Duration readIdleTimeout) {
        this.readIdleTimeout = readIdleTimeout;
    }

    /**
     * Sets the idle timeout for connection in the client connection pool. Defaults to 0.
     *
     * @param connectionPoolIdleTimeout The connection pool idle timeout
     */
    public void setConnectionPoolIdleTimeout(@Nullable Duration connectionPoolIdleTimeout) {
        this.connectionPoolIdleTimeout = connectionPoolIdleTimeout;
    }

    /**
     * Sets the connect timeout.
     *
     * @param connectTimeout The connect timeout
     */
    public void setConnectTimeout(@Nullable Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Sets the connect timeout.
     *
     * @param connectTtl The connect timeout
     */
    public void setConnectTtl(@Nullable Duration connectTtl) {
        this.connectTtl = connectTtl;
    }

    /**
     * [available in the Netty HTTP client].
     *
     * @return The number of threads the client should use for requests
     */
    public OptionalInt getNumOfThreads() {
        return numOfThreads != null ? OptionalInt.of(numOfThreads) : OptionalInt.empty();
    }

    /**
     * Sets the number of threads the client should use for requests.
     *
     * @param numOfThreads The number of threads the client should use for requests
     */
    public void setNumOfThreads(@Nullable Integer numOfThreads) {
        this.numOfThreads = numOfThreads;
    }

    /**
     * [available in the Netty HTTP client].
     *
     * @return An {@link Optional} {@code ThreadFactory}
     */
    public Optional<Class<? extends ThreadFactory>> getThreadFactory() {
        return Optional.ofNullable(threadFactory);
    }

    /**
     * Sets a thread factory.
     *
     * @param threadFactory The thread factory
     */
    public void setThreadFactory(Class<? extends ThreadFactory> threadFactory) {
        this.threadFactory = threadFactory;
    }

    /**
     * [available in the Netty HTTP client].
     *
     * @return The maximum content length the client can consume
     */
    public int getMaxContentLength() {
        return maxContentLength;
    }

    /**
     * Sets the maximum content length the client can consume. Default value ({@value io.micronaut.http.client.HttpClientConfiguration#DEFAULT_MAX_CONTENT_LENGTH} =&gt; 10MB).
     *
     * @param maxContentLength The maximum content length the client can consume
     */
    public void setMaxContentLength(@ReadableBytes int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    /**
     * [available in the Netty HTTP client].
     *
     * @return The maximum header size the client can handle
     */
    public int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    /**
     * Sets the maximum header size the client can handle. Default value ({@value io.micronaut.http.client.HttpClientConfiguration#DEFAULT_MAX_HEADER_SIZE}).
     *
     * @param maxHeaderSize The maximum header size the client can handle
     */
    public void setMaxHeaderSize(@ReadableBytes int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    /**
     * <p>The proxy to use. For authentication specify http.proxyUser and http.proxyPassword system properties.</p>
     *
     * <p>Alternatively configure a {@code java.net.ProxySelector}</p>
     *
     * @return The proxy type
     */
    public Proxy.Type getProxyType() {
        return proxyType;
    }

    /**
     * @param proxyType The proxy type
     */
    public void setProxyType(Proxy.Type proxyType) {
        this.proxyType = proxyType;
    }

    /**
     * The proxy to use. For authentication specify http.proxyUser and http.proxyPassword system properties.
     * <p>
     * Alternatively configure a {@code java.net.ProxySelector}
     *
     * @return The optional proxy address
     */
    public Optional<SocketAddress> getProxyAddress() {
        return Optional.ofNullable(proxyAddress);
    }

    /**
     * Sets a proxy address.
     *
     * @param proxyAddress The proxy address
     */
    public void setProxyAddress(SocketAddress proxyAddress) {
        this.proxyAddress = proxyAddress;
    }

    /**
     * @return The proxy username to use
     */
    public Optional<String> getProxyUsername() {
        String type = proxyType.name().toLowerCase();
        return proxyUsername != null ? Optional.of(proxyUsername) : Optional.ofNullable(CachedEnvironment.getProperty(type + ".proxyUser"));
    }

    /**
     * Sets the proxy username to use.
     *
     * @param proxyUsername The proxy username to use
     */
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    /**
     * @return The proxy password to use.
     */
    @SuppressWarnings("WeakerAccess")
    public Optional<String> getProxyPassword() {
        String type = proxyType.name().toLowerCase();
        return proxyPassword != null ? Optional.of(proxyPassword) : Optional.ofNullable(CachedEnvironment.getProperty(type + ".proxyPassword"));
    }

    /**
     * Sets the proxy password.
     *
     * @param proxyPassword The proxy password
     */
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    /**
     * Sets the proxy selector.
     * ProxySelector decides what proxy to use and take precedence over {@link #setProxyAddress(SocketAddress)} and {@link #setProxyType(Proxy.Type)}.
     *
     * @param proxySelector The proxy selector to use
     */
    public void setProxySelector(ProxySelector proxySelector) {
        this.proxySelector = proxySelector;
    }

    /**
     * @return The proxy selector provided
     */
    public Optional<ProxySelector> getProxySelector() {
        return Optional.ofNullable(proxySelector);
    }

    /**
     * Resolves a proxy to use for connection.
     * <p>
     * If ProxySelector is set by {@link #setProxySelector(ProxySelector)} then it constructs URI and pass it to {@link ProxySelector#select(URI)}.
     * First proxy returned by proxy selector will be used. If no proxy is returned by select, then {@link Proxy#NO_PROXY} will be used.
     * <p>
     * If ProxySelector is not set then parameters are ignored and a proxy as defined by {@link #setProxyAddress(SocketAddress)} and {@link #setProxyType(Proxy.Type)} will be returned.
     * If no proxy is defined then parameters are ignored and {@link Proxy#NO_PROXY} is returned.
     *
     * @param isSsl is it http or https connection
     * @param host  connection host
     * @param port  connection port
     * @return A non null proxy instance
     */
    public Proxy resolveProxy(boolean isSsl, String host, int port) {
        try {
            if (proxySelector != null) {
                final URI uri = new URI(isSsl ? "https" : "http", null, host, port, null, null, null);
                return getProxySelector()
                        .flatMap(selector -> selector.select(uri).stream().findFirst())
                        .orElse(Proxy.NO_PROXY);
            } else if (proxyAddress != null) {
                return new Proxy(getProxyType(), proxyAddress);
            }
            return Proxy.NO_PROXY;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The connection mode to use for <i>plaintext</i> (http as opposed to https) connections.
     * <br>
     * <b>Note: If {@link #httpVersion} is set, this setting is ignored!</b>
     *
     * [available in the Netty HTTP client].
     *
     * @return The plaintext connection mode.
     * @since 4.0.0
     */
    @NonNull
    public HttpVersionSelection.PlaintextMode getPlaintextMode() {
        return plaintextMode;
    }

    /**
     * The connection mode to use for <i>plaintext</i> (http as opposed to https) connections.
     * <br>
     * <b>Note: If {@link #httpVersion} is set, this setting is ignored!</b>
     *
     * @param plaintextMode The plaintext connection mode.
     * @since 4.0.0
     */
    public void setPlaintextMode(@NonNull HttpVersionSelection.PlaintextMode plaintextMode) {
        this.plaintextMode = Objects.requireNonNull(plaintextMode, "plaintextMode");
    }

    /**
     * The protocols to support for TLS ALPN. If HTTP 2 is included, this will also restrict the
     * TLS cipher suites to those supported by the HTTP 2 standard.
     * <br>
     * <b>Note: If {@link #httpVersion} is set, this setting is ignored!</b>
     * [available in the Netty HTTP client].
     *
     * @return The supported ALPN protocols.
     * @since 4.0.0
     */
    @NonNull
    public List<String> getAlpnModes() {
        return alpnModes;
    }

    /**
     * The protocols to support for TLS ALPN. If HTTP 2 is included, this will also restrict the
     * TLS cipher suites to those supported by the HTTP 2 standard.
     * <br>
     * <b>Note: If {@link #httpVersion} is set, this setting is ignored!</b>
     *
     * @param alpnModes The supported ALPN protocols.
     * @since 4.0.0
     */
    public void setAlpnModes(@NonNull List<String> alpnModes) {
        this.alpnModes = Objects.requireNonNull(alpnModes, "alpnModes");
    }

    /**
     * Whether to allow blocking a netty event loop with a call to {@link BlockingHttpClient}. When
     * this is off (the default), any calls that block an event loop will throw an error. Such
     * calls are almost always a mistake that can lead to hard-to-debug transient issues such as
     * read timeouts. Only enable this setting if you are sure you won't hit such a bug.
     *
     * @return {@code true} if blocking an event loop should be allowed
     */
    public boolean isAllowBlockEventLoop() {
        return allowBlockEventLoop;
    }

    /**
     * Whether to allow blocking a netty event loop with a call to {@link BlockingHttpClient}. When
     * this is off (the default), any calls that block an event loop will throw an error. Such
     * calls are almost always a mistake that can lead to hard-to-debug transient issues such as
     * read timeouts. Only enable this setting if you are sure you won't hit such a bug.
     * <br>
     * Default value: {@value DEFAULT_ALLOW_BLOCK_EVENT_LOOP}
     *
     * @param allowBlockEventLoop {@code true} if blocking an event loop should be allowed
     */
    public void setAllowBlockEventLoop(boolean allowBlockEventLoop) {
        this.allowBlockEventLoop = allowBlockEventLoop;
    }

    /**
     * Configure how DNS records are resolved. Ignored if {@link #getAddressResolverGroupName()} is
     * non-null. This option is specific to the netty client.
     *
     * @return The DNS resolution mode
     * @since 4.6.0
     */
    @NonNull
    public DnsResolutionMode getDnsResolutionMode() {
        return dnsResolutionMode;
    }

    /**
     * Configure how DNS records are resolved. Ignored if {@link #getAddressResolverGroupName()} is
     * non-null. This option is specific to the netty client.
     *
     * @param dnsResolutionMode The DNS resolution mode
     * @since 4.6.0
     */
    public void setDnsResolutionMode(@NonNull DnsResolutionMode dnsResolutionMode) {
        this.dnsResolutionMode = dnsResolutionMode;
    }

    /**
     * Name of a fixed netty AddressResolverGroup to use for this client, or {@code null} to
     * instead use {@link #getDnsResolutionMode()}. This option is specific to the netty client.
     *
     * @return The bean name of the resolver group
     * @since 4.6.0
     */
    public @Nullable String getAddressResolverGroupName() {
        return addressResolverGroupName;
    }

    /**
     * Name of a fixed netty AddressResolverGroup to use for this client, or {@code null} to
     * instead use {@link #getDnsResolutionMode()}. This option is specific to the netty client.
     *
     * @param addressResolverGroupName The bean name of the resolver group
     * @since 4.6.0
     */
    public void setAddressResolverGroupName(@Nullable String addressResolverGroupName) {
        this.addressResolverGroupName = addressResolverGroupName;
    }

    /**
     * Obtains the HTTP/2 configuration.
     *
     * @return The HTTP/2 configuration.
     * @since 4.6.0
     */
    @Nullable
    public HttpClientConfiguration.Http2ClientConfiguration getHttp2Configuration() {
        return null;
    }

    /**
     * The path pattern to use for logging outgoing connections to pcap. This is an unsupported option: Behavior may
     * change, or it may disappear entirely, without notice! Only implemented for netty.
     *
     * @return The path pattern, or {@code null} if logging is disabled.
     */
    @Internal
    public String getPcapLoggingPathPattern() {
        return pcapLoggingPathPattern;
    }

    /**
     * The path pattern to use for logging outgoing connections to pcap. This is an unsupported option: Behavior may
     * change, or it may disappear entirely, without notice! Only implemented for netty.
     *
     * @param pcapLoggingPathPattern The path pattern, or {@code null} to disable logging.
     */
    @Internal
    public void setPcapLoggingPathPattern(String pcapLoggingPathPattern) {
        this.pcapLoggingPathPattern = pcapLoggingPathPattern;
    }

    /**
     * Configuration for the HTTP client connnection pool.
     */
    public static class ConnectionPoolConfiguration implements Toggleable {
        /**
         * The prefix to use for configuration.
         */
        public static final String PREFIX = "pool";

        /**
         * The default enable value.
         */
        @SuppressWarnings("WeakerAccess")
        public static final boolean DEFAULT_ENABLED = true;

        private int maxPendingConnections = 4;

        private int maxConcurrentRequestsPerHttp2Connection = Integer.MAX_VALUE;
        private int maxConcurrentHttp1Connections = 1000;
        private int maxConcurrentHttp2Connections = 1;

        private int maxPendingAcquires = Integer.MAX_VALUE;

        private Duration acquireTimeout;

        private boolean enabled = DEFAULT_ENABLED;

        @NonNull
        private HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality connectionLocality = ConnectionLocality.PREFERRED;

        @NonNull
        private PoolVersion version = PoolVersion.V4_9;

        /**
         * Whether connection pooling is enabled.
         * [available in the Netty HTTP client]
         * @return True if connection pooling is enabled
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether connection pooling is enabled. Default value ({@value io.micronaut.http.client.HttpClientConfiguration.ConnectionPoolConfiguration#DEFAULT_ENABLED}).
         *
         * @param enabled True if it is enabled
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Maximum number of futures awaiting connection acquisition. Defaults to no maximum.
         * [available in the Netty HTTP client]
         * @return The max pending requires
         */
        public int getMaxPendingAcquires() {
            return maxPendingAcquires;
        }

        /**
         * Sets the max pending acquires.
         *
         * @param maxPendingAcquires The max pending acquires
         */
        public void setMaxPendingAcquires(int maxPendingAcquires) {
            this.maxPendingAcquires = maxPendingAcquires;
        }

        /**
         * The time to wait to acquire a connection.
         * [available in the Netty HTTP client]
         * @return The timeout as a duration.
         */
        public Optional<Duration> getAcquireTimeout() {
            return Optional.ofNullable(acquireTimeout);
        }

        /**
         * Sets the timeout to wait for a connection.
         *
         * @param acquireTimeout The acquire timeout
         */
        public void setAcquireTimeout(@Nullable Duration acquireTimeout) {
            this.acquireTimeout = acquireTimeout;
        }

        /**
         * The maximum number of <i>pending</i> (new) connections before they are assigned to a
         * pool.
         * [available in the Netty HTTP client]
         * @return The maximum number of pending connections
         * @since 4.0.0
         */
        public int getMaxPendingConnections() {
            return maxPendingConnections;
        }

        /**
         * The maximum number of <i>pending</i> (new) connections before they are assigned to a
         * pool.
         *
         * @param maxPendingConnections The maximum number of pending connections
         * @since 4.0.0
         */
        public void setMaxPendingConnections(int maxPendingConnections) {
            this.maxPendingConnections = maxPendingConnections;
        }

        /**
         * The maximum number of requests (streams) that can run concurrently on one HTTP2
         * connection.
         * [available in the Netty HTTP client]
         * @return The maximum concurrent request count
         * @since 4.0.0
         */
        public int getMaxConcurrentRequestsPerHttp2Connection() {
            return maxConcurrentRequestsPerHttp2Connection;
        }

        /**
         * The maximum number of requests (streams) that can run concurrently on one HTTP2
         * connection.
         *
         * @param maxConcurrentRequestsPerHttp2Connection The maximum concurrent request count
         * @since 4.0.0
         */
        public void setMaxConcurrentRequestsPerHttp2Connection(int maxConcurrentRequestsPerHttp2Connection) {
            this.maxConcurrentRequestsPerHttp2Connection = maxConcurrentRequestsPerHttp2Connection;
        }

        /**
         * The maximum number of concurrent HTTP1 connections in the pool.
         * [available in the Netty HTTP client]
         * @return The maximum concurrent connection count
         * @since 4.0.0
         */
        public int getMaxConcurrentHttp1Connections() {
            return maxConcurrentHttp1Connections;
        }

        /**
         * The maximum number of concurrent HTTP1 connections in the pool.
         *
         * @param maxConcurrentHttp1Connections The maximum concurrent connection count
         * @since 4.0.0
         */
        public void setMaxConcurrentHttp1Connections(int maxConcurrentHttp1Connections) {
            this.maxConcurrentHttp1Connections = maxConcurrentHttp1Connections;
        }

        /**
         * The maximum number of concurrent HTTP2 connections in the pool.
         * [available in the Netty HTTP client]
         * @return The maximum concurrent connection count
         * @since 4.0.0
         */
        public int getMaxConcurrentHttp2Connections() {
            return maxConcurrentHttp2Connections;
        }

        /**
         * The maximum number of concurrent HTTP2 connections in the pool.
         *
         * @param maxConcurrentHttp2Connections The maximum concurrent connection count
         * @since 4.0.0
         */
        public void setMaxConcurrentHttp2Connections(int maxConcurrentHttp2Connections) {
            this.maxConcurrentHttp2Connections = maxConcurrentHttp2Connections;
        }

        /**
         * Optimize locality of client connections depending on which event loop makes a request.
         * [available in the Netty HTTP client]
         *
         * @return The locality configuration
         * @since 4.8.0
         */
        public @NonNull HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality getConnectionLocality() {
            return connectionLocality;
        }

        /**
         * Optimize locality of client connections depending on which event loop makes a request.
         * [available in the Netty HTTP client]
         *
         * @param connectionLocality The locality configuration
         * @since 4.8.0
         */
        public void setConnectionLocality(@NonNull HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality connectionLocality) {
            this.connectionLocality = connectionLocality;
        }

        /**
         * The version of the connection pool implementation. Defaults to {@code V4_9}, can be set
         * to {@code V4_0} for compatibility.
         *
         * @return The pool version
         */
        public @NonNull PoolVersion getVersion() {
            return version;
        }

        /**
         * The version of the connection pool implementation. Defaults to {@code V4_9}, can be set
         * to {@code V4_0} for compatibility.
         *
         * @param version The pool version
         */
        public void setVersion(@NonNull PoolVersion version) {
            this.version = version;
        }

        /**
         * Options for {@link #connectionLocality}.
         *
         * @since 4.8.0
         */
        public enum ConnectionLocality {
            /**
             * Do not consider locality when selecting a connection.
             */
            IGNORE,
            /**
             * If a request is made from an event loop, and a connection is already in the pool
             * from that same event loop, prefer using that connection. When a new connection needs
             * to be created, also prefer the current event loop.
             */
            PREFERRED,
            /**
             * If a request is made from an event loop, and a connection is already in the pool
             * from that same event loop, use that connection. Otherwise, force creating a new
             * connection on the same event loop. Please ensure that settings such as
             * {@link #maxPendingConnections} are also high enough to create new connections.
             */
            ENFORCED_IF_SAME_GROUP,
            /**
             * Same as {@link #ENFORCED_IF_SAME_GROUP}, but if a request is made from outside the
             * event loop group of the client, fail <i>a</i> request. Note that there is no
             * guarantee that the offending request is the only request seeing a failure, so the
             * exception should only be used as a warning that you are using the client in a way
             * that you did not intend.
             */
            ENFORCED_ALWAYS,
        }

        /**
         * Different pool implementation versions, for compatibility.
         *
         * @since 4.9.0
         */
        public enum PoolVersion {
            /**
             * The connection pool introduced in micronaut-core 4.0.0.
             */
            V4_0,
            /**
             * The connection pool introduced in micronaut-core 4.9.0.
             */
            V4_9
        }
    }

    /**
     * Configuration for WebSocket client compression extensions.
     */
    public static class WebSocketCompressionConfiguration implements Toggleable {

        /**
         * The prefix to use for configuration.
         */
        public static final String PREFIX = "ws.compression";

        /**
         * The default enable value.
         */
        @SuppressWarnings("WeakerAccess")
        public static final boolean DEFAULT_ENABLED = true;

        private boolean enabled = DEFAULT_ENABLED;

        /**
         * Whether deflate compression is enabled for client WebSocket connections.
         *
         * @return True if the per message deflate extension is enabled.
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether the per message deflate extension is enabled for WebSocket connections.
         * Default value ({@link io.micronaut.http.client.HttpClientConfiguration.WebSocketCompressionConfiguration#DEFAULT_ENABLED}).
         *
         * @param enabled True is it is enabled.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * HTTP/2-specific client configuration.
     *
     * @since 4.6.0
     */
    public static class Http2ClientConfiguration {
        /**
         * The prefix to use for configuration.
         */
        public static final String PREFIX = "http2";

        private Duration pingIntervalRead = null;

        private Duration pingIntervalWrite = null;

        private Duration pingIntervalIdle = null;

        /**
         * For HTTP/2 connections, the interval from the last inbound message to when an automated ping
         * should be sent. This can be used to keep low-traffic connections alive.
         *
         * @return The timeout when to send a ping frame
         */
        @Nullable
        public Duration getPingIntervalRead() {
            return pingIntervalRead;
        }

        /**
         * For HTTP/2 connections, the interval from the last inbound message to when an automated ping
         * should be sent. This can be used to keep low-traffic connections alive.
         *
         * @param pingIntervalRead The timeout when to send a ping frame
         */
        public void setPingIntervalRead(@Nullable Duration pingIntervalRead) {
            this.pingIntervalRead = pingIntervalRead;
        }

        /**
         * For HTTP/2 connections, the interval from the last outbound message to when an automated ping
         * should be sent. This can be used to keep low-traffic connections alive.
         *
         * @return The timeout when to send a ping frame
         */
        @Nullable
        public Duration getPingIntervalWrite() {
            return pingIntervalWrite;
        }

        /**
         * For HTTP/2 connections, the interval from the last outbound message to when an automated ping
         * should be sent. This can be used to keep low-traffic connections alive.
         *
         * @param pingIntervalWrite The timeout when to send a ping frame
         */
        public void setPingIntervalWrite(@Nullable Duration pingIntervalWrite) {
            this.pingIntervalWrite = pingIntervalWrite;
        }

        /**
         * For HTTP/2 connections, the interval from the last message (inbound or outbound) to when an
         * automated ping should be sent. This can be used to keep low-traffic connections alive.
         *
         * @return The timeout when to send a ping frame
         */
        @Nullable
        public Duration getPingIntervalIdle() {
            return pingIntervalIdle;
        }

        /**
         * For HTTP/2 connections, the interval from the last message (inbound or outbound) to when an
         * automated ping should be sent. This can be used to keep low-traffic connections alive.
         *
         * @param pingIntervalIdle The timeout when to send a ping frame
         */
        public void setPingIntervalIdle(@Nullable Duration pingIntervalIdle) {
            this.pingIntervalIdle = pingIntervalIdle;
        }
    }

    /**
     * The DNS resolution mode.
     *
     * @since 4.6.0
     */
    public enum DnsResolutionMode {
        /**
         * Default netty resolution implementation. Addresses are resolved before connecting.
         */
        DEFAULT,
        /**
         * No-op resolution implementation. Addresses are resolved internally by the JDK during
         * connection.
         */
        NOOP,
        /**
         * Pick a random resolved record for each connection.
         */
        ROUND_ROBIN,
    }
}
