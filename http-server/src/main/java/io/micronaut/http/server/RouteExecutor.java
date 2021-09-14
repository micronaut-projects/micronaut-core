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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.*;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodReference;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static io.micronaut.core.util.KotlinUtils.isKotlinCoroutineSuspended;
import static io.micronaut.inject.util.KotlinExecutableMethodUtils.isKotlinFunctionReturnTypeUnit;

/**
 * A class responsible for executing routes.
 *
 * @author James Kleeh
 * @since 3.0.0
 */
@Singleton
public final class RouteExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RouteExecutor.class);
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE);

    private final Router router;
    private final BeanContext beanContext;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final HttpServerConfiguration serverConfiguration;
    private final ErrorResponseProcessor<?> errorResponseProcessor;
    private final ExecutorSelector executorSelector;

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
     * Creates a response publisher to represent the response after being handled
     * by any available error route or exception handler.
     *
     * @param t The exception that occurred
     * @param httpRequest The request that caused the exception
     * @return A response publisher
     */
    public Flux<MutableHttpResponse<?>> onError(Throwable t, HttpRequest<?> httpRequest) {
        // find the origination of of the route
        Class declaringType = httpRequest.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class).map(RouteInfo::getDeclaringType).orElse(null);

        final Throwable cause;
        // top level exceptions returned by CompletableFutures. These always wrap the real exception thrown.
        if ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            cause = t.getCause();
        } else {
            cause = t;
        }

        RouteMatch<?> errorRoute = findErrorRoute(cause, declaringType, httpRequest);

        if (errorRoute != null) {
            if (serverConfiguration.isLogHandledExceptions()) {
                logException(cause);
            }
            try {
                AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(httpRequest);
                return buildRouteResponsePublisher(
                        requestReference,
                        Flux.just(errorRoute))
                    .doOnNext(response -> response.setAttribute(HttpAttributes.EXCEPTION, cause))
                    .onErrorResume(throwable -> createDefaultErrorResponsePublisher(requestReference.get(), throwable));
            } catch (Throwable e) {
                return createDefaultErrorResponsePublisher(httpRequest, e).flux();
            }
        } else {
            Optional<BeanDefinition<ExceptionHandler>> optionalDefinition = beanContext.findBeanDefinition(ExceptionHandler.class, Qualifiers.byTypeArgumentsClosest(cause.getClass(), Object.class));

            if (optionalDefinition.isPresent()) {
                BeanDefinition<ExceptionHandler> handlerDefinition = optionalDefinition.get();
                final Optional<ExecutableMethod<ExceptionHandler, Object>> optionalMethod = handlerDefinition.findPossibleMethods("handle").findFirst();
                RouteInfo<Object> routeInfo;
                if (optionalMethod.isPresent()) {
                    routeInfo = new ExecutableRouteInfo(optionalMethod.get(), true);
                } else {
                    routeInfo = new RouteInfo<Object>() {
                        @Override
                        public ReturnType<?> getReturnType() {
                            return ReturnType.of(Object.class);
                        }

                        @Override
                        public Class<?> getDeclaringType() {
                            return handlerDefinition.getBeanType();
                        }

                        @Override
                        public boolean isErrorRoute() {
                            return true;
                        }

                        @Override
                        public List<MediaType> getProduces() {
                            return MediaType.fromType(getDeclaringType())
                                    .map(Collections::singletonList)
                                    .orElse(Collections.emptyList());
                        }
                    };
                }
                Flux<MutableHttpResponse<?>> reactiveSequence = Flux.defer(() -> {
                    ExceptionHandler handler = beanContext.getBean(handlerDefinition);
                    try {
                        if (serverConfiguration.isLogHandledExceptions()) {
                            logException(cause);
                        }
                        Object result = handler.handle(httpRequest, cause);

                        return createResponseForBody(httpRequest, result, routeInfo);
                    } catch (Throwable e) {
                        return createDefaultErrorResponsePublisher(httpRequest, e);
                    }
                });
                final ExecutorService executor = findExecutor(routeInfo);
                if (executor != null) {
                    reactiveSequence = applyExecutorToPublisher(reactiveSequence, executor);
                }
                return reactiveSequence
                        .doOnNext(response -> response.setAttribute(HttpAttributes.EXCEPTION, cause))
                        .onErrorResume(throwable -> createDefaultErrorResponsePublisher(httpRequest, throwable));
            } else {
                if (isIgnorable(cause)) {
                    logIgnoredException(cause);
                    return Flux.empty();
                } else {
                    return createDefaultErrorResponsePublisher(
                            httpRequest,
                            cause).flux();
                }
            }
        }
    }

    /**
     * Creates a default error response. Should be used when a response could not be retrieved
     * from any other method.
     *
     * @param httpRequest The request that case the exception
     * @param cause The exception that occurred
     * @return A response to represent the exception
     */
    public MutableHttpResponse<?> createDefaultErrorResponse(HttpRequest<?> httpRequest,
                                                             Throwable cause) {
        logException(cause);
        final MutableHttpResponse<Object> response = HttpResponse.serverError();
        response.setAttribute(HttpAttributes.EXCEPTION, cause);
        response.setAttribute(HttpAttributes.ROUTE_INFO, new RouteInfo<MutableHttpResponse>() {
            @Override
            public ReturnType<MutableHttpResponse> getReturnType() {
                return ReturnType.of(MutableHttpResponse.class, Argument.OBJECT_ARGUMENT);
            }

            @Override
            public Class<?> getDeclaringType() {
                return Object.class;
            }

            @Override
            public boolean isErrorRoute() {
                return true;
            }
        });
        MutableHttpResponse<?> mutableHttpResponse = errorResponseProcessor.processResponse(
                ErrorContext.builder(httpRequest)
                        .cause(cause)
                        .errorMessage("Internal Server Error: " + cause.getMessage())
                        .build(), response);
        applyConfiguredHeaders(mutableHttpResponse.getHeaders());
        if (!mutableHttpResponse.getContentType().isPresent()) {
            return mutableHttpResponse.contentType(MediaType.APPLICATION_JSON_TYPE);
        }
        return mutableHttpResponse;
    }

    /**
     * @param request The request
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

    /**
     * Executes a route.
     *
     * @param request The request that matched to the route
     * @param executeFilters Whether or not to execute server filters
     * @param routePublisher The route match publisher
     * @return A response publisher
     */
    public Flux<MutableHttpResponse<?>> executeRoute(
            HttpRequest<?> request,
            boolean executeFilters,
            Flux<RouteMatch<?>> routePublisher) {
        AtomicReference<HttpRequest<?>> requestReference = new AtomicReference<>(request);
        return buildResultEmitter(
                requestReference,
                executeFilters,
                routePublisher
        );
    }

    /**
     * Applies server filters to a request/response.
     *
     * @param requestReference The request reference
     * @param upstreamResponsePublisher The original response publisher
     * @return A new response publisher that executes server filters
     */
    public Publisher<MutableHttpResponse<?>> filterPublisher(
            AtomicReference<HttpRequest<?>> requestReference,
            Publisher<MutableHttpResponse<?>> upstreamResponsePublisher) {
        List<HttpFilter> httpFilters = router.findFilters(requestReference.get());
        if (httpFilters.isEmpty()) {
            return upstreamResponsePublisher;
        }
        List<HttpFilter> filters = new ArrayList<>(httpFilters);
        if (filters.isEmpty()) {
            return upstreamResponsePublisher;
        }
        AtomicInteger integer = new AtomicInteger();
        int len = filters.size();
        final Function<MutableHttpResponse<?>, Publisher<MutableHttpResponse<?>>> handleStatusException = (response) ->
                handleStatusException(requestReference.get(), response);
        final Function<Throwable, Publisher<MutableHttpResponse<?>>> onError = (t) ->
                onError(t, requestReference.get());

        ServerFilterChain filterChain = new ServerFilterChain() {
            @SuppressWarnings("unchecked")
            @Override
            public Publisher<MutableHttpResponse<?>> proceed(io.micronaut.http.HttpRequest<?> request) {
                int pos = integer.incrementAndGet();
                if (pos > len) {
                    throw new IllegalStateException("The FilterChain.proceed(..) method should be invoked exactly once per filter execution. The method has instead been invoked multiple times by an erroneous filter definition.");
                }
                if (pos == len) {
                    return upstreamResponsePublisher;
                }
                HttpFilter httpFilter = filters.get(pos);
                return Flux.from((Publisher<MutableHttpResponse<?>>) httpFilter.doFilter(requestReference.getAndSet(request), this))
                        .flatMap(handleStatusException)
                        .onErrorResume(onError);
            }
        };
        HttpFilter httpFilter = filters.get(0);
        return Flux.from((Publisher<MutableHttpResponse<?>>) httpFilter.doFilter(requestReference.get(), filterChain))
                .flatMap(handleStatusException)
                .onErrorResume(onError);
    }

    private Mono<MutableHttpResponse<?>> createDefaultErrorResponsePublisher(HttpRequest<?> httpRequest,
                                                                                  Throwable cause) {
        return Mono.fromCallable(() -> createDefaultErrorResponse(httpRequest, cause));
    }

    private MutableHttpResponse<?> newNotFoundError(HttpRequest<?> request) {
        MutableHttpResponse<?> response = errorResponseProcessor.processResponse(
                ErrorContext.builder(request)
                        .errorMessage("Page Not Found")
                        .build(), HttpResponse.notFound());
        if (!response.getContentType().isPresent()) {
            return response.contentType(MediaType.APPLICATION_JSON_TYPE);
        }
        return response;
    }

    private Mono<MutableHttpResponse<?>> createNotFoundErrorResponsePublisher(HttpRequest<?> httpRequest) {
        return Mono.fromCallable(() -> newNotFoundError(httpRequest));
    }

    private void logException(Throwable cause) {
        //handling connection reset by peer exceptions
        if (isIgnorable(cause)) {
            logIgnoredException(cause);
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Unexpected error occurred: " + cause.getMessage(), cause);
            }
        }
    }

    private boolean isIgnorable(Throwable cause) {
        String message = cause.getMessage();
        return cause instanceof IOException && message != null && IGNORABLE_ERROR_MESSAGE.matcher(message).matches();
    }

    private void logIgnoredException(Throwable cause) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Swallowed an IOException caused by client connectivity: " + cause.getMessage(), cause);
        }
    }

    private RouteMatch<?> findErrorRoute(Throwable cause,
                                         Class<?> declaringType,
                                         HttpRequest<?> httpRequest) {
        RouteMatch<?> errorRoute = null;
        if (cause instanceof BeanCreationException && declaringType != null) {
            // If the controller could not be instantiated, don't look for a local error route
            Optional<Class> rootBeanType = ((BeanCreationException) cause).getRootBeanType().map(BeanType::getBeanType);
            if (rootBeanType.isPresent() && declaringType == rootBeanType.get()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to instantiate [{}]. Skipping lookup of a local error route", declaringType.getName());
                }
                declaringType = null;
            }
        }

        // First try to find an error route by the exception
        if (declaringType != null) {
            // handle error with a method that is non global with exception
            errorRoute = router.findErrorRoute(declaringType, cause, httpRequest).orElse(null);
        }
        if (errorRoute == null) {
            // handle error with a method that is global with exception
            errorRoute = router.findErrorRoute(cause, httpRequest).orElse(null);
        }

        if (errorRoute == null) {
            // Second try is by status route if the status is known
            HttpStatus errorStatus = null;
            if (cause instanceof UnsatisfiedRouteException) {
                // when arguments do not match, then there is UnsatisfiedRouteException, we can handle this with a routed bad request
                errorStatus = HttpStatus.BAD_REQUEST;
            } else if (cause instanceof HttpStatusException) {
                errorStatus = ((HttpStatusException) cause).getStatus();
            }

            if (errorStatus != null) {
                if (declaringType != null) {
                    // handle error with a method that is non global with bad request
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
            errorRoute = requestArgumentSatisfier.fulfillArgumentRequirements(errorRoute, httpRequest, false);
        }

        return errorRoute;
    }

    private Publisher<MutableHttpResponse<?>> handleStatusException(HttpRequest<?> request,
                                                                    MutableHttpResponse<?> response) {
        HttpStatus status = response.status();
        RouteInfo<?> routeInfo = response.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class).orElse(null);

        if (status.getCode() >= 400 && routeInfo != null && !routeInfo.isErrorRoute()) {
            RouteMatch<Object> statusRoute = findStatusRoute(request, status, routeInfo);

            if (statusRoute != null) {
                return executeRoute(
                        request,
                        false,
                        Flux.just(statusRoute)
                );
            }
        }
        return Flux.just(response);
    }

    private RouteMatch<Object> findStatusRoute(HttpRequest<?> incomingRequest, HttpStatus status, RouteInfo<?> finalRoute) {
        Class<?> declaringType = finalRoute.getDeclaringType();
        // handle re-mapping of errors
        RouteMatch<Object> statusRoute = null;
        // if declaringType is not null, this means its a locally marked method handler
        if (declaringType != null) {
            statusRoute = router.findStatusRoute(declaringType, status, incomingRequest)
                    .orElseGet(() -> router.findStatusRoute(status, incomingRequest).orElse(null));
        }
        return statusRoute;
    }

    private ExecutorService findExecutor(RouteInfo<?> routeMatch) {
        // Select the most appropriate Executor
        ExecutorService executor;
        if (routeMatch instanceof MethodReference) {
            executor = executorSelector.select((MethodReference<?, ?>) routeMatch, serverConfiguration.getThreadSelection()).orElse(null);
        } else {
            executor = null;
        }
        return executor;
    }

    private <T> Flux<T> applyExecutorToPublisher(
            Publisher<T> publisher,
            @Nullable ExecutorService executor) {
        if (executor != null) {
            final Scheduler scheduler = Schedulers.fromExecutorService(executor);
            return Flux.from(publisher)
                    .subscribeOn(scheduler)
                    .publishOn(scheduler);
        } else {
            return Flux.from(publisher);
        }
    }

    private boolean isSingle(RouteInfo<?> finalRoute, Class<?> bodyClass) {
        return finalRoute.isSpecifiedSingle() || (finalRoute.isSingleResult() &&
                (finalRoute.isAsync() || finalRoute.isSuspended() || Publishers.isSingle(bodyClass)));
    }

    private MutableHttpResponse<?> toMutableResponse(HttpResponse<?> message) {
        MutableHttpResponse<?> mutableHttpResponse;
        if (message instanceof MutableHttpResponse) {
            mutableHttpResponse = (MutableHttpResponse<?>) message;
        } else {
            HttpStatus httpStatus = message.status();
            mutableHttpResponse = HttpResponse.status(httpStatus, httpStatus.getReason());
            mutableHttpResponse.body(message.body());
            message.getHeaders().forEach((name, value) -> {
                for (String val: value) {
                    mutableHttpResponse.header(name, val);
                }
            });
            mutableHttpResponse.getAttributes().putAll(message.getAttributes());
        }
        return mutableHttpResponse;
    }

    private MutableHttpResponse<?> toMutableResponse(HttpRequest<?> request, RouteInfo<?> routeInfo, HttpStatus defaultHttpStatus, Object body) {
        MutableHttpResponse<?> outgoingResponse;
        if (body instanceof HttpResponse) {
            outgoingResponse = toMutableResponse((HttpResponse<?>) body);
            final Argument<?> bodyArgument = routeInfo.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (bodyArgument.isAsyncOrReactive()) {
                outgoingResponse = processPublisherBody(request, outgoingResponse, routeInfo);
            }
        } else {
            outgoingResponse = forStatus(routeInfo, defaultHttpStatus)
                    .body(body);
        }
        return outgoingResponse;
    }

    private Flux<MutableHttpResponse<?>> buildRouteResponsePublisher(AtomicReference<HttpRequest<?>> requestReference,
                                                                     Flux<RouteMatch<?>> routeMatchPublisher) {
        // build the result emitter. This result emitter emits the response from a controller action
        return routeMatchPublisher
                .flatMap((route) -> {
                    final ExecutorService executor = findExecutor(route);
                    Flux<MutableHttpResponse<?>> reactiveSequence = executeRoute(requestReference, route);
                    if (executor != null) {
                        reactiveSequence = applyExecutorToPublisher(reactiveSequence, executor);
                    }
                    return reactiveSequence;
                });
    }

    private Flux<MutableHttpResponse<?>> buildResultEmitter(
            AtomicReference<HttpRequest<?>> requestReference,
            boolean executeFilters,
            Flux<RouteMatch<?>> routeMatchPublisher) {

        Publisher<MutableHttpResponse<?>> executeRoutePublisher = buildRouteResponsePublisher(requestReference, routeMatchPublisher)
                .flatMap((response) -> handleStatusException(requestReference.get(), response))
                .onErrorResume((t) -> onError(t, requestReference.get()));

        if (executeFilters) {
            executeRoutePublisher = filterPublisher(requestReference, executeRoutePublisher);
        }

        return Flux.from(executeRoutePublisher);
    }

    private Flux<MutableHttpResponse<?>> executeRoute(AtomicReference<HttpRequest<?>> requestReference,
                                                      RouteMatch<?> routeMatch) {

        return Flux.defer(() -> {
            try {
                final RouteMatch<?> finalRoute;

                // ensure the route requirements are completely satisfied
                final HttpRequest<?> httpRequest = requestReference.get();
                if (!routeMatch.isExecutable()) {
                    finalRoute = requestArgumentSatisfier
                            .fulfillArgumentRequirements(routeMatch, httpRequest, true);
                } else {
                    finalRoute = routeMatch;
                }

                Object body = ServerRequestContext.with(httpRequest, (Supplier<Object>) finalRoute::execute);
                if (body instanceof Optional) {
                    body = ((Optional<?>) body).orElse(null);
                }

                return createResponseForBody(httpRequest, body, finalRoute);
            } catch (Throwable e) {
                return Flux.error(e);
            }
        });
    }

    private Flux<MutableHttpResponse<?>> createResponseForBody(HttpRequest<?> request,
                                                               Object body,
                                                               RouteInfo<?> routeInfo) {
        return Flux.defer(() -> {
            MutableHttpResponse<?> outgoingResponse;
            if (body == null) {
                if (routeInfo.isVoid()) {
                    outgoingResponse = forStatus(routeInfo);
                    if (HttpMethod.permitsRequestBody(request.getMethod())) {
                        outgoingResponse.header(HttpHeaders.CONTENT_LENGTH, "0");
                    }
                } else {
                    outgoingResponse = newNotFoundError(request);
                }
            } else {
                HttpStatus defaultHttpStatus = routeInfo.isErrorRoute() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
                boolean isReactive = routeInfo.isAsyncOrReactive() || Publishers.isConvertibleToPublisher(body);
                if (isReactive) {
                    Class<?> bodyClass = body.getClass();
                    boolean isSingle = isSingle(routeInfo, bodyClass);
                    boolean isCompletable = !isSingle && routeInfo.isVoid() && Publishers.isCompletable(bodyClass);
                    if (isSingle || isCompletable) {
                        // full response case
                        Publisher<Object> publisher = Publishers.convertPublisher(body, Publisher.class);
                        Supplier<MutableHttpResponse<?>> emptyResponse = () -> {
                            MutableHttpResponse<?> singleResponse;
                            if (isCompletable || routeInfo.isVoid()) {
                                singleResponse = forStatus(routeInfo, HttpStatus.OK)
                                        .header(HttpHeaders.CONTENT_LENGTH, "0");
                            } else {
                                singleResponse = newNotFoundError(request);
                            }
                            return singleResponse;
                        };
                        return Flux.from(publisher)
                                .map(o -> {
                                    MutableHttpResponse<?> singleResponse;
                                    if (o instanceof Optional) {
                                        Optional optional = (Optional) o;
                                        if (optional.isPresent()) {
                                            o = ((Optional<?>) o).get();
                                        } else {
                                            return emptyResponse.get();
                                        }
                                    }
                                    if (o instanceof HttpResponse) {
                                        singleResponse = toMutableResponse((HttpResponse<?>) o);
                                        final Argument<?> bodyArgument = routeInfo.getReturnType() //Mono
                                                .getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT) //HttpResponse
                                                .getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT); //Mono
                                        if (bodyArgument.isAsyncOrReactive()) {
                                            singleResponse = processPublisherBody(request, singleResponse, routeInfo);
                                        }
                                    } else if (o instanceof HttpStatus) {
                                        singleResponse = forStatus(routeInfo, (HttpStatus) o);
                                    } else {
                                        singleResponse = forStatus(routeInfo, defaultHttpStatus)
                                                .body(o);
                                    }
                                    return singleResponse;
                                })
                                .switchIfEmpty(Mono.fromSupplier(emptyResponse));
                    } else {
                        // streaming case
                        Argument<?> typeArgument = routeInfo.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                        if (HttpResponse.class.isAssignableFrom(typeArgument.getType())) {
                            // a response stream
                            Publisher<HttpResponse<?>> bodyPublisher = Publishers.convertPublisher(body, Publisher.class);
                            Flux<MutableHttpResponse<?>> response = Flux.from(bodyPublisher)
                                    .map(this::toMutableResponse);
                            Argument<?> bodyArgument = typeArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                            if (bodyArgument.isAsyncOrReactive()) {
                                return response.map((resp) ->
                                        processPublisherBody(request, resp, routeInfo));
                            }
                            return response;
                        } else {
                            MutableHttpResponse<?> response = forStatus(routeInfo, defaultHttpStatus).body(body);
                            return Flux.just(processPublisherBody(request, response, routeInfo));
                        }
                    }
                }
                // now we have the raw result, transform it as necessary
                if (body instanceof HttpStatus) {
                    outgoingResponse = HttpResponse.status((HttpStatus) body);
                } else {
                    if (routeInfo.isSuspended()) {
                        boolean isKotlinFunctionReturnTypeUnit =
                                routeInfo instanceof MethodBasedRouteMatch &&
                                        isKotlinFunctionReturnTypeUnit(((MethodBasedRouteMatch) routeInfo).getExecutableMethod());
                        final Supplier<CompletableFuture<?>> supplier = ContinuationArgumentBinder.extractContinuationCompletableFutureSupplier(request);
                        if (isKotlinCoroutineSuspended(body)) {
                            return Mono.fromCompletionStage(supplier)
                                    .<MutableHttpResponse<?>>map(obj -> {
                                        MutableHttpResponse<?> response;
                                        if (obj instanceof HttpResponse) {
                                            response = toMutableResponse((HttpResponse<?>) obj);
                                            final Argument<?> bodyArgument = routeInfo.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                                            if (bodyArgument.isAsyncOrReactive()) {
                                                response = processPublisherBody(request, response, routeInfo);
                                            }
                                        } else {
                                            response = forStatus(routeInfo, defaultHttpStatus);
                                            if (!isKotlinFunctionReturnTypeUnit) {
                                                response = response.body(obj);
                                            }
                                        }
                                        return response;
                                    })
                                    .switchIfEmpty(createNotFoundErrorResponsePublisher(request));
                        } else {
                            Object suspendedBody;
                            if (isKotlinFunctionReturnTypeUnit) {
                                suspendedBody = Mono.empty();
                            } else {
                                suspendedBody = body;
                            }
                            outgoingResponse = toMutableResponse(request, routeInfo, defaultHttpStatus, suspendedBody);
                        }
                    } else {
                        outgoingResponse = toMutableResponse(request, routeInfo, defaultHttpStatus, body);
                    }
                }
            }
            // for head request we never emit the body
            if (request != null && request.getMethod().equals(HttpMethod.HEAD)) {
                final Object o = outgoingResponse.getBody().orElse(null);
                if (o instanceof ReferenceCounted) {
                    ((ReferenceCounted) o).release();
                }
                outgoingResponse.body(null);
            }

            return Flux.just(outgoingResponse);
        })
                .doOnNext((response) -> {
                    applyConfiguredHeaders(response.getHeaders());
                    if (routeInfo instanceof RouteMatch) {
                        response.setAttribute(HttpAttributes.ROUTE_MATCH, routeInfo);
                    }
                    response.setAttribute(HttpAttributes.ROUTE_INFO, routeInfo);
                });
    }

    private MutableHttpResponse<?> processPublisherBody(HttpRequest<?> request,
                                                        MutableHttpResponse<?> response,
                                                        RouteInfo<?> routeInfo) {
        MediaType mediaType = response.getContentType().orElseGet(() -> resolveDefaultResponseContentType(request, routeInfo));

        Flux<Object> bodyPublisher = applyExecutorToPublisher(
                Publishers.convertPublisher(response.body(), Publisher.class),
                findExecutor(routeInfo));

        return response
                .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .header(HttpHeaders.CONTENT_TYPE, mediaType)
                .body(bodyPublisher);
    }

    private void applyConfiguredHeaders(MutableHttpHeaders headers) {
        if (serverConfiguration.isDateHeader() && !headers.contains(HttpHeaders.DATE)) {
            headers.date(LocalDateTime.now());
        }
        if (!headers.contains(HttpHeaders.SERVER)) {
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

}
