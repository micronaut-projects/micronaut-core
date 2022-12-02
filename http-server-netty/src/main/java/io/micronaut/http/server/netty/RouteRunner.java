package io.micronaut.http.server.netty;

import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.web.router.RouteMatch;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

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
                HttpProcessorListener processorListener = new HttpProcessorListener(nettyRequest.isFormOrMultipartData() ?
                    new FormRouteCompleter(rib, nettyRequest, route) :
                    new BaseRouteCompleter(rib, nettyRequest, route));
                rib.httpContentProcessorResolver.resolve(nettyRequest, route)
                    .subscribe(processorListener);
                return CompletableFutureExecutionFlow.just(processorListener.completion);
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

    private static class HttpProcessorListener extends CompletionAwareSubscriber<Object> {
        private Subscription s;

        final BaseRouteCompleter completer;
        final CompletableFuture<RouteMatch<?>> completion = new CompletableFuture<>();

        private HttpProcessorListener(BaseRouteCompleter completer) {
            this.completer = completer;
        }

        private void checkDemand() {
            if (completer.needsInput) {
                s.request(1);
                completer.needsInput = false;
            }
        }

        @Override
        protected void doOnSubscribe(Subscription subscription) {
            this.s = subscription;
            subscription.request(1);
            completer.checkDemand = this::checkDemand;
        }

        @Override
        protected void doOnNext(Object message) {
            boolean wasExecuted = completer.execute;
            completer.add(message);
            if (!wasExecuted && completer.execute) {
                executeRoute();
            }
            checkDemand();
        }

        @Override
        protected void doOnError(Throwable t) {
            completer.completeFailure(t);
            // this may drop the exception if the route has already been executed. However, that is
            // only the case if there are publisher parameters, and those will still receive the
            // failure. Hopefully.
            completion.completeExceptionally(t);
        }

        @Override
        protected void doOnComplete() {
            boolean wasExecuted = completer.execute;
            completer.completeSuccess();
            if (!wasExecuted && completer.execute) {
                executeRoute();
            }
        }

        private void executeRoute() {
            completion.complete(completer.routeMatch);
        }
    }
}
