package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Internal
final class RouteRunner {
    private static final Logger LOG = LoggerFactory.getLogger(RouteRunner.class);

    private final RoutingInBoundHandler rib;
    private final ChannelHandlerContext ctx;
    private final NettyHttpRequest<?> request;

    RouteRunner(RoutingInBoundHandler rib, ChannelHandlerContext ctx, NettyHttpRequest<?> request) {
        this.rib = rib;
        this.ctx = ctx;
        this.request = request;
    }

    void handleNormal() {
        ctx.channel().config().setAutoRead(false);

        if (LOG.isDebugEnabled()) {
            HttpMethod httpMethod = request.getMethod();
            ServerRequestContext.set(request);
            LOG.debug("Request {} {}", httpMethod, request.getUri());
        }

        RouteExecutor.RequestBodyReader requestBodyReader = (routeMatch, hr) -> {
            // handle decoding failure
            DecoderResult decoderResult = request.getNativeRequest().decoderResult();
            if (decoderResult.isFailure()) {
                return ExecutionFlow.error(decoderResult.cause());
            }
            // try to fulfill the argument requirements of the route
            RouteMatch<?> route = rib.requestArgumentSatisfier.fulfillArgumentRequirements(routeMatch, request, false);
            if (shouldReadBody(request, route)) {
                BaseRouteCompleter completer = request.isFormOrMultipartData() ?
                    new FormRouteCompleter(new NettyStreamingFileUpload.Factory(rib.serverConfiguration.getMultipart(), rib.getIoExecutor()), rib.conversionService, request, route) :
                    new BaseRouteCompleter(request, route);
                HttpContentProcessor processor = rib.httpContentProcessorResolver.resolve(request, route);
                StreamingDataSubscriber pr = new StreamingDataSubscriber(completer, processor);
                ((StreamedHttpRequest) request.getNativeRequest()).subscribe(pr);
                return CompletableFutureExecutionFlow.just(pr.completion);
            }
            ctx.read();
            return ExecutionFlow.just(route);
        };

        ExecutionFlow<MutableHttpResponse<?>> responseFlow;

        // handle decoding failure
        DecoderResult decoderResult = request.getNativeRequest().decoderResult();
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
            .onComplete((response, throwable) -> rib.writeResponse(ctx, request, response, throwable));
    }

    void handleException(Throwable cause) {
        rib.routeExecutor.filterPublisher(new AtomicReference<>(request), () -> rib.routeExecutor.onError(cause, request))
            .onComplete((response, throwable) -> rib.writeResponse(ctx, request, response, throwable));
    }

    private static boolean shouldReadBody(NettyHttpRequest<?> nettyHttpRequest, RouteMatch<?> routeMatch) {
        if (!HttpMethod.permitsRequestBody(nettyHttpRequest.getMethod())) {
            return false;
        }
        io.netty.handler.codec.http.HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
        if (!(nativeRequest instanceof StreamedHttpRequest)) {
            // Illegal state: The request body is required, so at this point we must have a StreamedHttpRequest
            return false;
        }
        if (routeMatch instanceof MethodBasedRouteMatch<?, ?> methodBasedRouteMatch) {
            if (Arrays.stream(methodBasedRouteMatch.getArguments()).anyMatch(argument -> MultipartBody.class.equals(argument.getType()))) {
                // MultipartBody will subscribe to the request body in MultipartBodyArgumentBinder
                return false;
            }
            if (Arrays.stream(methodBasedRouteMatch.getArguments()).anyMatch(argument -> HttpRequest.class.equals(argument.getType()))) {
                // HttpRequest argument in the method
                return true;
            }
        }
        Optional<Argument<?>> bodyArgument = routeMatch.getBodyArgument()
            .filter(argument -> argument.getAnnotationMetadata().hasAnnotation(Body.class));
        if (bodyArgument.isPresent() && !routeMatch.isSatisfied(bodyArgument.get().getName())) {
            // Body argument in the method
            return true;
        }
        // Might be some body parts
        return !routeMatch.isExecutable();
    }

    private static class StreamingDataSubscriber implements Subscriber<ByteBufHolder> {
        private final List<Object> bufferList = new ArrayList<>(1);
        private final HttpContentProcessor contentProcessor;
        private final BaseRouteCompleter completer;
        private Subscription upstream;
        final CompletableFuture<RouteMatch<?>> completion = new CompletableFuture<>();

        private volatile boolean upstreamRequested = false;
        private boolean downstreamDone = false;

        StreamingDataSubscriber(BaseRouteCompleter completer, HttpContentProcessor contentProcessor) {
            this.completer = completer;
            this.contentProcessor = contentProcessor;
        }

        private void checkDemand() {
            if (completer.needsInput && !upstreamRequested) {
                upstreamRequested = true;
                upstream.request(1);
            }
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (upstream != null) {
                throw new IllegalStateException("Only one upstream subscription allowed");
            }
            upstream = s;
            completer.checkDemand = this::checkDemand;
            checkDemand();
        }

        private void sendToCompleter(Collection<Object> out) throws Throwable {
            for (Object processed : out) {
                boolean wasExecuted = completer.execute;
                completer.add(processed);
                if (!wasExecuted && completer.execute) {
                    executeRoute();
                }
            }
        }

        @Override
        public void onNext(ByteBufHolder holder) {
            upstreamRequested = false;
            if (downstreamDone) {
                // previous error
                holder.release();
                return;
            }
            try {
                bufferList.clear();
                contentProcessor.add(holder, bufferList);
                sendToCompleter(bufferList);
                checkDemand();
            } catch (Throwable t) {
                handleError(t);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (downstreamDone) {
                // previous error
                LOG.warn("Downstream already complete, dropping error", t);
                return;
            }
            handleError(t);
        }

        private void handleError(Throwable t) {
            try {
                upstream.cancel();
            } catch (Throwable o) {
                t.addSuppressed(o);
            }
            try {
                contentProcessor.cancel();
            } catch (Throwable o) {
                t.addSuppressed(o);
            }
            completer.completeFailure(t);
            // this may drop the exception if the route has already been executed. However, that is
            // only the case if there are publisher parameters, and those will still receive the
            // failure. Hopefully.
            completion.completeExceptionally(t);
            downstreamDone = true;
        }

        @Override
        public void onComplete() {
            if (downstreamDone) {
                // previous error
                return;
            }
            try {
                bufferList.clear();
                contentProcessor.complete(bufferList);
                sendToCompleter(bufferList);
                boolean wasExecuted = completer.execute;
                completer.completeSuccess();
                if (!wasExecuted && completer.execute) {
                    executeRoute();
                }
            } catch (Throwable t) {
                handleError(t);
            }
        }

        private void executeRoute() {
            completion.complete(completer.routeMatch);
        }
    }
}
