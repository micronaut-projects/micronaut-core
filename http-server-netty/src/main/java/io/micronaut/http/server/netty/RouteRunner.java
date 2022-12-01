package io.micronaut.http.server.netty;

import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.web.router.RouteMatch;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

final class RouteRunner {
    private static final Logger LOG = LoggerFactory.getLogger(RouteRunner.class);

    private final RoutingInBoundHandler rib;
    private final ChannelHandlerContext ctx;
    private final NettyHttpRequest<?> nettyRequest;
    private HttpRequest<?> request;

    RouteRunner(RoutingInBoundHandler rib, ChannelHandlerContext ctx, HttpRequest<?> request) {
        this.rib = rib;
        this.ctx = ctx;
        this.nettyRequest = (NettyHttpRequest<?>) request;
        this.request = request;
    }

    void handle() {
        ctx.channel().config().setAutoRead(false);

        if (LOG.isDebugEnabled()) {
            HttpMethod httpMethod = request.getMethod();
            ServerRequestContext.set(request);
            LOG.debug("Request {} {}", httpMethod, request.getUri());
        }

        RouteExecutor.RequestBodyReader requestBodyReader = (routeMatch, hr) -> {
            // handle decoding failure
            DecoderResult decoderResult = nettyRequest.getNativeRequest().decoderResult();
            if (decoderResult.isFailure()) {
                return ExecutionFlow.error(decoderResult.cause());
            }
            // try to fulfill the argument requirements of the route
            RouteMatch<?> route = rib.requestArgumentSatisfier.fulfillArgumentRequirements(routeMatch, request, false);
            if (rib.shouldReadBody(nettyRequest, route)) {
                return ReactiveExecutionFlow.fromPublisher(
                    Mono.create(emitter -> rib.httpContentProcessorResolver.resolve(nettyRequest, route)
                        .subscribe(rib.buildSubscriber(nettyRequest, route, emitter))
                    ));
            }
            ctx.read();
            return ExecutionFlow.just(route);
        };

        ExecutionFlow<MutableHttpResponse<?>> responseFlow;

        // handle decoding failure
        DecoderResult decoderResult = nettyRequest.getNativeRequest().decoderResult();
        if (decoderResult.isFailure()) {
            Throwable cause = decoderResult.cause();
            HttpStatus status = cause instanceof TooLongFrameException ? HttpStatus.REQUEST_ENTITY_TOO_LARGE : HttpStatus.BAD_REQUEST;
            responseFlow = rib.routeExecutor.onStatusError(
                requestBodyReader,
                request,
                HttpResponse.status(status),
                status.getReason()
            );
        } else {
            responseFlow = rib.routeExecutor.executeRoute(requestBodyReader, request, rib.multipartEnabled, rib);
        }
        responseFlow
            .onComplete((response, throwable) -> rib.writeResponse(ctx, nettyRequest, response, throwable));
    }
}
