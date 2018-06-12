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

package io.micronaut.http.client;

import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.channel.ChannelOption;

import javax.annotation.Nullable;
import java.net.Proxy;
import java.net.SocketAddress;
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

    private Map<ChannelOption, Object> channelOptions = Collections.emptyMap();

    private Integer numOfThreads = null;

    /**
     * The thread factory to use for creating threads.
     */
    private Class<? extends ThreadFactory> threadFactory;

    private Duration connectTimeout;

    private Duration readTimeout = Duration.ofSeconds(10);

    private Duration readIdleTime = Duration.of(60, ChronoUnit.SECONDS);

    private Duration shutdownTimeout = Duration.ofMillis(100);

    private int maxContentLength = 1024 * 1024 * 10; // 10MB;

    private Proxy.Type proxyType = Proxy.Type.DIRECT;

    private SocketAddress proxyAddress;

    private String proxyUsername;

    private String proxyPassword;

    private Charset defaultCharset = StandardCharsets.UTF_8;

    private boolean followRedirects = true;

    private SslConfiguration sslConfiguration = new ClientSslConfiguration();

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
     * Sets whether redirects should be followed (defaults to true).
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
     * Sets the default charset to use.
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
     * For streaming requests, the {@link #getReadTimeout()} method does not apply instead a configurable
     * idle timeout is applied.
     *
     * @return The default amount of time to allow read operation connections  to remain idle
     */
    public Optional<Duration> getReadIdleTime() {
        return Optional.ofNullable(readIdleTime);
    }


    /**
     * @return The default connect timeout. Defaults to Netty default.
     */
    public Optional<Duration> getConnectTimeout() {
        return Optional.ofNullable(connectTimeout);
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
     * Sets the amount of time to wait for shutdown of client thread pools.
     *
     * @param shutdownTimeout The shutdown time
     */
    public void setShutdownTimeout(@Nullable Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    /**
     * Sets the read timeout.
     *
     * @param readTimeout The read timeout
     */
    public void setReadTimeout(@Nullable Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * Sets the max read idle time for streaming requests.
     *
     * @param readIdleTime The read idle time
     */
    public void setReadIdleTime(@Nullable Duration readIdleTime) {
        this.readIdleTime = readIdleTime;
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
     * Sets the maximum content length the client can consume.
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
}
