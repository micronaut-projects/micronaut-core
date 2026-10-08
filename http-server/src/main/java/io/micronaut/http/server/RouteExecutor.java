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
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.ReleasingBodyElements;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.ReactiveFilterChainElement;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.binding.ServerRequestBody;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.multipart.FormRouteCompleter;
import io.micronaut.http.server.stream.ResponseStreams;
import io.micronaut.http.server.util.HttpDateHeader;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.MethodReference;
import io.micronaut.context.propagation.instrument.execution.ContextPropagatingExecutorService;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.DefaultRouteInfo;
import io.micronaut.web.router.GroupErrorRoutes;
import io.micronaut.web.router.MethodBasedRouteInfo;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.RouteLocator;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
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
    /**
     * The value of an empty single-valued publisher, the flows do not carry {@code null}.
     */
    private static final Object EMPTY = new Object();

    final Router router;
    final BeanContext beanContext;
    final RequestArgumentSatisfier requestArgumentSatisfier;
    final HttpServerConfiguration serverConfiguration;
    final ErrorResponseProcessor<?> errorResponseProcessor;
    private final ExecutorSelector executorSelector;
    private final Optional<CoroutineHelper> coroutineHelper;
    /**
     * Whether the Reactor context of the subscriber has to reach suspended routes as a {@code ReactorContext}.
     */
    private final boolean suspendedRoutesNeedReactorContext;
    private final ConversionService conversionService;
    /**
     * The executor that may block, which closes the elements of a response body that would be
     * closed on an event loop.
     */
    private final Supplier<@Nullable ExecutorService> blockingExecutor;

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
        this.suspendedRoutesNeedReactorContext = coroutineHelper.isPresent() && coroutineHelper.get().isReactorContextPropagated();
        this.conversionService = beanContext.getConversionService();
        this.blockingExecutor = SupplierUtil.memoized(() -> executorSelector.select(TaskExecutors.BLOCKING).orElse(null));
    }

    /**
     * @return The router
     */
    public Router getRouter() {
        return router;
    }

    /**
     * @return The request argument satisfier
     */
    @Internal
    public RequestArgumentSatisfier getRequestArgumentSatisfier() {
        return requestArgumentSatisfier;
    }

    /**
     * @return The error response processor
     */
    public ErrorResponseProcessor<?> getErrorResponseProcessor() {
        return errorResponseProcessor;
    }

    /**
     * @return The executor selector
     */
    public ExecutorSelector getExecutorSelector() {
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
        // a located route has the template under the prefixes of its locator routes
        BasicHttpAttributes.setUriTemplate(request, RouteLocator.uriTemplate(route));
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
                    .exceptionMessage(cause.getMessage())
                    .errorMessage(shouldIncludeErrorResponseMessage(httpRequest)
                        ? "Internal Server Error: " + cause.getMessage()
                        : "Internal Server Error")
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

    private boolean shouldIncludeErrorResponseMessage(HttpRequest<?> httpRequest) {
        return switch (serverConfiguration.getErrorResponseIncludeMessage()) {
            case NEVER -> false;
            case ALWAYS -> true;
            case ON_PARAM -> {
                HttpParameters parameters = httpRequest.getParameters();
                yield parameters.names().contains("message") && !StringUtils.FALSE.equalsIgnoreCase(parameters.get("message"));
            }
        };
    }

    /**
     * @param request    The request
     * @param finalRoute The route
     * @return The default content type declared on the route
     */
    public MediaType resolveDefaultResponseContentType(@Nullable HttpRequest<?> request, RouteInfo<?> finalRoute) {
        final List<MediaType> producesList = finalRoute.getProduces();
        if (request != null) {
            final Iterator<MediaType> i = request.accept().iterator();
            if (i.hasNext()) {
                final MediaType mt = i.next();
                if (producesList.contains(mt)) {
                    return MediaType.ALL_TYPE.equals(mt) ? MediaType.APPLICATION_JSON_TYPE : mt;
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
        return MediaType.ALL_TYPE.equals(defaultResponseMediaType) ? MediaType.APPLICATION_JSON_TYPE : defaultResponseMediaType;
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
        if (cause instanceof ClosedChannelException || cause instanceof BaseSharedBuffer.IncorrectContentLengthException
            || RouteLocator.isAbandonment(cause)) {
            // the client went away, e.g. before a route locator located its target
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

    @Nullable
    RouteMatch<?> findErrorRoute(Throwable cause,
                                 @Nullable Class<?> declaringType,
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
        RouteInfo<?> failedRoute = null;
        if (errorRoute == null) {
            // handle error with an error route of the groups of the handler route, innermost first
            failedRoute = RouteAttributes.getRouteInfo(httpRequest).orElse(null);
            errorRoute = GroupErrorRoutes.findErrorRoute(httpRequest, failedRoute, cause);
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
                    // handle error with a status route of the groups of the handler route
                    errorRoute = GroupErrorRoutes.findStatusRoute(httpRequest, failedRoute, errorStatus.getCode(), cause);
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

    @Nullable
    RouteMatch<Object> findStatusRoute(HttpRequest<?> incomingRequest, int status, RouteInfo<?> finalRoute) {
        Class<?> declaringType = finalRoute.getDeclaringType();
        // handle re-mapping of errors
        RouteMatch<Object> statusRoute = null;
        // if declaringType is not null, this means it's a locally marked method handler
        if (declaringType != null) {
            statusRoute = router.<Object>findStatusRoute(declaringType, status, incomingRequest).orElse(null);
            if (statusRoute == null) {
                // a status route of the groups of the handler route, innermost first
                statusRoute = GroupErrorRoutes.findStatusRoute(incomingRequest, finalRoute, status);
            }
            if (statusRoute == null) {
                statusRoute = router.<Object>findStatusRoute(status, incomingRequest).orElse(null);
            }
        }
        return statusRoute;
    }

    @Nullable
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
        // the context is bound around each task: this avoids wrapping the executor service and
        // initializing a delegating scheduler for every request
        ExecutorService target = ContextPropagatingExecutorService.unwrap(executor).orElse(executor);
        final Scheduler scheduler = Schedulers.fromExecutor(command -> target.execute(propagatedContext.wrap(command)));
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
            return processPublisherBody(propagatedContext, request, outgoingResponse, routeInfo);
        }
        return ExecutionFlow.just(outgoingResponse);
    }

    ExecutionFlow<HttpResponse<?>> callRoute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
        RouteInfo<?> routeInfo = routeMatch.getRouteInfo();
        ExecutorService executorService = routeInfo.getExecutor(serverConfiguration.getThreadSelection());
        ExecutionFlow<HttpResponse<?>> executeMethodResponseFlow;
        if (executorService != null) {
            if (routeInfo.isSuspended() && !suspendedRoutesNeedReactorContext) {
                // without kotlinx-coroutines-reactor the Reactor context never reaches the coroutine, so the
                // route is dispatched like a blocking one: the coroutine context keeps the continuation on the executor
                executeMethodResponseFlow = ExecutionFlow.async(executorService, () -> executeRouteAndConvertBody(propagatedContext, routeMatch, request, true, Context.empty(), executorService));
            } else if (routeInfo.isSuspended()) {
                // a suspend function runs synchronously on the caller until its first suspension point, and its
                // coroutine context decides where it resumes, so honouring the executor needs both: applying it to
                // the publisher moves the body off the event loop, and passing it on to the coroutine context keeps
                // the continuation there. publishOn is deliberate - it holds the same "the response is assembled off
                // the event loop" invariant the blocking branch below gets from ExecutionFlow.async
                executeMethodResponseFlow = ReactiveExecutionFlow.fromPublisher(
                    applyExecutorToPublisher(
                        Mono.deferContextual(contextView -> Mono.from(
                            ReactiveExecutionFlow.toPublisher(executeRouteAndConvertBody(propagatedContext, routeMatch, request, true, contextView, executorService))
                        )),
                        executorService,
                        propagatedContext
                    )
                );
            } else if (routeInfo.isReactive() && ReactiveFilterChainElement.isPresent(propagatedContext)) {
                // a filter subscribes to the response publisher and may write to its Reactor
                // context: the route runs on the executor when that filter subscribes
                executeMethodResponseFlow = ReactiveExecutionFlow.async(executorService, () -> executeRouteAndConvertBody(propagatedContext, routeMatch, request, false, null, null));
            } else {
                // a reactive result is subscribed to on the executor thread as well: the route
                // result is converted in the supplier
                executeMethodResponseFlow = ExecutionFlow.async(executorService, () -> executeRouteAndConvertBody(propagatedContext, routeMatch, request, false, null, null));
            }
        } else {
            if (routeInfo.isSuspended() && !suspendedRoutesNeedReactorContext) {
                executeMethodResponseFlow = executeRouteAndConvertBody(propagatedContext, routeMatch, request, true, Context.empty(), null);
            } else if (routeInfo.isSuspended()) {
                // the Reactor context of the subscriber becomes the coroutine's ReactorContext
                executeMethodResponseFlow = ReactiveExecutionFlow.fromPublisher(Mono.deferContextual(contextView -> Mono.from(
                    ReactiveExecutionFlow.toPublisher(executeRouteAndConvertBody(propagatedContext, routeMatch, request, true, contextView, null))
                )));
            } else {
                executeMethodResponseFlow = executeRouteAndConvertBody(propagatedContext, routeMatch, request, false, null, null);
            }
        }
        return executeMethodResponseFlow;
    }

    private ExecutionFlow<HttpResponse<?>> executeRouteAndConvertBody(PropagatedContext propagatedContext,
                                                                      RouteMatch<?> routeMatch,
                                                                      HttpRequest<?> httpRequest,
                                                                      boolean isKotlinCoroutine,
                                                                      @Nullable ContextView contextView,
                                                                      @Nullable ExecutorService executorService) {
        PropagatedContext routePropagatedContext = ServerHttpRequestContext.withRequest(propagatedContext, httpRequest);
        return routePropagatedContext.propagate(() -> {
            try {
                if (isKotlinCoroutine && contextView != null) {
                    coroutineHelper.ifPresent(helper -> helper.setupCoroutineContext(httpRequest, contextView, routePropagatedContext, executorService));
                }
                ExecutionFlow<?> waitsFor = fulfillArgumentsAfterFilters(routeMatch, httpRequest);
                if (waitsFor != null) {
                    // e.g. the form of a request bean, which is still arriving
                    ExecutorService routeExecutor = routeMatch.getRouteInfo().getExecutor(serverConfiguration.getThreadSelection());
                    return releaseRouteBodies(httpRequest, waitsFor.then(() -> {
                        FormRouteCompleter completer = FormFactory.getCompleterOrNull(httpRequest);
                        if (completer != null) {
                            completer.stopDeadlockDetection();
                        }
                        // the wait completed on the thread that read the body: the route runs
                        // on its executor, as it would without the wait
                        Supplier<ExecutionFlow<HttpResponse<?>>> execution = () -> routePropagatedContext.propagate(() -> {
                            try {
                                return executeFulfilledRoute(propagatedContext, routeMatch, httpRequest);
                            } catch (Throwable e) {
                                return ExecutionFlow.error(e);
                            }
                        });
                        return routeExecutor == null ? execution.get() : ExecutionFlow.async(routeExecutor, execution);
                    }));
                }
                return releaseRouteBodies(httpRequest, executeFulfilledRoute(propagatedContext, routeMatch, httpRequest));
            } catch (Throwable e) {
                return releaseRouteBodies(httpRequest, ExecutionFlow.error(e));
            }
        });
    }

    private ExecutionFlow<HttpResponse<?>> executeFulfilledRoute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> httpRequest) {
        Object body = routeMatch.execute();
        if (body instanceof Optional optional) {
            body = optional.orElse(null);
        }
        return createResponseForBody(propagatedContext, httpRequest, body, routeMatch.getRouteInfo(), routeMatch);
    }

    /**
     * Bind the arguments of the route that are bound after the filters, e.g. a request bean, and
     * tell what they wait for: a member of a request bean that is taken from the body, e.g. a
     * form, waits for a body that is still arriving, like an argument of the route does. The
     * route waited for its other arguments before, see {@link RequestLifecycle#fulfillArguments}.
     *
     * @param routeMatch  The route
     * @param httpRequest The request
     * @return What the arguments wait for, or {@code null} if the route can be executed
     */
    private @Nullable ExecutionFlow<?> fulfillArgumentsAfterFilters(RouteMatch<?> routeMatch, HttpRequest<?> httpRequest) {
        if (routeMatch.isFulfilled()) {
            // nothing is bound after the filters
            return null;
        }
        // what the route waited for before: it is done, or, for an error route, not waited for
        BasicHttpAttributes.takeRouteWaitsFor(httpRequest);
        requestArgumentSatisfier.fulfillArgumentRequirementsAfterFilters(routeMatch, httpRequest);
        ExecutionFlow<?> waitsFor = BasicHttpAttributes.takeRouteWaitsFor(httpRequest);
        if (waitsFor == null) {
            return null;
        }
        FormRouteCompleter completer = FormFactory.getCompleterOrNull(httpRequest);
        if (completer != null && !completer.isStarted()) {
            // a field an argument reads by name, e.g. the CompletedFileUpload of a request bean
            completer.start();
        }
        return waitsFor;
    }

    /**
     * Release what the reads of the {@link io.micronaut.http.body.AsyncRequestBody} the route was
     * invoked with left open, e.g. a read the route started and did not wait for, once the route
     * completed: when the value or the stage it returned completed, or it failed, before the
     * response is written. A failure to release fails a successful route, and is added as
     * suppressed to the failure of the route; either is answered by the error handling.
     *
     * <p>The bodies are taken when the route completed: a streamed response took them before,
     * see {@link #releaseWhenStreamEnds}, and releases them when its stream ends.</p>
     *
     * @param request The request the route was invoked with
     * @param flow    The response of the route
     * @return The response, once the bodies were released
     */
    private static ExecutionFlow<HttpResponse<?>> releaseRouteBodies(HttpRequest<?> request, ExecutionFlow<HttpResponse<?>> flow) {
        if (!BasicHttpAttributes.hasRouteBodies(request)) {
            return flow;
        }
        return ReleasableRequestBody.releaseAfter(flow, () -> {
            ReleasableRequestBody bodies = BasicHttpAttributes.takeRouteBodies(request);
            return bodies == null ? CompletableFuture.completedStage(null) : bodies.releaseBody();
        });
    }

    /**
     * Release the bodies the route of the request was invoked with when the stream of its
     * response ends, instead of when the route completed: the stream may be made of the reads
     * of the body, e.g. of its elements. The stream releases them when it completes, before its
     * completion is delivered, when it fails, or when it is cancelled, e.g. the client
     * disconnected. The response is committed by then: a failure to release is logged. A stream
     * that is never subscribed to leaves the bodies to the release when the request ends.
     *
     * @param request The request of the route
     * @param stream  The stream of the response body
     * @return The stream, which releases the bodies when it ends
     */
    private static Publisher<Object> releaseWhenStreamEnds(HttpRequest<?> request, Publisher<Object> stream) {
        ReleasableRequestBody bodies = BasicHttpAttributes.takeRouteBodies(request);
        if (bodies == null) {
            return stream;
        }
        return Flux.defer(() -> {
            AtomicBoolean released = new AtomicBoolean();
            Supplier<Mono<Void>> release = () -> released.compareAndSet(false, true)
                ? Mono.fromCompletionStage(() -> releaseLogged(request, bodies)) : Mono.empty();
            return Flux.from(stream)
                .onErrorResume(error -> release.get().then(Mono.error(error)))
                .concatWith(Mono.defer(release).then(Mono.empty()))
                .doOnCancel(() -> release.get().subscribe());
        });
    }

    /**
     * Release the bodies the route of the request was invoked with when the single-valued
     * publisher of its response ends, like {@link #releaseWhenStreamEnds} does for a stream: the
     * publisher may read the bodies when it is subscribed to, after the route completed. The
     * bodies are released before the value or the completion is delivered, when the publisher
     * fails, or when it is cancelled.
     *
     * @param request The request of the route
     * @param single  The single-valued publisher of the response body
     * @return The publisher, single-valued, which releases the bodies when it ends
     */
    private static Publisher<Object> releaseWhenSingleEnds(HttpRequest<?> request, Publisher<Object> single) {
        ReleasableRequestBody bodies = BasicHttpAttributes.takeRouteBodies(request);
        if (bodies == null) {
            return single;
        }
        return Mono.usingWhen(
            Mono.just(bodies),
            owned -> Mono.from(single),
            owned -> Mono.fromCompletionStage(() -> releaseLogged(request, owned))
        );
    }

    /**
     * Release the bodies of a streamed response, and log a failure: the response is committed.
     *
     * @param request The request of the route
     * @param bodies  The bodies
     * @return Completes when released, normally even when releasing failed
     */
    private static CompletionStage<Void> releaseLogged(HttpRequest<?> request, ReleasableRequestBody bodies) {
        CompletionStage<Void> released;
        try {
            released = bodies.releaseBody();
        } catch (Throwable e) {
            released = CompletableFuture.failedStage(e);
        }
        return released.handle((ignored, error) -> {
            if (error != null && LOG.isWarnEnabled()) {
                LOG.warn("Failed to release what the reads of the body of {} left open when its streamed response ended", request, error);
            }
            return null;
        });
    }

    ExecutionFlow<HttpResponse<?>> createResponseForBody(PropagatedContext propagatedContext,
                                                         HttpRequest<?> request,
                                                         @Nullable
                                                         Object body,
                                                         RouteInfo<?> routeInfo,
                                                         @Nullable
                                                         RouteMatch<?> routeMatch) {
        ExecutionFlow<MutableHttpResponse<?>> outgoingResponse;
        MutableHttpResponse<?> response = null;
        if (body == null) {
            response = emptyResponse(request, routeInfo);
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
                    outgoingResponse = fromReactiveExecute(propagatedContext, request, publisher, routeInfo);
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
            return ExecutionFlow.just(keepRouteBodiesForStream(request, finaliseResponse(request, routeInfo, routeMatch, response)));
        }
        return outgoingResponse.map(res -> keepRouteBodiesForStream(request, finaliseResponse(request, routeInfo, routeMatch, res)));
    }

    /**
     * Keep the bodies the route was invoked with for the publisher of a response the route did not
     * return directly, see {@link #releaseWhenStreamEnds}: the publisher inside the stage, the
     * {@link ExecutionFlow}, or the result of the suspend function the route returned, with or
     * without a response around it. Such a publisher is subscribed to when the response is
     * written, after the route completed, and would find the bodies released: a stream, or a
     * single-valued publisher, e.g. a {@code CompletionStage<Mono<T>>}, which stays single-valued.
     * A publisher the route returned directly, or in the response it returned, took the bodies
     * already, or was resolved before the route completed.
     *
     * @param request  The request of the route
     * @param response The response of the route
     * @return The response, its publisher body releasing the bodies when it ends
     */
    private MutableHttpResponse<?> keepRouteBodiesForStream(HttpRequest<?> request, MutableHttpResponse<?> response) {
        if (!BasicHttpAttributes.hasRouteBodies(request)) {
            return response;
        }
        Object body = response.body();
        if (body instanceof BodyElements<?> elements) {
            return response.body(releaseWhenClosed(request, elements));
        }
        if (body == null || body instanceof HttpResponse<?> || !Publishers.isConvertibleToPublisher(body)) {
            return response;
        }
        Publisher<Object> publisher = Publishers.convertToPublisher(conversionService, body);
        if (Publishers.isSingle(body.getClass())) {
            return response.body(releaseWhenSingleEnds(request, publisher));
        }
        return response.body(releaseWhenStreamEnds(request, publisher));
    }

    /**
     * Release the bodies the route of the request was invoked with when the
     * {@link BodyElements} body of its response is closed, like {@link #releaseWhenStreamEnds}
     * does for a stream: the elements may be made of the reads of the body. The server closes
     * them once: when the response ends, fails, or the client disconnects, or when a filter
     * replaces the response.
     *
     * @param request  The request of the route
     * @param elements The elements of the response body
     * @param <T>      The type of an element
     * @return The elements, which release the bodies when they are closed
     */
    private static <T> BodyElements<T> releaseWhenClosed(HttpRequest<?> request, BodyElements<T> elements) {
        ReleasableRequestBody bodies = BasicHttpAttributes.takeRouteBodies(request);
        if (bodies == null) {
            return elements;
        }
        // the elements keep their own operations, e.g. the elements of the request body
        return ReleasingBodyElements.onClose(elements, () -> releaseLogged(request, bodies));
    }

    /**
     * Close the {@link BodyElements} of a response body that is not written: on the blocking
     * executor when the current thread is an event loop, since closing them may block, e.g. a
     * database cursor.
     *
     * @param request  The request
     * @param elements The elements
     */
    void discardElements(HttpRequest<?> request, BodyElements<?> elements) {
        ServerHttpRequest<?> server = ServerRequestBody.of(request);
        if (server == null) {
            ResponseStreams.discard(elements);
        } else {
            ResponseStreams.discard(elements, server.byteBodyFactory(), blockingExecutor.get());
        }
    }

    private MutableHttpResponse<?> finaliseResponse(@Nullable HttpRequest<?> request, RouteInfo<?> routeInfo, @Nullable RouteMatch<?> routeMatch, MutableHttpResponse<?> response) {
        // for head request we never emit the body
        if (request != null && request.getMethod().equals(HttpMethod.HEAD)) {
            final Object o = response.getBody().orElse(null);
            if (o instanceof ReferenceCounted referenceCounted) {
                referenceCounted.release();
            } else if (o instanceof BodyElements<?> elements) {
                // they are never pulled
                discardElements(request, elements);
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
        MessageBodyWriter messageBodyWriter = routeInfo.getMessageBodyWriter();
        if (messageBodyWriter != null && response.getBodyWriter().isEmpty()) {
            response.bodyWriter(messageBodyWriter);
        }
        return response;
    }

    private ExecutionFlow<MutableHttpResponse<?>> fromKotlinCoroutineExecute(PropagatedContext propagatedContext, HttpRequest<?> request, @Nullable Object body, RouteInfo<?> routeInfo) {
        boolean isKotlinFunctionReturnTypeUnit =
            routeInfo instanceof MethodBasedRouteInfo<?, ?> mbri &&
                isKotlinFunctionReturnTypeUnit(mbri.getTargetMethod().getExecutableMethod());
        if (isKotlinCoroutineSuspended(body)) {
            final Supplier<CompletableFuture<?>> supplier = ContinuationArgumentBinder.extractContinuationCompletableFutureSupplier(request);
            if (supplier == null) {
                return ExecutionFlow.error(new IllegalStateException("Missing coroutine continuation for suspended route"));
            }
            boolean notFoundOnMissingBody = serverConfiguration.isNotFoundOnMissingBody();
            // the result is wrapped so that an empty (null) result still reaches the transformer
            CompletionStage<Optional<Object>> result = ((CompletableFuture<Object>) supplier.get()).thenApply(Optional::ofNullable);
            return CompletableFutureExecutionFlow.just(result).flatMap(optional -> {
                Object obj = optional.orElse(null);
                if (obj == null) {
                    return notFoundOnMissingBody ? ExecutionFlow.just(notFoundErrorResponse(request)) : ExecutionFlow.empty();
                }
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
                return ExecutionFlow.just(response);
            });
        }
        Object suspendedBody = isKotlinFunctionReturnTypeUnit ? null : body;
        return fromImperativeExecute(propagatedContext, request, routeInfo, suspendedBody);
    }

    private ExecutionFlow<MutableHttpResponse<?>> fromReactiveExecute(PropagatedContext propagatedContext,
                                                                      HttpRequest<?> request,
                                                                      Publisher<Object> publisher,
                                                                      RouteInfo<?> routeInfo) {
        boolean isSingle = routeInfo.isSpecifiedSingle() || routeInfo.isReactive() && routeInfo.isSingleResult() || Publishers.isSingle(publisher.getClass());
        boolean isCompletable = !isSingle && routeInfo.isVoid() && routeInfo.isCompletable();
        if (isSingle || isCompletable) {
            // full response case: the publisher is subscribed to right away, a publisher that
            // completes synchronously yields an imperative flow and the rest of the lifecycle
            // stays free of Reactor operators
            return subscribeSingle(propagatedContext, request, publisher, routeInfo)
                .<MutableHttpResponse<?>>flatMap(o -> {
                    if (o instanceof Optional<?> optional) {
                        o = optional.isPresent() ? optional.get() : EMPTY;
                    }
                    if (o == EMPTY) {
                        // empty publisher, or empty Optional
                        return ExecutionFlow.just(emptyResponse(request, routeInfo));
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
                    return ExecutionFlow.just(singleResponse);
                });
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
                response = response.flatMap(resp ->
                    ReactiveExecutionFlow.toPublisher(processPublisherBody(propagatedContext, request, resp, routeInfo)));
            }
            // the outer publisher sees the request and the propagated context, whether or not the
            // bodies of its responses are reactive
            response = response.contextWrite(context -> ReactorPropagation.addPropagatedContext(context, propagatedContext).put(ServerRequestContext.KEY, request));
            return ReactiveExecutionFlow.fromPublisher(ReactivePropagation.propagate(propagatedContext, response));
        }
        // a streamed response keeps the bodies of the route until its stream ends
        return processPublisherBody(propagatedContext, request, forStatus(routeInfo, null), false, releaseWhenStreamEnds(request, publisher), routeInfo);
    }

    /**
     * The flow of the first value of a single-valued publisher, {@link #EMPTY} if there is none. The
     * request and the propagated context are available in the Reactor context of the publisher, and
     * the propagated context is bound as a thread-local for the subscription and the signals.
     * <p>The publisher is subscribed to right away, so a publisher that completes synchronously
     * yields an imperative flow. A value that arrives during the subscription is only seen once
     * the subscription returns, which is soon on the event loop, where nothing may block. There
     * are two exceptions, where the publisher stays lazy and is subscribed to by the consumer of
     * the response, so its value is passed on as it arrives:
     * <ul>
     *     <li>A filter that subscribes to the response publisher itself
     *     ({@link ReactiveFilterChainElement}): it may add values to the Reactor context.</li>
     *     <li>A route on an executor: it may block. A publisher that emits the response and then
     *     keeps the thread, e.g. a {@code Mono.create} that emits the first event of a streamed
     *     body and goes on working, would hold the response until it is done.</li>
     * </ul>
     *
     * @param propagatedContext The propagated context
     * @param request           The request
     * @param publisher         The publisher
     * @param routeInfo         The route
     * @return The flow of the first value, immediate if the publisher completed synchronously
     */
    private ExecutionFlow<Object> subscribeSingle(PropagatedContext propagatedContext, HttpRequest<?> request, Publisher<Object> publisher, RouteInfo<?> routeInfo) {
        if (publisher instanceof Fuseable.ScalarCallable<?>) {
            // Mono.just, Mono.empty, Mono.error: nothing observes the context
            return ReactiveExecutionFlow.fromPublisherEager(publisher, propagatedContext)
                .map(o -> o == null ? EMPTY : o);
        }
        if (ReactiveFilterChainElement.isPresent(propagatedContext) || routeInfo.getExecutor(serverConfiguration.getThreadSelection()) != null) {
            Mono<Object> lazy = Mono.from(publisher)
                .contextWrite(context -> ReactorPropagation.addPropagatedContext(context, propagatedContext).put(ServerRequestContext.KEY, request))
                .defaultIfEmpty(EMPTY);
            return ReactiveExecutionFlow.fromPublisher(ReactivePropagation.propagate(propagatedContext, lazy));
        }
        Mono<Object> mono = Mono.from(publisher)
            .contextWrite(context -> context.put(ServerRequestContext.KEY, request));
        return ReactiveExecutionFlow.fromPublisherEager(mono, propagatedContext)
            .map(o -> o == null ? EMPTY : o);
    }

    private MutableHttpResponse<?> emptyResponse(HttpRequest<?> request, RouteInfo<?> routeInfo) {
        if (routeInfo.isVoid()) {
            return voidResponse(routeInfo);
        } else if (serverConfiguration.isNotFoundOnMissingBody()) {
            return notFoundErrorResponse(request);
        } else {
            return noContentResponse(routeInfo);
        }
    }

    private MutableHttpResponse<Object> voidResponse(RouteInfo<?> routeInfo) {
        return forStatus(routeInfo, HttpStatus.OK)
            .header(HttpHeaders.CONTENT_LENGTH, "0");
    }

    private MutableHttpResponse<Object> noContentResponse(RouteInfo<?> routeInfo) {
        return forStatus(routeInfo, HttpStatus.NO_CONTENT)
            .header(HttpHeaders.CONTENT_LENGTH, "0");
    }

    private CompletionStage<MutableHttpResponse<?>> fromCompletionStage(HttpRequest<?> request,
                                                                        CompletionStage<Object> completionStage,
                                                                        RouteInfo<?> routeInfo) {
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
                    CompletionStage<@Nullable Object> inner = (CompletionStage<Object>) mutableResponse.body();
                    if (inner == null) {
                        inner = CompletableFuture.completedFuture(null);
                    }
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

    private ExecutionFlow<MutableHttpResponse<?>> processPublisherBody(PropagatedContext propagatedContext,
                                                                       HttpRequest<?> request,
                                                                       MutableHttpResponse<?> response,
                                                                       RouteInfo<?> routeInfo) {
        Object body = response.body();
        if (body == null) {
            return ExecutionFlow.just(response);
        }
        Publisher<Object> bodyPublisher = Publishers.convertToPublisher(conversionService, body);
        boolean isSinglePublisher = Publishers.isSingle(body.getClass());
        if (!isSinglePublisher) {
            // a streamed response keeps the bodies of the route until its stream ends
            bodyPublisher = releaseWhenStreamEnds(request, bodyPublisher);
        }
        return processPublisherBody(propagatedContext, request, response, isSinglePublisher, bodyPublisher, routeInfo);
    }

    private ExecutionFlow<MutableHttpResponse<?>> processPublisherBody(PropagatedContext propagatedContext,
                                                                       HttpRequest<?> request,
                                                                       MutableHttpResponse<?> response,
                                                                       boolean isSinglePublisher,
                                                                       Publisher<Object> bodyPublisher,
                                                                       RouteInfo<?> routeInfo) {
        if (isSinglePublisher) {
            // the single value is the body, an empty publisher is a missing body
            return subscribeSingle(propagatedContext, request, bodyPublisher, routeInfo)
                .map(b -> b == EMPTY ? emptyResponse(request, routeInfo) : response.body(b));
        }
        MediaType mediaType = response.getContentType().orElseGet(() -> resolveDefaultResponseContentType(request, routeInfo));

        // the streaming body is subscribed to by the response writer: the request and the
        // propagated context are in its Reactor context, and the propagated context is bound for
        // the subscription and the signals
        Flux<Object> streamingBody = applyExecutorToPublisher(
            bodyPublisher,
            findExecutor(routeInfo),
            propagatedContext
        ).contextWrite(cv -> ReactorPropagation.addPropagatedContext(cv, propagatedContext).put(ServerRequestContext.KEY, request));

        return ExecutionFlow.just(response
            .contentType(mediaType)
            .body(ReactivePropagation.propagate(propagatedContext, streamingBody)));
    }

    private void applyConfiguredHeaders(MutableHttpHeaders headers) {
        if (serverConfiguration.isDateHeader() && !headers.contains(HttpHeaders.DATE)) {
            headers.add(HttpHeaders.DATE, HttpDateHeader.now());
        }
        if (headers.get(HttpHeaders.SERVER) == null) {
            serverConfiguration.getServerHeader()
                .ifPresent(header -> headers.add(HttpHeaders.SERVER, header));
        }
    }

    private MutableHttpResponse<Object> forStatus(RouteInfo<?> routeMatch) {
        return forStatus(routeMatch, HttpStatus.OK);
    }

    private MutableHttpResponse<Object> forStatus(RouteInfo<?> routeMatch, @Nullable HttpStatus defaultStatus) {
        HttpStatus status = routeMatch.findStatus(defaultStatus);
        MutableHttpResponse<Object> response = HttpResponse.status(status);
        String contentDisposition = routeMatch.findContentDispositionHeader();
        if (contentDisposition != null) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        }
        return response;
    }

}
