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

import io.micronaut.context.event.ApplicationEventPublisher;
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
import io.micronaut.http.context.event.HttpRequestReceivedEvent;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.body.AvailableNettyByteBody;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import io.micronaut.http.server.netty.handler.RequestHandler;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
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

    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundHandler.class);
    /*
     * Also present in {@link RouteExecutor}.
     */
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
        "^.*(?:connection (?:reset|closed|abort|broken)|broken pipe).*$", Pattern.CASE_INSENSITIVE);

    final StaticResourceResolver staticResourceResolver;
    final NettyHttpServerConfiguration serverConfiguration;
    final RequestArgumentSatisfier requestArgumentSatisfier;
    final Supplier<ExecutorService> ioExecutorSupplier;
    final boolean multipartEnabled;
    final MessageBodyHandlerRegistry messageBodyHandlerRegistry;
    ExecutorService ioExecutor;
    final ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher;
    final ApplicationEventPublisher<HttpRequestReceivedEvent> receivedPublisher;
    final RouteExecutor routeExecutor;
    final ConversionService conversionService;
    /**
     * This is set to {@code true} if <i>any</i> {@link HttpPipelineBuilder} has a logging handler.
     * When this is not set, we can do a shortcut for performance.
     */
    boolean supportLoggingHandler = false;

    /**
     * @param serverConfiguration               The Netty HTTP server configuration
     * @param embeddedServerContext             The embedded server context
     * @param ioExecutor                        The IO executor
     * @param terminateEventPublisher           The terminate event publisher
     * @param receivedPublisher                 The received publisher
     * @param conversionService                 The conversion service
     */
    RoutingInBoundHandler(
        NettyHttpServerConfiguration serverConfiguration,
        NettyEmbeddedServices embeddedServerContext,
        Supplier<ExecutorService> ioExecutor,
        ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher,
        ApplicationEventPublisher<HttpRequestReceivedEvent> receivedPublisher, ConversionService conversionService) {
        this.staticResourceResolver = embeddedServerContext.getStaticResourceResolver();
        this.messageBodyHandlerRegistry = embeddedServerContext.getMessageBodyHandlerRegistry();
        this.ioExecutorSupplier = ioExecutor;
        this.requestArgumentSatisfier = embeddedServerContext.getRequestArgumentSatisfier();
        this.serverConfiguration = serverConfiguration;
        this.terminateEventPublisher = terminateEventPublisher;
        this.receivedPublisher = receivedPublisher;
        Optional<Boolean> isMultiPartEnabled = serverConfiguration.getMultipart().getEnabled();
        this.multipartEnabled = isMultiPartEnabled.isEmpty() || isMultiPartEnabled.get();
        this.routeExecutor = embeddedServerContext.getRouteExecutor();
        this.conversionService = conversionService;
    }

    private void cleanupRequest(NettyHttpRequest<?> request) {
        try {
            request.release();
        } finally {
            if (!terminateEventPublisher.isEmpty()) {
                try {
                    terminateEventPublisher.publishEvent(new HttpRequestTerminatedEvent(request));
                } catch (Exception e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error publishing request terminated event: {}", e.getMessage(), e);
                    }
                }
            }
        }
    }

    @Override
    public void responseWritten(Object attachment) {
        if (attachment != null) {
            cleanupRequest((NettyHttpRequest<?>) attachment);
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
                AvailableNettyByteBody.empty(),
                ctx,
                conversionService,
                serverConfiguration
            );
            outboundAccess.attachment(errorRequest);
            if (receivedPublisher != ApplicationEventPublisher.NO_OP) {
                receivedPublisher.publishEvent(new HttpRequestReceivedEvent(errorRequest));
            }
            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(errorRequest)).propagate()) {
                new NettyRequestLifecycle(this, outboundAccess).handleException(errorRequest, e.getCause() == null ? e : e.getCause());
            }
            return;
        }
        if (receivedPublisher != ApplicationEventPublisher.NO_OP) {
            receivedPublisher.publishEvent(new HttpRequestReceivedEvent(mnRequest));
        }
        if (supportLoggingHandler && ctx.pipeline().get(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER) != null) {
            // Micronaut Session needs this to extract values from the Micronaut Http Request for logging
            AttributeKey<NettyHttpRequest> key = AttributeKey.valueOf(NettyHttpRequest.class.getSimpleName());
            ctx.channel().attr(key).set(mnRequest);
        }
        outboundAccess.attachment(mnRequest);
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(mnRequest)).propagate()) {
            new NettyRequestLifecycle(this, outboundAccess).handleNormal(mnRequest);
        }
    }

    public void writeResponse(OutboundAccess outboundAccess,
                              NettyHttpRequest<?> nettyHttpRequest,
                              HttpResponse<?> response,
                              Throwable throwable) {
        if (throwable != null) {
            response = routeExecutor.createDefaultErrorResponse(nettyHttpRequest, throwable);
        }
        if (response != null) {
            ExecutionFlow<? extends ByteBodyHttpResponse<?>> finalResponse =
                new NettyResponseLifecycle(this, nettyHttpRequest).encodeHttpResponseSafe(nettyHttpRequest, response);
            finalResponse.onComplete((r, t) -> {
                ByteBodyHttpResponse<?> encodedResponse;
                if (t != null) {
                    // fallback of the fallback...
                    encodedResponse = ByteBodyHttpResponseWrapper.wrap(HttpResponse.serverError(), AvailableNettyByteBody.empty());
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
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE), AvailableNettyByteBody.empty());
        }
    }

    ExecutorService getIoExecutor() {
        ExecutorService executor = this.ioExecutor;
        if (executor == null) {
            synchronized (this) { // double check
                executor = this.ioExecutor;
                if (executor == null) {
                    executor = this.ioExecutorSupplier.get();
                    this.ioExecutor = executor;
                }
            }
        }
        return executor;
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
