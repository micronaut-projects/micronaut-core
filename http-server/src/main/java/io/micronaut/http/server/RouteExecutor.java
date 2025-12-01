/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.server;

import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.BeanCreationException;
import io.micronaut.context.propagation.instrument.execution.ContextPropagatingExecutorService;
import io.micronaut.context.propagation.instrument.execution.ContextPropagatingScheduledExecutorService;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.MethodReference;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.DefaultRouteInfo;
import io.micronaut.web.router.MethodBasedRouteInfo;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CorePublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static io.micronaut.core.util.KotlinUtils.isKotlinCoroutineSuspended;
import static io.micronaut.inject.beans.KotlinExecutableMethodUtils.isKotlinFunctionReturnTypeUnit;

/**
 * A class responsible for executing routes.
 *
 * @author James Kleeh
 * @since 3.0.0
 */
@Singleton
public final class RouteExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RouteExecutor.class);
    /**
     * Also present in netty RoutingInBoundHandler.
     */
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
        "^.*(?:connection (?:reset|closed|abort|broken)|broken pipe).*$", Pattern.CASE_INSENSITIVE);

    final Router router;
    final BeanContext beanContext;
    final RequestArgumentSatisfier requestArgumentSatisfier;
    final HttpServerConfiguration serverConfiguration;
    final ErrorResponseProcessor<?> errorResponseProcessor;
    private final ExecutorSelector executorSelector;
    private final Optional<CoroutineHelper> coroutineHelper;
    private final ConversionService conversionService;

    /**
     * Default constructor.
     *
     * @param router                   The router
     * @param beanContext              The bean context
     * @param requestArgumentSatisfier The request argument satisfier
     * @param serverConfiguration      The server configuration
     * @param errorResponseProcessor   The error response processor
     * @param executorSelector         The executor selector
     */
    public RouteExecutor(Router router,
                         BeanContext beanContext,
                         RequestArgumentSatisfier requestArgumentSatisfier,
                         HttpServerConfiguration serverConfiguration,
                         ErrorResponseProcessor<?> errorResponseProcessor,
                         ExecutorSelector executorSelector) {
        this.router = router;
        this.beanContext = beanContext;
        this.requestArgumentSatisfier = requestArgumentSatisfier;
        this.serverConfiguration = serverConfiguration;
        this.errorResponseProcessor = errorResponseProcessor;
        this.executorSelector = executorSelector;
        this.coroutineHelper = beanContext.findBean(CoroutineHelper.class);
        this.conversionService = beanContext.getConversionService();
    }

    /**
     * @return The router
     */
    public @NonNull Router getRouter() {
        return router;
    }

    /**
     * @return The request argument satisfier
     */
    @Internal
    public @NonNull RequestArgumentSatisfier getRequestArgumentSatisfier() {
        return requestArgumentSatisfier;
    }

    /**
     * @return The error response processor
     */
    public @NonNull ErrorResponseProcessor<?> getErrorResponseProcessor() {
        return errorResponseProcessor;
    }

    /**
     * @return The executor selector
     */
    public @NonNull ExecutorSelector getExecutorSelector() {
        return executorSelector;
    }

    /**
     * @return The kotlin coroutine helper
     */
    public Optional<CoroutineHelper> getCoroutineHelper() {
        return coroutineHelper;
    }

    @Nullable
    UriRouteMatch<Object, Object> findRouteMatch(HttpRequest<?> httpRequest) {
        return router.findClosest(httpRequest);
    }

    static void setRouteAttributes(HttpRequest<?> request, UriRouteMatch<Object, Object> route) {
        setRouteAttributes(request, (RouteMatch<?>) route);
        BasicHttpAttributes.setUriTemplate(request, route.getRouteInfo().getUriMatchTemplate().toString());
    }

    static void setRouteAttributes(HttpRequest<?> request, RouteMatch<?> route) {
        RouteAttributes.setRouteMatch(request, route);
        RouteAttributes.setRouteInfo(request, route.getRouteInfo());
    }

    /**
     * Creates a default error response. Should be used when a response could not be retrieved
     * from any other method.
     *
     * @param httpRequest The request that case the exception
     * @param cause       The exception that occurred
     * @return A response to represent the exception
     */
    public MutableHttpResponse<?> createDefaultErrorResponse(HttpRequest<?> httpRequest,
                                                             Throwable cause) {
        logException(cause);
        MutableHttpResponse<?> mutableHttpResponse = HttpResponse.serverError();
        RouteAttributes.setException(mutableHttpResponse, cause);
        RouteAttributes.setRouteInfo(mutableHttpResponse, new DefaultRouteInfo<>(
                ReturnType.of(MutableHttpResponse.class, Argument.OBJECT_ARGUMENT),
                Object.class,
                true,
                false));
        try {
            mutableHttpResponse = errorResponseProcessor.processResponse(
                ErrorContext.builder(httpRequest)
                    .cause(cause)
                    .errorMessage("Internal Server Error: " + cause.getMessage())
                    .build(), mutableHttpResponse);
        } catch (Exception e) {
            logException(e);
        }
        applyConfiguredHeaders(mutableHttpResponse.getHeaders());
        if (mutableHttpResponse.getContentType().isEmpty() && httpRequest.getMethod() != HttpMethod.HEAD) {
            return mutableHttpResponse.contentType(MediaType.APPLICATION_JSON_TYPE);
        }
        return mutableHttpResponse;
    }

    /**
     * @param request    The request
     * @param finalRoute The route
     * @return The default content type declared on the route
     */
    public MediaType resolveDefaultResponseContentType(HttpRequest<?> request, RouteInfo<?> finalRoute) {
        final List<MediaType> producesList = finalRoute.getProduces();
        if (request != null) {
            final Iterator<MediaType> i = request.accept().iterator();
            if (i.hasNext()) {
                final MediaType mt = i.next();
                if (producesList.contains(mt)) {
                    return mt;
                }
            }
        }

        MediaType defaultResponseMediaType;
        final Iterator<MediaType> produces = producesList.iterator();
        if (produces.hasNext()) {
            defaultResponseMediaType = produces.next();
        } else {
            defaultResponseMediaType = MediaType.APPLICATION_JSON_TYPE;
        }
        return defaultResponseMediaType;
    }

    private MutableHttpResponse<?> notFoundErrorResponse(HttpRequest<?> request) {
        MutableHttpResponse<?> response = errorResponseProcessor.processResponse(
            ErrorContext.builder(request)
                .errorMessage("Page Not Found")
                .build(), HttpResponse.notFound());
        if (response.getContentType().isEmpty() && request.getMethod() != HttpMethod.HEAD) {
            return response.contentType(MediaType.APPLICATION_JSON_TYPE);
        }
        return response;
    }

    void logException(Throwable cause) {
        //handling connection reset by peer exceptions
        if (isIgnorable(cause)) {
            logIgnoredException(cause);
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Unexpected error occurred: {}", cause.getMessage(), cause);
            }
        }
    }

    static boolean isIgnorable(Throwable cause) {
        if (cause instanceof ClosedChannelException || cause instanceof BaseSharedBuffer.IncorrectContentLengthException) {
            return true;
        }
        String message = cause.getMessage();
        return cause instanceof IOException && message != null && IGNORABLE_ERROR_MESSAGE.matcher(message).matches();
    }

    static void logIgnoredException(Throwable cause) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Swallowed an IOException caused by client connectivity: {}", cause.getMessage(), cause);
        }
    }

    RouteMatch<?> findErrorRoute(Throwable cause,
                                         Class<?> declaringType,
                                         HttpRequest<?> httpRequest) {
        RouteMatch<?> errorRoute = null;
        if (cause instanceof BeanCreationException beanCreationException && declaringType != null) {
            // If the controller could not be instantiated, don't look for a local error route
            Optional<Class<?>> rootBeanType = beanCreationException.getRootBeanType().map(BeanType::getBeanType);
            if (rootBeanType.isPresent() && declaringType == rootBeanType.get()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to instantiate [{}]. Skipping lookup of a local error route", declaringType.getName());
                }
                declaringType = null;
            }
        }

        // First try to find an error route by the exception
        if (declaringType != null) {
            // handle error with a method that is non-global with exception
            errorRoute = router.findErrorRoute(declaringType, cause, httpRequest).orElse(null);
        }
        if (errorRoute == null) {
            // handle error with a method that is global with exception
            errorRoute = router.findErrorRoute(cause, httpRequest).orElse(null);
        }

        if (errorRoute == null) {
            // Second try is by status route if the status is known
            HttpStatus errorStatus = null;
            if (cause instanceof UnsatisfiedRouteException || cause instanceof CodecException) {
                // when arguments do not match, then there is UnsatisfiedRouteException, we can handle this with a routed bad request
                // or when incoming request body is not in the expected format
                errorStatus = HttpStatus.BAD_REQUEST;
            } else if (cause instanceof HttpStatusException statusException) {
                errorStatus = statusException.getStatus();
            }

            if (errorStatus != null) {
                if (declaringType != null) {
                    // handle error with a method that is non-global with bad request
                    errorRoute = router.findStatusRoute(declaringType, errorStatus, httpRequest).orElse(null);
                }
                if (errorRoute == null) {
                    // handle error with a method that is global with bad request
                    errorRoute = router.findStatusRoute(errorStatus, httpRequest).orElse(null);
                }
            }
        }

        if (errorRoute != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found matching exception handler for exception [{}]: {}", cause.getMessage(), errorRoute);
            }
            setRouteAttributes(httpRequest, errorRoute);
            requestArgumentSatisfier.fulfillArgumentRequirementsBeforeFilters(errorRoute, httpRequest);
        }

        return errorRoute;
    }

    RouteMatch<Object> findStatusRoute(HttpRequest<?> incomingRequest, int status, RouteInfo<?> finalRoute) {
        Class<?> declaringType = finalRoute.getDeclaringType();
        // handle re-mapping of errors
        RouteMatch<Object> statusRoute = null;
        // if declaringType is not null, this means it's a locally marked method handler
        if (declaringType != null) {
            statusRoute = router.findStatusRoute(declaringType, status, incomingRequest)
                .orElseGet(() -> router.findStatusRoute(status, incomingRequest).orElse(null));
        }
        return statusRoute;
    }

    ExecutorService findExecutor(RouteInfo<?> routeInfo) {
        // Select the most appropriate Executor
        ExecutorService executor;
        if (routeInfo instanceof MethodReference<?, ?> methodReference) {
            executor = executorSelector.select(methodReference, serverConfiguration.getThreadSelection()).orElse(null);
        } else if (routeInfo instanceof MethodBasedRouteInfo<?, ?> methodBasedRouteInfo) {
            executor = executorSelector.select(methodBasedRouteInfo.getTargetMethod().getExecutableMethod(), serverConfiguration.getThreadSelection()).orElse(null);
        } else {
            executor = null;
        }
        return executor;
    }

    private <T> Flux<T> applyExecutorToPublisher(Publisher<T> publisher, @Nullable ExecutorService executor, PropagatedContext propagatedContext) {
        if (executor == null) {
            return Flux.from(publisher).subscribeOn(Schedulers.fromExecutor(command -> propagatedContext.wrap(command).run()));
        }
        Optional<ExecutorService> wrappedTarget = ContextPropagatingExecutorService.unwrap(executor);
        if (wrappedTarget.isPresent()) {
            executor = wrappedTarget.get();
        }
        if (executor instanceof ScheduledExecutorService scheduledExecutorService) {
            executor = new ContextPropagatingScheduledExecutorService(
                scheduledExecutorService,
                propagatedContext
            );
        } else {
            ExecutorService finalExecutor = executor;
            executor = new ContextPropagatingExecutorService(finalExecutor, propagatedContext);
        }
        final Scheduler scheduler = Schedulers.fromExecutorService(executor);
        return Flux.from(publisher)
            .subscribeOn(scheduler)
            .publishOn(scheduler);
    }

    private ExecutionFlow<MutableHttpResponse<?>> fromImperativeExecute(PropagatedContext propagatedContext,
                                                                        HttpRequest<?> request,
                                                                        RouteInfo<?> routeInfo,
                                                                        @Nullable Object body) {
        // performance optimization: check for common body types
        boolean shortCircuit = body instanceof String || body instanceof byte[];

        // this is a bit messy to avoid type pollution performance issues
        MutableHttpResponse<?> outgoingResponse;
        if (!shortCircuit && body instanceof MutableHttpResponse<?> mut) {
            outgoingResponse = mut;
        } else if (!shortCircuit && body instanceof HttpResponse<?> httpResponse) {
            outgoingResponse = httpResponse.toMutableResponse();
        } else {
            MutableHttpResponse<Object> mutableHttpResponse = forStatus(routeInfo, null);
            if (body != null) {
                mutableHttpResponse = mutableHttpResponse.body(body);
            }
            return ExecutionFlow.just(mutableHttpResponse);
        }

        final Argument<?> bodyArgument = routeInfo.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        if (bodyArgument.isAsyncOrReactive()) {
            return fromPublisher(
                processPublisherBody(propagatedContext, request, outgoingResponse, routeInfo)
            );
        }
        return ExecutionFlow.just(outgoingResponse);
    }

    ExecutionFlow<HttpResponse<?>> callRoute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
        RouteInfo<?> routeInfo = routeMatch.getRouteInfo();
        ExecutorService executorService = routeInfo.getExecutor(serverConfiguration.getThreadSelection());
        ExecutionFlow<HttpResponse<?>> executeMethodResponseFlow;
        if (executorService != null) {
            if (routeInfo.isSuspended()) {
                executeMethodResponseFlow = ReactiveExecutionFlow.fromPublisher(Mono.deferContextual(contextView -> {
                        coroutineHelper.ifPresent(helper -> helper.setupCoroutineContext(request, contextView, propagatedContext));
                        return Mono.from(
                            ReactiveExecutionFlow.fromFlow(executeRouteAndConvertBody(propagatedContext, routeMatch, request)).toPublisher()
                        );
                    }));
            } else if (routeInfo.isReactive()) {
                executeMethodResponseFlow = ReactiveExecutionFlow.async(executorService, () -> executeRouteAndConvertBody(propagatedContext, routeMatch, request));
            } else {
                executeMethodResponseFlow = ExecutionFlow.async(executorService, () -> executeRouteAndConvertBody(propagatedContext, routeMatch, request));
            }
        } else {
            if (routeInfo.isSuspended()) {
                executeMethodResponseFlow = ReactiveExecutionFlow.fromPublisher(Mono.deferContextual(contextView -> {
                        coroutineHelper.ifPresent(helper -> helper.setupCoroutineContext(request, contextView, propagatedContext));
                        return Mono.from(
                            ReactiveExecutionFlow.fromFlow(executeRouteAndConvertBody(propagatedContext, routeMatch, request)).toPublisher()
                        );
                    }));
            } else if (routeInfo.isReactive()) {
                executeMethodResponseFlow = ReactiveExecutionFlow.fromFlow(executeRouteAndConvertBody(propagatedContext, routeMatch, request));
            } else {
                executeMethodResponseFlow = executeRouteAndConvertBody(propagatedContext, routeMatch, request);
            }
        }
        return executeMethodResponseFlow;
    }

    private ExecutionFlow<HttpResponse<?>> executeRouteAndConvertBody(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> httpRequest) {
        try (PropagatedContext.Scope ignore = propagatedContext.plus(new ServerHttpRequestContext(httpRequest)).propagate()) {
            try {
                requestArgumentSatisfier.fulfillArgumentRequirementsAfterFilters(routeMatch, httpRequest);
                Object body = routeMatch.execute();
                if (body instanceof Optional optional) {
                    body = optional.orElse(null);
                }
                return createResponseForBody(propagatedContext, httpRequest, body, routeMatch.getRouteInfo(), routeMatch);
            } catch (Throwable e) {
                return ExecutionFlow.error(e);
            }
        }
    }

    ExecutionFlow<HttpResponse<?>> createResponseForBody(PropagatedContext propagatedContext,
                                                         HttpRequest<?> request,
                                                         Object body,
                                                         RouteInfo<?> routeInfo,
                                                         @Nullable
                                                         RouteMatch<?> routeMatch) {
        ExecutionFlow<MutableHttpResponse<?>> outgoingResponse;
        MutableHttpResponse<?> response = null;
        if (body == null) {
            if (routeInfo.isVoid()) {
                response = voidResponse(routeInfo);
            } else if (serverConfiguration.isNotFoundOnMissingBody()) {
                response = notFoundErrorResponse(request);
            } else {
                response = noContentResponse(routeInfo);
            }
        } else if (body instanceof String) {
            // Micro-optimization for String values
            response = forStatus(routeInfo, null).body(body);
        } else if (body instanceof HttpStatus httpStatus) {
            response = HttpResponse.status(httpStatus);
        }
        if (response != null) {
            return ExecutionFlow.just(finaliseResponse(request, routeInfo, routeMatch, response));
        }
        if (routeInfo.isImperative()) {
            outgoingResponse = fromImperativeExecute(propagatedContext, request, routeInfo, body);
        } else {
            if (routeInfo.isAsync() && body != null) {
                outgoingResponse = CompletableFutureExecutionFlow.just(
                    fromCompletionStage(request, (CompletionStage<Object>) body, routeInfo)
                );
            } else {
                // special case HttpResponse because FullNettyClientHttpResponse implements Completable...
                boolean isReactive = routeInfo.isReactive() || (Publishers.isConvertibleToPublisher(body) && !(body instanceof HttpResponse<?>));
                if (isReactive && body != null) {
                    Publisher<Object> publisher = Publishers.convertToPublisher(conversionService, body);
                    outgoingResponse = ReactiveExecutionFlow.fromPublisher(
                        ReactivePropagation.propagate(
                            propagatedContext,
                            fromReactiveExecute(propagatedContext, request, publisher, routeInfo)
                        )
                    );
                } else {
                    if (routeInfo.isSuspended()) {
                        outgoingResponse = fromKotlinCoroutineExecute(propagatedContext, request, body, routeInfo);
                    } else {
                        outgoingResponse = fromImperativeExecute(propagatedContext, request, routeInfo, body);
                    }
                }
            }
        }
        response = outgoingResponse.tryCompleteValue();
        if (response != null) {
            return ExecutionFlow.just(finaliseResponse(request, routeInfo, routeMatch, response));
        }
        return outgoingResponse.map(res -> finaliseResponse(request, routeInfo, routeMatch, res));
    }

    private MutableHttpResponse<?> finaliseResponse(HttpRequest<?> request, RouteInfo<?> routeInfo, RouteMatch<?> routeMatch, MutableHttpResponse<?> response) {
        // for head request we never emit the body
        if (request != null && request.getMethod().equals(HttpMethod.HEAD)) {
            final Object o = response.getBody().orElse(null);
            if (o instanceof ReferenceCounted referenceCounted) {
                referenceCounted.release();
            }
            response.body(null);
            if (o != null) {
                RouteAttributes.setHeadBody(response, o);
            }
        }
        applyConfiguredHeaders(response.getHeaders());
        if (routeMatch != null) {
            RouteAttributes.setRouteMatch(response, routeMatch);
        }
        RouteAttributes.setRouteInfo(response, routeInfo);
        response.bodyWriter((MessageBodyWriter) routeInfo.getMessageBodyWriter());
        return response;
    }

    private ExecutionFlow<MutableHttpResponse<?>> fromKotlinCoroutineExecute(PropagatedContext propagatedContext, HttpRequest<?> request, Object body, RouteInfo<?> routeInfo) {
        boolean isKotlinFunctionReturnTypeUnit =
            routeInfo instanceof MethodBasedRouteInfo<?, ?> mbri &&
                isKotlinFunctionReturnTypeUnit(mbri.getTargetMethod().getExecutableMethod());
        final Supplier<CompletableFuture<?>> supplier = ContinuationArgumentBinder.extractContinuationCompletableFutureSupplier(request);
        if (isKotlinCoroutineSuspended(body)) {
            Mono<MutableHttpResponse<?>> responsePublisher = Mono.fromCompletionStage(supplier)
                .flatMap(obj -> {
                    MutableHttpResponse<?> response;
                    if (obj instanceof HttpResponse<?> httpResponse) {
                        response = httpResponse.toMutableResponse();
                        final Argument<?> bodyArgument = routeInfo.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                        if (bodyArgument.isAsyncOrReactive()) {
                            return processPublisherBody(propagatedContext, request, response, routeInfo);
                        }
                    } else {
                        response = forStatus(routeInfo, null);
                        if (!isKotlinFunctionReturnTypeUnit) {
                            response = response.body(obj);
                        }
                    }
                    return Mono.just(response);
                });
            if (serverConfiguration.isNotFoundOnMissingBody()) {
                responsePublisher = responsePublisher
                    .switchIfEmpty(Mono.fromCallable(() -> notFoundErrorResponse(request)));
            }
            return ReactiveExecutionFlow.fromPublisher(responsePublisher);
        }
        Object suspendedBody = isKotlinFunctionReturnTypeUnit ? null : body;
        return fromImperativeExecute(propagatedContext, request, routeInfo, suspendedBody);
    }

    private CorePublisher<MutableHttpResponse<?>> fromReactiveExecute(PropagatedContext propagatedContext,
                                                                      HttpRequest<?> request,
                                                                      Publisher<Object> publisher,
                                                                      RouteInfo<?> routeInfo) {
        boolean isSingle = routeInfo.isSpecifiedSingle() || routeInfo.isReactive() && routeInfo.isSingleResult() || Publishers.isSingle(publisher.getClass());
        boolean isCompletable = !isSingle && routeInfo.isVoid() && routeInfo.isCompletable();
        if (isSingle || isCompletable) {
            // full response case
            return Flux.from(publisher)
                .flatMap(o -> {
                    if (o instanceof Optional<?> optional) {
                        if (optional.isPresent()) {
                            o = optional.get();
                        } else {
                            return Mono.empty();
                        }
                    }
                    MutableHttpResponse<?> singleResponse;
                    if (o instanceof HttpResponse<?> httpResponse) {
                        singleResponse = httpResponse.toMutableResponse();
                        final Argument<?> bodyArgument = routeInfo.getReturnType() //Mono
                            .getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT) //HttpResponse
                            .getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT); //Mono
                        if (bodyArgument.isAsyncOrReactive()) {
                            return processPublisherBody(propagatedContext, request, singleResponse, routeInfo);
                        }
                    } else if (o instanceof HttpStatus status) {
                        singleResponse = forStatus(routeInfo, status);
                    } else {
                        singleResponse = forStatus(routeInfo, null)
                            .body(o);
                    }
                    return Flux.just(singleResponse);
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    MutableHttpResponse<?> singleResponse;
                    if (isCompletable || routeInfo.isVoid()) {
                        singleResponse = voidResponse(routeInfo);
                    } else if (serverConfiguration.isNotFoundOnMissingBody()) {
                        singleResponse = notFoundErrorResponse(request);
                    } else {
                        singleResponse = noContentResponse(routeInfo);
                    }
                    return singleResponse;
                }))
                .contextWrite(context -> ReactorPropagation.addPropagatedContext(context, propagatedContext).put(ServerRequestContext.KEY, request));
        }
        // streaming case
        Argument<?> typeArgument = routeInfo.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        if (HttpResponse.class.isAssignableFrom(typeArgument.getType())) {
            // a response stream
            Publisher<HttpResponse<?>> bodyPublisher = (Publisher) publisher;
            Flux<MutableHttpResponse<?>> response = Flux.from(bodyPublisher)
                .map(HttpResponse::toMutableResponse);
            Argument<?> bodyArgument = typeArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (bodyArgument.isAsyncOrReactive()) {
                return response.flatMap(resp ->
                    processPublisherBody(propagatedContext, request, resp, routeInfo));
            }
            return response.contextWrite(context -> ReactorPropagation.addPropagatedContext(context, propagatedContext).put(ServerRequestContext.KEY, request));
        }
        return processPublisherBody(propagatedContext, request, forStatus(routeInfo, null), false, publisher, routeInfo);
    }

    private MutableHttpResponse<Object> voidResponse(RouteInfo<?> routeInfo) {
        return forStatus(routeInfo, HttpStatus.OK)
            .header(HttpHeaders.CONTENT_LENGTH, "0");
    }

    private MutableHttpResponse<Object> noContentResponse(RouteInfo<?> routeInfo) {
        return forStatus(routeInfo, HttpStatus.NO_CONTENT)
            .header(HttpHeaders.CONTENT_LENGTH, "0");
    }

    @NonNull
    private CompletionStage<MutableHttpResponse<?>> fromCompletionStage(@NonNull HttpRequest<?> request,
                                                                        @NonNull CompletionStage<Object> completionStage,
                                                                        @NonNull RouteInfo<?> routeInfo) {
        return completionStage.thenCompose(asyncBody -> {
            MutableHttpResponse<?> mutableResponse;
            if (asyncBody instanceof Optional<?> optional) {
                if (optional.isPresent()) {
                    asyncBody = optional.get();
                } else if (serverConfiguration.isNotFoundOnMissingBody()) {
                    return CompletableFuture.completedStage(notFoundErrorResponse(request));
                } else {
                    return CompletableFuture.completedStage(noContentResponse(routeInfo));
                }
            }
            boolean explicitResponse = false;
            if (asyncBody instanceof HttpResponse<?> httpResponse) {
                mutableResponse = httpResponse.toMutableResponse();
                final Argument<?> bodyArgument = routeInfo.getReturnType() // CompletionStage
                    .getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT) // HttpResponse
                    .getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT); // CompletionStage
                if (bodyArgument.isAsync()) {
                    CompletionStage<Object> inner = (CompletionStage<Object>) mutableResponse.body();
                    return inner.thenApply(innerBody -> {
                        if (innerBody == null) {
                            return notFoundErrorResponse(request);
                        }
                        return mutableResponse.body(innerBody);
                    });
                }
                explicitResponse = true;
            } else if (asyncBody instanceof HttpStatus status) {
                mutableResponse = forStatus(routeInfo, status);
            } else {
                mutableResponse = forStatus(routeInfo, null)
                    .body(asyncBody);
            }
            if (mutableResponse.body() == null && !explicitResponse) {
                if (routeInfo.isVoid()) {
                    return CompletableFuture.completedStage(voidResponse(routeInfo));
                } else if (serverConfiguration.isNotFoundOnMissingBody()) {
                    return CompletableFuture.completedStage(notFoundErrorResponse(request));
                } else {
                    return CompletableFuture.completedStage(noContentResponse(routeInfo));
                }
            }
            return CompletableFuture.completedStage(mutableResponse);
        });
    }

    private Mono<MutableHttpResponse<?>> processPublisherBody(PropagatedContext propagatedContext,
                                                              HttpRequest<?> request,
                                                              MutableHttpResponse<?> response,
                                                              RouteInfo<?> routeInfo) {
        Object body = response.body();
        if (body == null) {
            return Mono.just(response);
        }
        Publisher<Object> bodyPublisher = Publishers.convertToPublisher(conversionService, body);
        return processPublisherBody(propagatedContext, request, response, Publishers.isSingle(body.getClass()), bodyPublisher, routeInfo);
    }

    private Mono<MutableHttpResponse<?>> processPublisherBody(PropagatedContext propagatedContext,
                                                              HttpRequest<?> request,
                                                              MutableHttpResponse<?> response,
                                                              boolean isSinglePublisher,
                                                              Publisher<Object> bodyPublisher,
                                                              RouteInfo<?> routeInfo) {
        if (isSinglePublisher) {
            return Mono.from(bodyPublisher).map(b -> {
                response.body(b);
                return response;
            });
        }
        MediaType mediaType = response.getContentType().orElseGet(() -> resolveDefaultResponseContentType(request, routeInfo));

        bodyPublisher = applyExecutorToPublisher(
            bodyPublisher,
            findExecutor(routeInfo),
            propagatedContext
        ).contextWrite(cv -> ReactorPropagation.addPropagatedContext(cv, propagatedContext).put(ServerRequestContext.KEY, request));

        return Mono.<MutableHttpResponse<?>>just(response
            .header(HttpHeaders.CONTENT_TYPE, mediaType)
            .body(ReactivePropagation.propagate(propagatedContext, bodyPublisher)))
            .contextWrite(context -> ReactorPropagation.addPropagatedContext(context, propagatedContext).put(ServerRequestContext.KEY, request));
    }

    private void applyConfiguredHeaders(MutableHttpHeaders headers) {
        if (serverConfiguration.isDateHeader() && !headers.contains(HttpHeaders.DATE)) {
            headers.date(LocalDateTime.now());
        }
        if (headers.get(HttpHeaders.SERVER) == null) {
            serverConfiguration.getServerHeader()
                .ifPresent(header -> headers.add(HttpHeaders.SERVER, header));
        }
    }

    private MutableHttpResponse<Object> forStatus(RouteInfo<?> routeMatch) {
        return forStatus(routeMatch, HttpStatus.OK);
    }

    private MutableHttpResponse<Object> forStatus(RouteInfo<?> routeMatch, HttpStatus defaultStatus) {
        HttpStatus status = routeMatch.findStatus(defaultStatus);
        return HttpResponse.status(status);
    }

    static <K> ExecutionFlow<K> fromPublisher(Publisher<K> publisher) {
        return ReactiveExecutionFlow.fromPublisher(publisher);
    }

}
