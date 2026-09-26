/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.context.event.HttpRequestReceivedEvent;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import io.micronaut.http.server.netty.handler.RequestHandler;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.http.scope.RequestScope;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Internal implementation of the {@link io.netty.channel.ChannelInboundHandler} for Micronaut.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@Sharable
@SuppressWarnings("FileLength")
public final class RoutingInBoundHandler implements RequestHandler {

    /**
     * Channel attribute that exposes the current request to access log elements (Micronaut
     * Session's log element reads it by this name). Set when the pipeline has an access logger,
     * and cleared again once the response has been written.
     */
    static final AttributeKey<NettyHttpRequest<?>> ACCESS_LOG_REQUEST_ATTRIBUTE = AttributeKey.valueOf("NettyHttpRequest");
    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundHandler.class);
    /*
     * Also present in {@link RouteExecutor}.
     */
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
        "^.*(?:connection (?:reset|closed|abort|broken)|broken pipe).*$", Pattern.CASE_INSENSITIVE);
    /**
     * Request event listeners that take longer than this on the event loop are reported at debug level.
     */
    private static final long SLOW_LISTENER_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    final StaticResourceResolver staticResourceResolver;
    final NettyHttpServerConfiguration serverConfiguration;
    final RequestArgumentSatisfier requestArgumentSatisfier;
    final Supplier<ExecutorService> ioExecutorSupplier;
    final Supplier<Executor> requestEventExecutorSupplier;
    final boolean multipartEnabled;
    final MessageBodyHandlerRegistry messageBodyHandlerRegistry;
    final ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher;
    final ApplicationEventPublisher<HttpRequestReceivedEvent> receivedPublisher;
    final RouteExecutor routeExecutor;
    final ConversionService conversionService;
    /**
     * This is set to {@code true} if <i>any</i> {@link HttpPipelineBuilder} has a logging handler.
     * When this is not set, we can do a shortcut for performance.
     */
    boolean supportLoggingHandler = false;
    private final ApplicationContext applicationContext;
    /**
     * Decides whether a {@link HttpRequestTerminatedEvent} has to be published for a request.
     * Resolved on first use, see {@link #resolveTerminatedEventFilter()}.
     */
    private @Nullable Predicate<NettyHttpRequest<?>> terminatedEventFilter;

    /**
     * @param serverConfiguration               The Netty HTTP server configuration
     * @param embeddedServerContext             The embedded server context
     * @param ioExecutor                        The IO executor supplier, must be memoized
     * @param requestEventExecutor              The request event executor supplier, must be memoized
     * @param terminateEventPublisher           The terminate event publisher
     * @param receivedPublisher                 The received publisher
     * @param conversionService                 The conversion service
     */
    RoutingInBoundHandler(
        NettyHttpServerConfiguration serverConfiguration,
        NettyEmbeddedServices embeddedServerContext,
        Supplier<ExecutorService> ioExecutor,
        Supplier<Executor> requestEventExecutor,
        ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher,
        ApplicationEventPublisher<HttpRequestReceivedEvent> receivedPublisher, ConversionService conversionService) {
        this.staticResourceResolver = embeddedServerContext.getStaticResourceResolver();
        this.messageBodyHandlerRegistry = embeddedServerContext.getMessageBodyHandlerRegistry();
        // Memoization (thread-safe, lazy, evaluated at most once) is done by the caller via SupplierUtil.memoized
        this.ioExecutorSupplier = ioExecutor;
        this.requestEventExecutorSupplier = requestEventExecutor;
        this.requestArgumentSatisfier = embeddedServerContext.getRequestArgumentSatisfier();
        this.serverConfiguration = serverConfiguration;
        this.terminateEventPublisher = terminateEventPublisher;
        this.receivedPublisher = receivedPublisher;
        Optional<Boolean> isMultiPartEnabled = serverConfiguration.getMultipart().getEnabled();
        this.multipartEnabled = isMultiPartEnabled.isEmpty() || isMultiPartEnabled.get();
        this.routeExecutor = embeddedServerContext.getRouteExecutor();
        this.conversionService = conversionService;
        this.applicationContext = embeddedServerContext.getApplicationContext();
    }

    private boolean shouldPublishTerminatedEvent(NettyHttpRequest<?> request) {
        Predicate<NettyHttpRequest<?>> filter = terminatedEventFilter;
        if (filter == null) {
            filter = resolveTerminatedEventFilter();
            terminatedEventFilter = filter;
        }
        return filter.test(request);
    }

    /**
     * The request scope listens for {@link HttpRequestTerminatedEvent} to destroy the request
     * scoped beans, so the publisher is practically never empty. When the request scope is the
     * only listener, the event is only published for requests that hold request scoped beans.
     *
     * @return The filter deciding whether the event is published for a request
     */
    @SuppressWarnings("unchecked")
    private Predicate<NettyHttpRequest<?>> resolveTerminatedEventFilter() {
        if (terminateEventPublisher.isEmpty()) {
            return request -> false;
        }
        if (terminateEventPublisher == applicationContext.getEventPublisher(HttpRequestTerminatedEvent.class)) {
            Collection<ApplicationEventListener> listeners = applicationContext.getBeansOfType(
                ApplicationEventListener.class, Qualifiers.byTypeArguments(HttpRequestTerminatedEvent.class));
            if (listeners.size() == 1
                && listeners.iterator().next() instanceof CustomScope<?> scope
                && scope.annotationType() == RequestScope.class) {
                ApplicationEventListener<HttpRequestTerminatedEvent> requestScope = (ApplicationEventListener<HttpRequestTerminatedEvent>) scope;
                return request -> requestScope.supports(new HttpRequestTerminatedEvent(request));
            }
        }
        return request -> true;
    }

    private void cleanupRequest(NettyHttpRequest<?> request) {
        try {
            request.release();
        } finally {
            ExecutionFlow<Void> terminatedFlow = ExecutionFlow.empty();
            try {
                if (shouldPublishTerminatedEvent(request)) {
                    terminatedFlow = ExecutionFlow.async(getRequestEventExecutor(), () -> {
                        PropagatedContext.getOrEmpty()
                            .plus(new ServerHttpRequestContext(request))
                            .propagate(() -> publishRequestEvent(request, terminateEventPublisher, new HttpRequestTerminatedEvent(request)));
                        return ExecutionFlow.empty();
                    });
                }
            } catch (RuntimeException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skipping request terminated event publication during shutdown: {}", e.getMessage(), e);
                }
            }
            terminatedFlow.onComplete((ignore, throwable) -> {
                if (throwable != null && LOG.isErrorEnabled()) {
                    LOG.error("Error publishing request terminated event: {}", throwable.getMessage(), throwable);
                }
            });
        }
    }

    @Override
    public void responseWritten(@Nullable Object attachment) {
        if (attachment != null) {
            NettyHttpRequest<?> request = (NettyHttpRequest<?>) attachment;
            if (supportLoggingHandler) {
                // only clear our own request: with pipelining the attribute may already hold
                // the next request on this connection
                request.getChannelHandlerContext().channel().attr(ACCESS_LOG_REQUEST_ATTRIBUTE).compareAndSet(request, null);
            }
            cleanupRequest(request);
        }
    }

    @Override
    public void handleUnboundError(Throwable cause) {
        // short-circuit ignorable exceptions: This is also handled by RouteExecutor, but handling this early avoids
        // running any filters
        if (isIgnorable(cause)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Swallowed an IOException caused by client connectivity: {}", cause.getMessage(), cause);
            }
            return;
        }

        if (cause instanceof SSLException || cause.getCause() instanceof SSLException || cause instanceof DecompressionException) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Micronaut Server Error - No request state present. Cause: {}", cause.getMessage(), cause);
            }
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Micronaut Server Error - No request state present. Cause: {}", cause.getMessage(), cause);
            }
        }
    }

    @Override
    public void accept(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
        NettyHttpRequest<Object> mnRequest;
        try {
            mnRequest = new NettyHttpRequest<>(request, body, ctx, conversionService, serverConfiguration);
        } catch (IllegalArgumentException e) {
            body.close();

            // invalid URI
            NettyHttpRequest<Object> errorRequest = new NettyHttpRequest<>(
                new DefaultHttpRequest(request.protocolVersion(), request.method(), "/"),
                    NettyByteBodyFactory.empty(),
                ctx,
                conversionService,
                serverConfiguration
            );
            prepareRequest(ctx, outboundAccess, errorRequest);
            Throwable error = e.getCause() == null ? e : e.getCause();
            executionFlowForReceivedEvent(errorRequest).onComplete((ignore, throwable) -> {
                if (throwable != null) {
                    error.addSuppressed(throwable);
                }
                handleException(ctx, outboundAccess, errorRequest, error);
            });
            return;
        }
        prepareRequest(ctx, outboundAccess, mnRequest);
        ExecutionFlow<Void> receivedFlow = executionFlowForReceivedEvent(mnRequest);
        receivedFlow.onComplete((ignore, throwable) -> {
            if (throwable != null) {
                handleException(ctx, outboundAccess, mnRequest, throwable);
            } else {
                executeOnEventLoopIfNeeded(ctx, () ->
                    PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(mnRequest))
                        .propagate(() -> new NettyRequestLifecycle(this, outboundAccess).handleNormal(mnRequest)));
            }
        });
    }

    private void prepareRequest(ChannelHandlerContext ctx, OutboundAccess outboundAccess, NettyHttpRequest<Object> mnRequest) {
        if (supportLoggingHandler && hasAccessLogHandler(ctx)) {
            // Micronaut Session needs this to extract values from the Micronaut Http Request for logging
            ctx.channel().attr(ACCESS_LOG_REQUEST_ATTRIBUTE).set(mnRequest);
        }
        outboundAccess.attachment(mnRequest);
    }

    /**
     * Whether the pipeline of this context carries the
     * {@value ChannelPipelineCustomizer#HANDLER_ACCESS_LOGGER} handler. The
     * {@link HttpPipelineBuilder.StreamPipeline} remembers this per pipeline, so it does not have
     * to be looked up in the pipeline for every request.
     */
    private static boolean hasAccessLogHandler(ChannelHandlerContext ctx) {
        HttpPipelineBuilder.StreamPipeline streamPipeline = ctx.channel().attr(HttpPipelineBuilder.STREAM_PIPELINE_ATTRIBUTE.get()).get();
        if (streamPipeline == null) {
            // not built by the HttpPipelineBuilder
            return ctx.pipeline().get(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER) != null;
        }
        return streamPipeline.hasAccessLogHandler();
    }

    private void handleException(ChannelHandlerContext ctx, OutboundAccess outboundAccess, NettyHttpRequest<Object> request, Throwable throwable) {
        executeOnEventLoopIfNeeded(ctx, () -> PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(request)).propagate(() -> {
            new NettyRequestLifecycle(this, outboundAccess).handleException(request, throwable);
            return null;
        }));
    }

    private void executeOnEventLoopIfNeeded(ChannelHandlerContext ctx, Runnable runnable) {
        if (ctx.executor().inEventLoop()) {
            runnable.run();
        } else {
            ctx.executor().execute(PropagatedContext.wrapCurrent(runnable));
        }
    }

    private ExecutionFlow<Void> executionFlowForReceivedEvent(NettyHttpRequest<?> request) {
        if (receivedPublisher.isEmpty()) {
            return ExecutionFlow.empty();
        }
        return ExecutionFlow.async(getRequestEventExecutor(), () -> {
            PropagatedContext.getOrEmpty()
                .plus(new ServerHttpRequestContext(request))
                .propagate(() -> publishRequestEvent(request, receivedPublisher, new HttpRequestReceivedEvent(request)));
            return ExecutionFlow.empty();
        });
    }

    /**
     * Publish a request event. With the default thread selection the listeners run inline on the
     * event loop, where a slow listener holds up every connection of that loop, so at debug level
     * the time they take is checked against {@link #SLOW_LISTENER_THRESHOLD_NANOS}.
     */
    private <E> void publishRequestEvent(NettyHttpRequest<?> request, ApplicationEventPublisher<E> publisher, E event) {
        if (!LOG.isDebugEnabled()) {
            publisher.publishEvent(event);
            return;
        }
        long start = System.nanoTime();
        try {
            publisher.publishEvent(event);
        } finally {
            long taken = System.nanoTime() - start;
            if (taken > SLOW_LISTENER_THRESHOLD_NANOS && request.getChannelHandlerContext().executor().inEventLoop()) {
                LOG.debug("Listeners for {} took {} ms on the event loop for {}. Request event listeners must not block; use @Async on the listener or micronaut.server.thread-selection=BLOCKING", event.getClass().getSimpleName(), TimeUnit.NANOSECONDS.toMillis(taken), request);
            }
        }
    }

    public void writeResponse(OutboundAccess outboundAccess,
                              NettyHttpRequest<?> nettyHttpRequest,
                              @Nullable
                              HttpResponse<?> response,
                              @Nullable
                              Throwable throwable) {
        writeResponse(outboundAccess, nettyHttpRequest, response, throwable, null);
    }

    /**
     * Write the response.
     *
     * @param outboundAccess    The outbound access
     * @param nettyHttpRequest  The request
     * @param response          The response, if there was no error
     * @param throwable         The error, if there is no response
     * @param writeErrorHandler Gives the error response when writing the body of the response
     *                          fails before anything was sent, or {@code null} to answer the
     *                          default error response
     */
    void writeResponse(OutboundAccess outboundAccess,
                       NettyHttpRequest<?> nettyHttpRequest,
                       @Nullable
                       HttpResponse<?> response,
                       @Nullable
                       Throwable throwable,
                       @Nullable
                       Function<Throwable, ExecutionFlow<HttpResponse<?>>> writeErrorHandler) {
        if (throwable != null) {
            response = routeExecutor.createDefaultErrorResponse(nettyHttpRequest, throwable);
            writeErrorHandler = null;
        }
        if (response != null) {
            NettyResponseLifecycle responseLifecycle = new NettyResponseLifecycle(this, nettyHttpRequest);
            ExecutionFlow<? extends ByteBodyHttpResponse<?>> finalResponse = writeErrorHandler == null
                ? responseLifecycle.encodeHttpResponseSafe(nettyHttpRequest, response)
                : responseLifecycle.encodeHttpResponseSafe(nettyHttpRequest, response, writeErrorHandler);
            finalResponse.onComplete((r, t) -> {
                ByteBodyHttpResponse<?> encodedResponse;
                if (t != null) {
                    // fallback of the fallback...
                    encodedResponse = ByteBodyHttpResponseWrapper.wrap(HttpResponse.serverError(), NettyByteBodyFactory.empty());
                    try {
                        outboundAccess.closeAfterWrite();
                    } catch (Throwable g) {
                        t.addSuppressed(g);
                    }
                    LOG.warn("Failed to encode error response", t);
                } else {
                    encodedResponse = r;
                }
                try (encodedResponse) {
                    closeConnectionIfError(encodedResponse, nettyHttpRequest, outboundAccess);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Response {} - {} {}",
                            encodedResponse.code(),
                            nettyHttpRequest.getMethodName(),
                            nettyHttpRequest.getUri());
                    }
                    io.netty.handler.codec.http.HttpResponse noBodyResponse = NettyMutableHttpResponse.toNoBodyResponse(encodedResponse);
                    if (nettyHttpRequest.getMethod() == HttpMethod.HEAD) {
                        outboundAccess.writeHeadResponse(new DefaultHttpResponse(
                            noBodyResponse.protocolVersion(),
                            noBodyResponse.status(),
                            noBodyResponse.headers()
                        ));
                    } else {
                        outboundAccess.write(noBodyResponse, encodedResponse.byteBody());
                    }
                } catch (Throwable u) {
                    if (t != null) {
                        u.addSuppressed(t);
                    }
                    t = u;
                }
                if (t != null) {
                    LOG.warn("Failed to build error response", t);
                }
            });
        } else {
            // this happens when the connection is already closed, but let's write a fake response
            // anyway to ensure the request is closed
            outboundAccess.closeAfterWrite();
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE), NettyByteBodyFactory.empty());
        }
    }

    ExecutorService getIoExecutor() {
        return ioExecutorSupplier.get();
    }

    Executor getRequestEventExecutor() {
        return requestEventExecutorSupplier.get();
    }

    private void closeConnectionIfError(HttpResponse<?> message, HttpRequest<?> request, OutboundAccess outboundAccess) {
        boolean decodeError = request instanceof NettyHttpRequest<?> nettyRequest &&
            nettyRequest.getNativeRequest().decoderResult().isFailure();

        if (decodeError || (message.code() >= 500 && !serverConfiguration.isKeepAliveOnServerError())) {
            outboundAccess.closeAfterWrite();
        }
    }

    /**
     * Is the exception ignorable by Micronaut.
     *
     * @param cause The cause
     * @return True if it can be ignored.
     */
    boolean isIgnorable(Throwable cause) {
        if (cause instanceof ClosedChannelException || cause.getCause() instanceof ClosedChannelException) {
            return true;
        }
        if (cause instanceof PrematureChannelClosureException && "Channel closed while still aggregating message".equals(cause.getMessage())) {
            return true;
        }
        String message = cause.getMessage();
        return cause instanceof IOException && message != null && IGNORABLE_ERROR_MESSAGE.matcher(message).matches();
    }
}
