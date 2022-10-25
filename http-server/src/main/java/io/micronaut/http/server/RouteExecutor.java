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
import io.micronaut.reactive.reactor.execution.ReactiveExecutionFlow;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
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
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.micronaut.core.util.KotlinUtils.isKotlinCoroutineSuspended;
import static io.micronaut.http.HttpAttributes.AVAILABLE_HTTP_METHODS;
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
    /**
     * Also present in netty RoutingInBoundHandler.
     */
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
        "^.*(?:connection (?:reset|closed|abort|broken)|broken pipe).*$", Pattern.CASE_INSENSITIVE);

    private final Router router;
    private final BeanContext beanContext;
    private final RequestArgumentSatisfier requestArgumentSatisfier;
    private final HttpServerConfiguration serverConfiguration;
    private final ErrorResponseProcessor<?> errorResponseProcessor;
    private final ExecutorSelector executorSelector;
    private final Optional<CoroutineHelper> coroutineHelper;

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

    @NonNull
    public ExecutionFlow<MutableHttpResponse<?>> executeRoute(RequestBodyReader requestBodyReader,
                                                              HttpRequest<?> httpRequest,
                                                              boolean multipartEnabled,
                                                              StaticResourceResponseFinder staticResourceResponseFinder) {
        ServerRequestContext.set(httpRequest);

        MediaType contentType = httpRequest.getContentType().orElse(null);
        if (!multipartEnabled &&
            contentType != null &&
            contentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Multipart uploads have been disabled via configuration. Rejected request for URI {}, method {}, and content type {}", httpRequest.getUri(),
                    httpRequest.getMethodName(), contentType);
            }
            return onStatusError(
                requestBodyReader,
                httpRequest,
                HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                "Content Type [" + contentType + "] not allowed");
        }

        UriRouteMatch<Object, Object> routeMatch = findRouteMatch(httpRequest);
        if (routeMatch == null) {
            //Check if there is a file for the route before returning route not found
            FileCustomizableResponseType fileCustomizableResponseType = staticResourceResponseFinder.find(httpRequest);
            if (fileCustomizableResponseType != null) {
                return filterPublisher(new AtomicReference<>(httpRequest), () -> ExecutionFlow.just(HttpResponse.ok(fileCustomizableResponseType)));
            }
            return onRouteMiss(requestBodyReader, httpRequest);
        }

        setRouteAttributes(httpRequest, routeMatch);

        if (LOG.isTraceEnabled()) {
            String requestPath = httpRequest.getUri().getPath();
            if (routeMatch instanceof MethodBasedRouteMatch) {
                LOG.trace("Matched route {} - {} to controller {}", httpRequest.getMethodName(), requestPath, routeMatch.getDeclaringType());
            } else {
                LOG.trace("Matched route {} - {}", httpRequest.getMethodName(), requestPath);
            }
        }
        // all ok proceed to try and execute the route
        if (routeMatch.isWebSocketRoute()) {
            return onStatusError(
                requestBodyReader,
                httpRequest,
                HttpResponse.status(HttpStatus.BAD_REQUEST),
                "Not a WebSocket request");
        }
        return executeRoute(
            new AtomicReference<>(httpRequest),
            true,
            true,
            requestBodyReader.read(routeMatch, httpRequest)
        );
    }

    @Nullable
    private UriRouteMatch<Object, Object> findRouteMatch(HttpRequest<?> httpRequest) {
        UriRouteMatch<Object, Object> routeMatch = null;

        List<UriRouteMatch<Object, Object>> uriRoutes = router.findAllClosest(httpRequest);
        if (uriRoutes.size() > 1) {
            throw new DuplicateRouteException(httpRequest.getUri().getPath(), uriRoutes);
        } else if (uriRoutes.size() == 1) {
            routeMatch = uriRoutes.get(0);
        }

        if (routeMatch == null && httpRequest.getMethod().equals(HttpMethod.OPTIONS)) {
            List<UriRouteMatch<Object, Object>> anyUriRoutes = router.findAny(httpRequest.getUri().toString(), httpRequest).toList();
            if (!anyUriRoutes.isEmpty()) {
                setRouteAttributes(httpRequest, anyUriRoutes.get(0));
                httpRequest.setAttribute(AVAILABLE_HTTP_METHODS, anyUriRoutes.stream().map(UriRouteMatch::getHttpMethod).collect(Collectors.toList()));
            }
        }
        return routeMatch;
    }

    private ExecutionFlow<MutableHttpResponse<?>> onRouteMiss(RequestBodyReader requestBodyReader,
                                                              HttpRequest<?> httpRequest) {
        HttpMethod httpMethod = httpRequest.getMethod();
        String requestMethodName = httpRequest.getMethodName();
        MediaType contentType = httpRequest.getContentType().orElse(null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("No matching route: {} {}", httpMethod, httpRequest.getUri());
        }

        // if there is no route present try to locate a route that matches a different HTTP method
        final List<UriRouteMatch<Object, Object>> anyMatchingRoutes = router
            .findAny(httpRequest.getUri().toString(), httpRequest).toList();
        final Collection<MediaType> acceptedTypes = httpRequest.accept();
        final boolean hasAcceptHeader = CollectionUtils.isNotEmpty(acceptedTypes);

        Set<MediaType> acceptableContentTypes = contentType != null ? new HashSet<>(5) : null;
        Set<String> allowedMethods = new HashSet<>(5);
        Set<MediaType> produceableContentTypes = hasAcceptHeader ? new HashSet<>(5) : null;
        for (UriRouteMatch<?, ?> anyRoute : anyMatchingRoutes) {
            final String routeMethod = anyRoute.getRoute().getHttpMethodName();
            if (!requestMethodName.equals(routeMethod)) {
                allowedMethods.add(routeMethod);
            }
            if (contentType != null && !anyRoute.doesConsume(contentType)) {
                acceptableContentTypes.addAll(anyRoute.getRoute().getConsumes());
            }
            if (hasAcceptHeader && !anyRoute.doesProduce(acceptedTypes)) {
                produceableContentTypes.addAll(anyRoute.getRoute().getProduces());
            }
        }

        if (CollectionUtils.isNotEmpty(acceptableContentTypes)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Content type not allowed for URI {}, method {}, and content type {}", httpRequest.getUri(),
                    requestMethodName, contentType);
            }
            return onStatusError(
                requestBodyReader,
                httpRequest,
                HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                "Content Type [" + contentType + "] not allowed. Allowed types: " + acceptableContentTypes);
        }
        if (CollectionUtils.isNotEmpty(produceableContentTypes)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Content type not allowed for URI {}, method {}, and content type {}", httpRequest.getUri(),
                    requestMethodName, contentType);
            }
            return onStatusError(
                requestBodyReader,
                httpRequest,
                HttpResponse.status(HttpStatus.NOT_ACCEPTABLE),
                "Specified Accept Types " + acceptedTypes + " not supported. Supported types: " + produceableContentTypes);
        }
        if (!allowedMethods.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Method not allowed for URI {} and method {}", httpRequest.getUri(), requestMethodName);
            }
            return onStatusError(
                requestBodyReader,
                httpRequest,
                HttpResponse.notAllowedGeneric(allowedMethods),
                "Method [" + requestMethodName + "] not allowed for URI [" + httpRequest.getUri() + "]. Allowed methods: " + allowedMethods);
        }
        return onStatusError(requestBodyReader,
            httpRequest,
            HttpResponse.status(HttpStatus.NOT_FOUND),
            "Page Not Found");
    }

    public ExecutionFlow<MutableHttpResponse<?>> onStatusError(RequestBodyReader requestBodyReader,
                                                               HttpRequest<?> httpRequest,
                                                               MutableHttpResponse<?> defaultResponse,
                                                               String message) {
        Optional<RouteMatch<Object>> statusRoute = router.findStatusRoute(defaultResponse.status(), httpRequest);
        if (statusRoute.isPresent()) {
            return executeRoute(
                new AtomicReference<>(httpRequest),
                true,
                true,
                requestBodyReader.read(statusRoute.get(), httpRequest)
            );
        }
        if (httpRequest.getMethod() != HttpMethod.HEAD) {
            defaultResponse = errorResponseProcessor.processResponse(ErrorContext.builder(httpRequest)
                .errorMessage(message)
                .build(), defaultResponse);
            if (defaultResponse.getContentType().isEmpty()) {
                defaultResponse = defaultResponse.contentType(MediaType.APPLICATION_JSON_TYPE);
            }
        }
        MutableHttpResponse<?> finalDefaultResponse = defaultResponse;
        return filterPublisher(new AtomicReference<>(httpRequest), () -> ExecutionFlow.just(finalDefaultResponse));
    }

    private void setRouteAttributes(HttpRequest<?> request, UriRouteMatch<Object, Object> route) {
        request.setAttribute(HttpAttributes.ROUTE, route.getRoute());
        request.setAttribute(HttpAttributes.ROUTE_MATCH, route);
        request.setAttribute(HttpAttributes.ROUTE_INFO, route);
        request.setAttribute(HttpAttributes.URI_TEMPLATE, route.getRoute().getUriMatchTemplate().toString());
    }

    /**
     * Creates a response publisher to represent the response after being handled
     * by any available error route or exception handler.
     *
     * @param t           The exception that occurred
     * @param httpRequest The request that caused the exception
     * @return A response publisher
     */
    public ExecutionFlow<MutableHttpResponse<?>> onError(Throwable t, HttpRequest<?> httpRequest) {
        // find the origination of the route
        Optional<RouteInfo> previousRequestRouteInfo = httpRequest.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class);
        Class declaringType = previousRequestRouteInfo.map(RouteInfo::getDeclaringType).orElse(null);

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
                return executeRoute(requestReference, false, false, ExecutionFlow.just(errorRoute))
                    .<MutableHttpResponse<?>>map(response -> {
                        response.setAttribute(HttpAttributes.EXCEPTION, cause);
                        return response;
                    })
                    .onErrorResume(throwable -> createDefaultErrorResponseFlow(requestReference.get(), throwable));
            } catch (Throwable e) {
                return createDefaultErrorResponseFlow(httpRequest, e);
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
                    routeInfo = new RouteInfo<>() {
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
                Supplier<ExecutionFlow<MutableHttpResponse<?>>> responseSupplier = () -> {
                    ExceptionHandler<Throwable, ?> handler = beanContext.getBean(handlerDefinition);
                    try {
                        if (serverConfiguration.isLogHandledExceptions()) {
                            logException(cause);
                        }
                        Object result = handler.handle(httpRequest, cause);
                        return createResponseForBody(httpRequest, result, routeInfo);
                    } catch (Throwable e) {
                        return createDefaultErrorResponseFlow(httpRequest, e);
                    }
                };
                ExecutionFlow<MutableHttpResponse<?>> responseFlow;
                final ExecutorService executor = findExecutor(routeInfo);
                if (executor != null) {
                    responseFlow = ExecutionFlow.async(executor, responseSupplier);
                } else {
                    responseFlow = responseSupplier.get();
                }
                return responseFlow
                    .<MutableHttpResponse<?>>map(response -> {
                        response.setAttribute(HttpAttributes.EXCEPTION, cause);
                        return response;
                    })
                    .onErrorResume(throwable -> createDefaultErrorResponseFlow(httpRequest, throwable));
            }
            if (isIgnorable(cause)) {
                logIgnoredException(cause);
                return ExecutionFlow.empty();
            }
            return createDefaultErrorResponseFlow(httpRequest, cause);
        }
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
        if (!mutableHttpResponse.getContentType().isPresent() && httpRequest.getMethod() != HttpMethod.HEAD) {
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

    /**
     * Applies server filters to a request/response.
     *
     * @param requestReference     The request reference
     * @param responseFlowSupplier The deferred response flow
     * @return A new response publisher that executes server filters
     */
    public ExecutionFlow<MutableHttpResponse<?>> filterPublisher(AtomicReference<HttpRequest<?>> requestReference,
                                                                 Supplier<ExecutionFlow<MutableHttpResponse<?>>> responseFlowSupplier) {
        ServerRequestContext.set(requestReference.get());
        List<HttpFilter> httpFilters = router.findFilters(requestReference.get());
        if (httpFilters.isEmpty()) {
            return responseFlowSupplier.get();
        }
        List<HttpFilter> filters = new ArrayList<>(httpFilters);
        AtomicInteger integer = new AtomicInteger();
        int len = filters.size();

        ServerFilterChain filterChain = new ServerFilterChain() {
            @Override
            public Publisher<MutableHttpResponse<?>> proceed(io.micronaut.http.HttpRequest<?> request) {
                int pos = integer.incrementAndGet();
                if (pos > len) {
                    throw new IllegalStateException("The FilterChain.proceed(..) method should be invoked exactly once per filter execution. The method has instead been invoked multiple times by an erroneous filter definition.");
                }
                if (pos == len) {
                    return ReactiveExecutionFlow.fromFlow(responseFlowSupplier.get()).toPublisher();
                }
                HttpFilter httpFilter = filters.get(pos);
                requestReference.set(request);
                return ReactiveExecutionFlow.fromFlow(
                    triggerFilter(requestReference, httpFilter, this)
                ).toPublisher();
            }
        };
        return triggerFilter(requestReference, filters.get(0), filterChain);
    }

    private ExecutionFlow<MutableHttpResponse<?>> triggerFilter(AtomicReference<HttpRequest<?>> requestReference, HttpFilter httpFilter, ServerFilterChain filterChain) {
        try {
            return fromPublisher((Publisher<MutableHttpResponse<?>>) httpFilter.doFilter(requestReference.get(), filterChain))
                .flatMap(response -> handleStatusException(requestReference.get(), response))
                .onErrorResume(throwable -> onError(throwable, requestReference.get()));
        } catch (Throwable t) {
            return onError(t, requestReference.get());
        }
    }

    private ExecutionFlow<MutableHttpResponse<?>> createDefaultErrorResponseFlow(HttpRequest<?> httpRequest, Throwable cause) {
        return ExecutionFlow.just(createDefaultErrorResponse(httpRequest, cause));
    }

    private MutableHttpResponse<?> newNotFoundError(HttpRequest<?> request) {
        MutableHttpResponse<?> response = errorResponseProcessor.processResponse(
            ErrorContext.builder(request)
                .errorMessage("Page Not Found")
                .build(), HttpResponse.notFound());
        if (response.getContentType().isEmpty() && request.getMethod() != HttpMethod.HEAD) {
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

    private ExecutionFlow<MutableHttpResponse<?>> handleStatusException(HttpRequest<?> request,
                                                                        MutableHttpResponse<?> response) {
        RouteInfo<?> routeInfo = response.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class).orElse(null);
        if (response.code() >= 400 && routeInfo != null && !routeInfo.isErrorRoute()) {
            RouteMatch<Object> statusRoute = findStatusRoute(request, response.status(), routeInfo);
            if (statusRoute != null) {
                return executeRoute(
                    new AtomicReference<>(request),
                    false,
                    true,
                    ExecutionFlow.just(statusRoute)
                );
            }
        }
        return ExecutionFlow.just(response);
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

    private <T> Flux<T> applyExecutorToPublisher(Publisher<T> publisher, @Nullable ExecutorService executor) {
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
            mutableHttpResponse = HttpResponse.status(message.code(), message.reason());
            mutableHttpResponse.body(message.body());
            message.getHeaders().forEach((name, value) -> {
                for (String val : value) {
                    mutableHttpResponse.header(name, val);
                }
            });
            mutableHttpResponse.getAttributes().putAll(message.getAttributes());
        }
        return mutableHttpResponse;
    }

    private ExecutionFlow<MutableHttpResponse<?>> fromImperativeExecute(HttpRequest<?> request, RouteInfo<?> routeInfo, HttpStatus defaultHttpStatus, Object body) {
        if (body instanceof HttpResponse) {
            MutableHttpResponse<?> outgoingResponse = toMutableResponse((HttpResponse<?>) body);
            final Argument<?> bodyArgument = routeInfo.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (bodyArgument.isAsyncOrReactive()) {
                return fromPublisher(
                    processPublisherBody(request, outgoingResponse, routeInfo)
                );
            }
            return ExecutionFlow.just(outgoingResponse);
        }
        return ExecutionFlow.just(forStatus(routeInfo, defaultHttpStatus).body(body));
    }

    private ExecutionFlow<MutableHttpResponse<?>> executeRoute(AtomicReference<HttpRequest<?>> requestReference,
                                                               boolean executeFilters,
                                                               boolean useErrorRoute,
                                                               ExecutionFlow<RouteMatch<?>> routeMatchFlow) {
        Supplier<ExecutionFlow<MutableHttpResponse<?>>> responseFlowSupplier = () -> {
            return routeMatchFlow.flatMap(routeMatch -> {
                    ExecutorService executorService = findExecutor(routeMatch);
                    Supplier<ExecutionFlow<MutableHttpResponse<?>>> flowSupplier = () -> executeRouteAndConvertBody(routeMatch, requestReference.get());
                    ExecutionFlow<MutableHttpResponse<?>> executeMethodResponseFlow;
                    if (executorService != null) {
                        if (routeMatch.isSuspended()) {
                            executeMethodResponseFlow = ReactiveExecutionFlow.fromPublisher(Mono.deferContextual(contextView -> {
                                    coroutineHelper.ifPresent(helper -> helper.setupCoroutineContext(requestReference.get(), contextView));
                                    return Mono.from(
                                        ReactiveExecutionFlow.fromFlow(flowSupplier.get()).toPublisher()
                                    );
                                }))
                                .putInContext(ServerRequestContext.KEY, requestReference.get());
                        } else if (routeMatch.isReactive()) {
                            executeMethodResponseFlow = ReactiveExecutionFlow.async(executorService, flowSupplier)
                                .putInContext(ServerRequestContext.KEY, requestReference.get());
                        } else {
                            executeMethodResponseFlow = ExecutionFlow.async(executorService, flowSupplier);
                        }
                    } else {
                        if (routeMatch.isSuspended()) {
                            executeMethodResponseFlow = ReactiveExecutionFlow.fromPublisher(Mono.deferContextual(contextView -> {
                                    coroutineHelper.ifPresent(helper -> helper.setupCoroutineContext(requestReference.get(), contextView));
                                    return Mono.from(
                                        ReactiveExecutionFlow.fromFlow(flowSupplier.get()).toPublisher()
                                    );
                                }))
                                .putInContext(ServerRequestContext.KEY, requestReference.get());
                        } else if (routeMatch.isReactive()) {
                            executeMethodResponseFlow = ReactiveExecutionFlow.fromFlow(flowSupplier.get())
                                .putInContext(ServerRequestContext.KEY, requestReference.get());
                        } else {
                            executeMethodResponseFlow = flowSupplier.get();
                        }
                    }
                    return executeMethodResponseFlow;
                }).flatMap(response -> handleStatusException(requestReference.get(), response))
                .onErrorResume(throwable -> {
                    if (useErrorRoute) {
                        return onError(throwable, requestReference.get());
                    }
                    return createDefaultErrorResponseFlow(requestReference.get(), throwable);
                });
        };
        if (!executeFilters) {
            return responseFlowSupplier.get();
        }
        return filterPublisher(requestReference, responseFlowSupplier);
    }

    private ExecutionFlow<MutableHttpResponse<?>> executeRouteAndConvertBody(RouteMatch<?> routeMatch, HttpRequest<?> httpRequest) {
        try {
            final RouteMatch<?> finalRoute;
            // ensure the route requirements are completely satisfied
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
            return ExecutionFlow.error(e);
        }
    }

    private ExecutionFlow<MutableHttpResponse<?>> createResponseForBody(HttpRequest<?> request,
                                                                        Object body,
                                                                        RouteInfo<?> routeInfo) {
        ExecutionFlow<MutableHttpResponse<?>> outgoingResponse;
        if (body == null) {
            if (routeInfo.isVoid()) {
                MutableHttpResponse<Object> data = forStatus(routeInfo);
                if (HttpMethod.permitsRequestBody(request.getMethod())) {
                    data.header(HttpHeaders.CONTENT_LENGTH, "0");
                }
                outgoingResponse = ExecutionFlow.just(data);
            } else {
                outgoingResponse = ExecutionFlow.just(newNotFoundError(request));
            }
        } else {
            HttpStatus defaultHttpStatus = routeInfo.isErrorRoute() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
            boolean isReactive = routeInfo.isAsyncOrReactive() || Publishers.isConvertibleToPublisher(body);
            if (isReactive) {
                outgoingResponse = ReactiveExecutionFlow.fromPublisher(
                    fromReactiveExecute(request, body, routeInfo, defaultHttpStatus)
                );
            } else if (body instanceof HttpStatus) { // now we have the raw result, transform it as necessary
                outgoingResponse = ExecutionFlow.just(HttpResponse.status((HttpStatus) body));
            } else {
                if (routeInfo.isSuspended()) {
                    outgoingResponse = fromKotlinCoroutineExecute(request, body, routeInfo, defaultHttpStatus);
                } else {
                    outgoingResponse = fromImperativeExecute(request, routeInfo, defaultHttpStatus, body);
                }
            }
        }
        outgoingResponse = outgoingResponse.map(response -> {
            // for head request we never emit the body
            if (request != null && request.getMethod().equals(HttpMethod.HEAD)) {
                final Object o = response.getBody().orElse(null);
                if (o instanceof ReferenceCounted) {
                    ((ReferenceCounted) o).release();
                }
                response.body(null);
            }
            applyConfiguredHeaders(response.getHeaders());
            if (routeInfo instanceof RouteMatch) {
                response.setAttribute(HttpAttributes.ROUTE_MATCH, routeInfo);
            }
            response.setAttribute(HttpAttributes.ROUTE_INFO, routeInfo);
            return response;
        });
        return outgoingResponse;
    }

    private ExecutionFlow<MutableHttpResponse<?>> fromKotlinCoroutineExecute(HttpRequest<?> request, Object body, RouteInfo<?> routeInfo, HttpStatus defaultHttpStatus) {
        ExecutionFlow<MutableHttpResponse<?>> outgoingResponse;
        boolean isKotlinFunctionReturnTypeUnit =
            routeInfo instanceof MethodBasedRouteMatch &&
                isKotlinFunctionReturnTypeUnit(((MethodBasedRouteMatch) routeInfo).getExecutableMethod());
        final Supplier<CompletableFuture<?>> supplier = ContinuationArgumentBinder.extractContinuationCompletableFutureSupplier(request);
        if (isKotlinCoroutineSuspended(body)) {
            return ReactiveExecutionFlow.fromPublisher(
                Mono.fromCompletionStage(supplier)
                    .flatMap(obj -> {
                        MutableHttpResponse<?> response;
                        if (obj instanceof HttpResponse) {
                            response = toMutableResponse((HttpResponse<?>) obj);
                            final Argument<?> bodyArgument = routeInfo.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                            if (bodyArgument.isAsyncOrReactive()) {
                                return processPublisherBody(request, response, routeInfo);
                            }
                        } else {
                            response = forStatus(routeInfo, defaultHttpStatus);
                            if (!isKotlinFunctionReturnTypeUnit) {
                                response = response.body(obj);
                            }
                        }
                        return Mono.just(response);
                    })
                    .switchIfEmpty(createNotFoundErrorResponsePublisher(request))
            );
        }
        Object suspendedBody;
        if (isKotlinFunctionReturnTypeUnit) {
            suspendedBody = Mono.empty();
        } else {
            suspendedBody = body;
        }
        outgoingResponse = fromImperativeExecute(request, routeInfo, defaultHttpStatus, suspendedBody);
        return outgoingResponse;
    }

    private CorePublisher<MutableHttpResponse<?>> fromReactiveExecute(HttpRequest<?> request, Object body, RouteInfo<?> routeInfo, HttpStatus defaultHttpStatus) {
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
                .flatMap(o -> {
                    MutableHttpResponse<?> singleResponse;
                    if (o instanceof Optional<?> optional) {
                        if (optional.isPresent()) {
                            o = optional.get();
                        } else {
                            return Flux.just(emptyResponse.get());
                        }
                    }
                    if (o instanceof HttpResponse) {
                        singleResponse = toMutableResponse((HttpResponse<?>) o);
                        final Argument<?> bodyArgument = routeInfo.getReturnType() //Mono
                            .getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT) //HttpResponse
                            .getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT); //Mono
                        if (bodyArgument.isAsyncOrReactive()) {
                            return processPublisherBody(request, singleResponse, routeInfo);
                        }
                    } else if (o instanceof HttpStatus) {
                        singleResponse = forStatus(routeInfo, (HttpStatus) o);
                    } else {
                        singleResponse = forStatus(routeInfo, defaultHttpStatus)
                            .body(o);
                    }
                    return Flux.just(singleResponse);
                })
                .switchIfEmpty(Mono.fromSupplier(emptyResponse));
        }
        // streaming case
        Argument<?> typeArgument = routeInfo.getReturnType().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        if (HttpResponse.class.isAssignableFrom(typeArgument.getType())) {
            // a response stream
            Publisher<HttpResponse<?>> bodyPublisher = Publishers.convertPublisher(body, Publisher.class);
            Flux<MutableHttpResponse<?>> response = Flux.from(bodyPublisher)
                .map(this::toMutableResponse);
            Argument<?> bodyArgument = typeArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (bodyArgument.isAsyncOrReactive()) {
                return response.flatMap((resp) ->
                    processPublisherBody(request, resp, routeInfo));
            }
            return response;
        }
        MutableHttpResponse<?> response = forStatus(routeInfo, defaultHttpStatus).body(body);
        return processPublisherBody(request, response, routeInfo);
    }

    private Mono<MutableHttpResponse<?>> processPublisherBody(HttpRequest<?> request,
                                                              MutableHttpResponse<?> response,
                                                              RouteInfo<?> routeInfo) {
        Object body = response.body();
        if (body == null) {
            return Mono.just(response);
        }
        if (Publishers.isSingle(body.getClass())) {
            return Mono.from(Publishers.convertPublisher(body, Publisher.class)).map(b -> {
                response.body(b);
                return response;
            });
        }
        MediaType mediaType = response.getContentType().orElseGet(() -> resolveDefaultResponseContentType(request, routeInfo));

        Flux<Object> bodyPublisher = applyExecutorToPublisher(Publishers.convertPublisher(body, Publisher.class), findExecutor(routeInfo));

        return Mono.just(response
            .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
            .header(HttpHeaders.CONTENT_TYPE, mediaType)
            .body(bodyPublisher));
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

    private <K> ExecutionFlow<K> fromPublisher(Publisher<K> publisher) {
        return ReactiveExecutionFlow.fromPublisher(publisher);
    }


    /**
     * The request body reader.
     */
    public interface RequestBodyReader {

        /**
         * Reads the HTTP request body.
         * TODO: This needs to be refactored for Micronaut 4 to eliminate the need for the route match.
         *
         * @param routeMatch
         * @param httpRequest
         * @return
         */
        @NonNull
        ExecutionFlow<RouteMatch<?>> read(@NonNull RouteMatch<?> routeMatch, @NonNull HttpRequest<?> httpRequest);

    }

    /**
     * The static resource finder.
     */
    public interface StaticResourceResponseFinder {

        /**
         * Finds a file response based on the request.
         *
         * @param httpRequest The request
         * @return The file response or null if not found.
         */
        @Nullable
        FileCustomizableResponseType find(@NonNull HttpRequest<?> httpRequest);

    }

}
