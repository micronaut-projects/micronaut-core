package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Collection;
import java.util.function.Consumer;

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

    /**
     * Creates an initial connection to the given remote host.
     *
     * @param requestKey
     * @param sslCtx          The SslContext instance
     * @param isStream        Is the connection a stream connection
     * @param isProxy         Is this a streaming proxy
     * @param acceptsEvents
     * @param contextConsumer The logic to run once the channel is configured correctly
     * @return A ChannelFuture
     * @throws HttpClientException If the URI is invalid
     */
    ChannelFuture doConnect(
        DefaultHttpClient.RequestKey requestKey,
        boolean isStream,
        boolean isProxy,
        boolean acceptsEvents,
        Consumer<ChannelHandlerContext> contextConsumer) throws HttpClientException {

        SslContext sslCtx = buildSslContext(requestKey);
        String host = requestKey.getHost();
        int port = requestKey.getPort();
        Bootstrap localBootstrap = bootstrap.clone();
        httpClient.initBootstrapForProxy(localBootstrap, sslCtx != null, host, port);
        localBootstrap.handler(httpClient.new HttpClientInitializer(
            sslCtx,
            host,
            port,
            isStream,
            isProxy,
            acceptsEvents,
            contextConsumer)
        );
        return localBootstrap.connect(host, port);
    }

    static boolean isAcceptEvents(HttpRequest<?> request) {
        String acceptHeader = request.getHeaders().get(io.micronaut.http.HttpHeaders.ACCEPT);
        return acceptHeader != null && acceptHeader.equalsIgnoreCase(MediaType.TEXT_EVENT_STREAM);
    }

    /**
     * Builds an {@link SslContext} for the given URI if necessary.
     *
     * @return The {@link SslContext} instance
     */
    private SslContext buildSslContext(DefaultHttpClient.RequestKey requestKey) {
        final SslContext sslCtx;
        if (requestKey.isSecure()) {
            sslCtx = sslContext;
            //Allow https requests to be sent if SSL is disabled but a proxy is present
            if (sslCtx == null && !configuration.getProxyAddress().isPresent()) {
                throw httpClient.customizeException(new HttpClientException("Cannot send HTTPS request. SSL is disabled"));
            }
        } else {
            sslCtx = null;
        }
        return sslCtx;
    }

    Future<PoolHandle> acquireChannelFromPool(DefaultHttpClient.RequestKey requestKey) {
        ChannelPool channelPool = poolMap.get(requestKey);
        Future<Channel> channelFuture = channelPool.acquire();
        Promise<PoolHandle> promise = group.next().newPromise();
        channelFuture.addListener(f -> {
            if (channelFuture.isSuccess()) {
                promise.setSuccess(new PoolHandle(channelPool, channelFuture.getNow()));
            } else {
                promise.setFailure(channelFuture.cause());
            }
        });
        return promise;
    }

    PoolHandle mockPoolHandle(Channel channel) {
        // TODO: delete
        return new PoolHandle(null, channel);
    }

    class PoolHandle {
        final Channel channel;
        private final ChannelPool channelPool;
        private boolean canReturn;

        private PoolHandle(ChannelPool channelPool, Channel channel) {
            this.channel = channel;
            this.channelPool = channelPool;
            this.canReturn = channelPool != null;
        }

        void taint() {
            canReturn = false;
        }

        void release() {
            if (channelPool != null) {
                httpClient.removeReadTimeoutHandler(channel.pipeline());
                if (!canReturn) {
                    channel.closeFuture().addListener((future ->
                        channelPool.release(channel)
                    ));
                } else {
                    channelPool.release(channel);
                }
            } else {
                // just close it to prevent any future reads without a handler registered
                channel.close();
            }
        }
    }
}
