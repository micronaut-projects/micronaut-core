/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client;

import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.channel.ChannelOption;

import javax.annotation.Nullable;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
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
     * The default follow redirects value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_FOLLOW_REDIRECTS = true;

    /**
     * The default value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_EXCEPTION_ON_ERROR_STATUS = true;

    private Map<ChannelOption, Object> channelOptions = Collections.emptyMap();

    private Integer numOfThreads = null;

    /**
     * The thread factory to use for creating threads.
     */
    private Class<? extends ThreadFactory> threadFactory;

    private Duration connectTimeout;

    private Duration connectTtl;

    private Duration readTimeout = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS);

    private Duration readIdleTimeout = Duration.of(DEFAULT_READ_IDLE_TIMEOUT_MINUTES, ChronoUnit.MINUTES);

    private Duration shutdownTimeout = Duration.ofMillis(DEFAULT_SHUTDOWN_TIMEOUT_MILLISECONDS);

    private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;

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
     * @return Whether redirects should be followed
     */
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    /**
     *
     * @return Whether throwing an exception upon HTTP error status (>= 400) is preferred.
     */
    public boolean isExceptionOnErrorStatus() {
        return exceptionOnErrorStatus;
    }

    /**
     * Sets whether throwing an exception upon HTTP error status (>= 400) is preferred. Default value ({@link io.micronaut.http.client.HttpClientConfiguration#DEFAULT_EXCEPTION_ON_ERROR_STATUS})
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
     * @return The Netty channel options.
     * @see io.netty.bootstrap.Bootstrap#options()
     */
    public Map<ChannelOption, Object> getChannelOptions() {
        return channelOptions;
    }

    /**
     * @param channelOptions The Netty channel options
     * @see io.netty.bootstrap.Bootstrap#options()
     */
    public void setChannelOptions(Map<ChannelOption, Object> channelOptions) {
        this.channelOptions = channelOptions;
    }

    /**
     * @return The default read timeout. Defaults to 10 seconds.
     */
    public Optional<Duration> getReadTimeout() {
        return Optional.ofNullable(readTimeout);
    }


    /**
     * For streaming requests and WebSockets, the {@link #getReadTimeout()} method does not apply instead a configurable
     * idle timeout is applied.
     *
     * @return The default amount of time to allow read operation connections  to remain idle
     */
    public Optional<Duration> getReadIdleTimeout() {
        return Optional.ofNullable(readIdleTimeout);
    }

    /**
     * @return The default connect timeout. Defaults to Netty default.
     */
    public Optional<Duration> getConnectTimeout() {
        return Optional.ofNullable(connectTimeout);
    }

    /**
     * @return The connectTtl.
     */
    public Optional<Duration> getConnectTtl() {
        return Optional.ofNullable(connectTtl);
    }

    /**
     * The amount of time to wait for shutdown.
     *
     * @return The shutdown timeout
     */
    public Optional<Duration> getShutdownTimeout() {
        return Optional.ofNullable(shutdownTimeout);
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
     * Sets the max read idle time for streaming requests. Default value ({@value io.micronaut.http.client.HttpClientConfiguration#DEFAULT_READ_IDLE_TIMEOUT_MINUTES} seconds).
     *
     * @param readIdleTimeout The read idle time
     */
    public void setReadIdleTimeout(@Nullable Duration readIdleTimeout) {
        this.readIdleTimeout = readIdleTimeout;
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
     * @return The maximum content length the client can consume
     */
    public int getMaxContentLength() {
        return maxContentLength;
    }

    /**
     * Sets the maximum content length the client can consume. Default value ({@value io.micronaut.http.client.HttpClientConfiguration#DEFAULT_MAX_CONTENT_LENGTH} => 10MB).
     *
     * @param maxContentLength The maximum content length the client can consume
     */
    public void setMaxContentLength(@ReadableBytes int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    /**
     * The proxy to use. For authentication specify http.proxyUser and http.proxyPassword system properties.
     * <p>
     * Alternatively configure a {@code java.net.ProxySelector}
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
     * @return The proxy user name to use
     */
    public Optional<String> getProxyUsername() {
        String type = proxyType.name().toLowerCase();
        return proxyUsername != null ? Optional.of(proxyUsername) : Optional.ofNullable(System.getProperty(type + ".proxyUser"));
    }

    /**
     * Sets the proxy user name to use.
     *
     * @param proxyUsername The proxy user name to use
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
        return proxyPassword != null ? Optional.of(proxyPassword) : Optional.ofNullable(System.getProperty(type + ".proxyPassword"));
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
     *
     * If ProxySelector is set by {@link #setProxySelector(ProxySelector)} then it constructs URI and pass it to {@link ProxySelector#select(URI)}.
     * First proxy returned by proxy selector will be used. If no proxy is returned by select, then {@link Proxy#NO_PROXY} will be used.
     *
     * If ProxySelector is not set then parameters are ignored and a proxy as defined by {@link #setProxyAddress(SocketAddress)} and {@link #setProxyType(Proxy.Type)} will be returned.
     * If no proxy is defined then parameters are ignored and {@link Proxy#NO_PROXY} is returned.
     *
     * @param isSsl is it http or https connection
     * @param host connection host
     * @param port connection port
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
            return  Proxy.NO_PROXY;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
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
        public static final boolean DEFAULT_ENABLED = false;

        /**
         * The default max connections value.
         */
        @SuppressWarnings("WeakerAccess")
        public static final int DEFAULT_MAXCONNECTIONS = -1;

        private int maxConnections = DEFAULT_MAXCONNECTIONS;

        private int maxPendingAcquires = Integer.MAX_VALUE;

        private Duration acquireTimeout;

        private boolean enabled = DEFAULT_ENABLED;

        /**
         * Whether connection pooling is enabled.
         *
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
         * The maximum number of connections. Defaults to ({@value io.micronaut.http.client.HttpClientConfiguration.ConnectionPoolConfiguration#DEFAULT_MAXCONNECTIONS}); no maximum.
         *
         * @return The max connections
         */
        public int getMaxConnections() {
            return maxConnections;
        }


        /**
         * Sets the maximum number of connections. Defaults to no maximum.
         * @param maxConnections The count
         */
        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        /**
         * Maximum number of futures awaiting connection acquisition. Defaults to no maximum.
         *
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
         *
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
    }
}
