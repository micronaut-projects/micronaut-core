package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Collection;

final class ConnectionManager {
    private final DefaultHttpClient httpClient; // TODO
    private final Logger log;
    EventLoopGroup group;
    final Bootstrap bootstrap;
    final ChannelPoolMap<DefaultHttpClient.RequestKey, ChannelPool> poolMap;
    private final HttpClientConfiguration configuration;
    final InvocationInstrumenter instrumenter;
    @Nullable
    final Long readTimeoutMillis;
    @Nullable
    final Long connectionTimeAliveMillis;
    final HttpVersion httpVersion;
    final SslContext sslContext;
    final NettyClientCustomizer clientCustomizer;
    final Collection<ChannelPipelineListener> pipelineListeners;
    final String informationalServiceId;

    ConnectionManager(
        DefaultHttpClient httpClient,
        Logger log, EventLoopGroup group,
        HttpClientConfiguration configuration,
        HttpVersion httpVersion,
        InvocationInstrumenter instrumenter,
        ChannelFactory<? extends Channel> socketChannelFactory,
        @Nullable Long readTimeoutMillis,
        @Nullable Long connectionTimeAliveMillis,
        SslContext sslContext,
        NettyClientCustomizer clientCustomizer,
        Collection<ChannelPipelineListener> pipelineListeners, String informationalServiceId) {
        this.httpClient = httpClient;
        this.log = log;
        this.httpVersion = httpVersion;
        this.group = group;
        this.sslContext = sslContext;
        this.configuration = configuration;
        this.instrumenter = instrumenter;
        this.readTimeoutMillis = readTimeoutMillis;
        this.connectionTimeAliveMillis = connectionTimeAliveMillis;
        this.clientCustomizer = clientCustomizer;
        this.pipelineListeners = pipelineListeners;
        this.informationalServiceId = informationalServiceId;
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(group)
            .channelFactory(socketChannelFactory)
            .option(ChannelOption.SO_KEEPALIVE, true);

        final ChannelHealthChecker channelHealthChecker = channel -> channel.eventLoop().newSucceededFuture(channel.isActive() && !ConnectTTLHandler.isChannelExpired(channel));

        HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration = configuration.getConnectionPoolConfiguration();
        // HTTP/2 defaults to keep alive connections so should we should always use a pool
        if (connectionPoolConfiguration.isEnabled() || httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
            int maxConnections = connectionPoolConfiguration.getMaxConnections();
            if (maxConnections > -1) {
                poolMap = new AbstractChannelPoolMap<DefaultHttpClient.RequestKey, ChannelPool>() {
                    @Override
                    protected ChannelPool newPool(DefaultHttpClient.RequestKey key) {
                        Bootstrap newBootstrap = bootstrap.clone(group);
                        httpClient.initBootstrapForProxy(newBootstrap, key.isSecure(), key.getHost(), key.getPort());
                        newBootstrap.remoteAddress(key.getRemoteAddress());

                        AbstractChannelPoolHandler channelPoolHandler = httpClient.newPoolHandler(key);
                        final long acquireTimeoutMillis = connectionPoolConfiguration.getAcquireTimeout().map(Duration::toMillis).orElse(-1L);
                        return new FixedChannelPool(
                            newBootstrap,
                            channelPoolHandler,
                            channelHealthChecker,
                            acquireTimeoutMillis > -1 ? FixedChannelPool.AcquireTimeoutAction.FAIL : null,
                            acquireTimeoutMillis,
                            maxConnections,
                            connectionPoolConfiguration.getMaxPendingAcquires()

                        );
                    }
                };
            } else {
                poolMap = new AbstractChannelPoolMap<DefaultHttpClient.RequestKey, ChannelPool>() {
                    @Override
                    protected ChannelPool newPool(DefaultHttpClient.RequestKey key) {
                        Bootstrap newBootstrap = bootstrap.clone(group);
                        httpClient.initBootstrapForProxy(newBootstrap, key.isSecure(), key.getHost(), key.getPort());
                        newBootstrap.remoteAddress(key.getRemoteAddress());

                        AbstractChannelPoolHandler channelPoolHandler = httpClient.newPoolHandler(key);
                        return new SimpleChannelPool(
                            newBootstrap,
                            channelPoolHandler,
                            channelHealthChecker
                        );
                    }
                };
            }
        } else {
            this.poolMap = null;
        }
    }
}
