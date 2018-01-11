/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.particleframework.context.annotation.ConfigurationProperties;

import javax.net.ssl.TrustManagerFactory;
import java.net.Proxy;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadFactory;

/**
 * Configuration for the {@link HttpClient}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("particle.http.client")
public class HttpClientConfiguration {

    private Map<ChannelOption, Object> channelOptions = Collections.emptyMap();
    /**
     * The encoding to use
     */
    private Charset encoding = StandardCharsets.UTF_8;
    /**
     * The number of threads the client should use for requests
     */
    private Integer numOfThreads = null;

    /**
     * The thread factory to use for creating threads
     */
    private Class<? extends ThreadFactory> threadFactory;
    /**
     * The SSL provider to use
     */
    private SslProvider sslProvider = SslContext.defaultClientProvider();

    /**
     * The default session cache size
     */
    private Long sslSessionCacheSize;

    /**
     * The SSL timeout period
     */
    private Duration sslSessionTimeout;

    /**
     * The default read timeout
     */
    private Duration readTimeout;

    /**
     * The default trust manager factory
     */
    private TrustManagerFactory sslTrustManagerFactory = InsecureTrustManagerFactory.INSTANCE;

    /**
     * The maximum content length the client can consume
     */
    private int maxContentLength = Integer.MAX_VALUE;

    /**
     * The proxy to use. For authentication specify http.proxyUser and http.proxyPassword system properties
     *
     * Alternatively configure a java.net.ProxySelector
     */
    private Proxy.Type proxyType = Proxy.Type.DIRECT;

    /**
     * The proxy to use. For authentication specify http.proxyUser and http.proxyPassword system properties
     *
     * Alternatively configure a java.net.ProxySelector
     */
    private SocketAddress proxyAddress;

    /**
     * @return The Netty channel options.
     * @see Bootstrap#options()
     */
    public Map<ChannelOption, Object> getChannelOptions() {
        return channelOptions;
    }

    public Optional<Duration> getReadTimeout() {
        return Optional.ofNullable(readTimeout);
    }

    public Charset getEncoding() {
        return encoding;
    }

    public OptionalInt getNumOfThreads() {
        return numOfThreads != null ? OptionalInt.of(numOfThreads) : OptionalInt.empty();
    }

    public Optional<Class<? extends ThreadFactory>> getThreadFactory() {
        return Optional.ofNullable(threadFactory);
    }

    public SslProvider getSslProvider() {
        return sslProvider;
    }

    public OptionalLong getSslSessionCacheSize() {
        return sslSessionCacheSize != null ? OptionalLong.of(sslSessionCacheSize) : OptionalLong.empty();
    }

    public Optional<Duration> getSslSessionTimeout() {
        return Optional.ofNullable(sslSessionTimeout);
    }

    public Optional<TrustManagerFactory> getSslTrustManagerFactory() {
        return Optional.ofNullable(sslTrustManagerFactory);
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public Proxy.Type getProxyType() {
        return proxyType;
    }

    public Optional<SocketAddress> getProxyAddress() {
        return Optional.ofNullable(proxyAddress);
    }

    public void setChannelOptions(Map<ChannelOption, Object> channelOptions) {
        this.channelOptions = channelOptions;
    }

    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }

    public void setNumOfThreads(Integer numOfThreads) {
        this.numOfThreads = numOfThreads;
    }

    public void setThreadFactory(Class<? extends ThreadFactory> threadFactory) {
        this.threadFactory = threadFactory;
    }

    public void setSslProvider(SslProvider sslProvider) {
        this.sslProvider = sslProvider;
    }

    public void setSslSessionCacheSize(Long sslSessionCacheSize) {
        this.sslSessionCacheSize = sslSessionCacheSize;
    }

    public void setSslSessionTimeout(Duration sslSessionTimeout) {
        this.sslSessionTimeout = sslSessionTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public void setSslTrustManagerFactory(TrustManagerFactory sslTrustManagerFactory) {
        this.sslTrustManagerFactory = sslTrustManagerFactory;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public void setProxyType(Proxy.Type proxyType) {
        this.proxyType = proxyType;
    }

    public void setProxyAddress(SocketAddress proxyAddress) {
        this.proxyAddress = proxyAddress;
    }
}
