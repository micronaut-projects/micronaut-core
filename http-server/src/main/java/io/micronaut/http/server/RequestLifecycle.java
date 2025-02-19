/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.NotAcceptableException;
import io.micronaut.http.server.exceptions.NotAllowedException;
import io.micronaut.http.server.exceptions.NotFoundException;
import io.micronaut.http.server.exceptions.NotWebSocketRequestException;
import io.micronaut.http.server.exceptions.UnsupportedMediaException;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.web.router.DefaultRouteInfo;
import io.micronaut.web.router.DefaultUriRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.UriRouteMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * This class handles the full route processing lifecycle for a request.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public class RequestLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLifecycle.class);

    private final RouteExecutor routeExecutor;
    private final boolean multipartEnabled;
    private HttpRequest<?> request;

    /**
     * @param routeExecutor The route executor to use for route resolution
     */
    protected RequestLifecycle(RouteExecutor routeExecutor) {
        this.routeExecutor = Objects.requireNonNull(routeExecutor, "routeExecutor");
        Optional<Boolean> isMultiPartEnabled = routeExecutor.serverConfiguration.getMultipart().getEnabled();
        this.multipartEnabled = isMultiPartEnabled.isEmpty() || isMultiPartEnabled.get();
    }

    /**
     * @param routeExecutor The route executor to use for route resolution
     * @param request       The request
     * @deprecated Will be removed after 4.3.0
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    protected RequestLifecycle(RouteExecutor routeExecutor, HttpRequest<?> request) {
        this(routeExecutor);
        this.request = request;
    }

    /**
     * Execute this request normally.
     *
     * @return The response to the request.
     * @deprecated Will be removed after 4.3.0
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    protected final ExecutionFlow<HttpResponse<?>> normalFlow() {
        return normalFlow(request);
    }

    /**
     * The request for this lifecycle. This may be changed by filters.
     *
     * @return The current request
     * @deprecated Will be removed after 4.3.0
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    protected final HttpRequest<?> request() {
        return request;
    }

    /**
     * Try to find a static file for this request. If there is a file, filters will still run, but
     * only after the call to this method.
     *
     * @return The file at this path, or {@code null} if none is found
     * @deprecated Will be removed after 4.3.0
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    @Nullable
    protected FileCustomizableResponseType findFile() {
        return null;
    }

    /**
     * Execute this request normally.
     *
     * @param request The request
     * @return The response to the request.
     */
    protected final ExecutionFlow<HttpResponse<?>> normalFlow(HttpRequest<?> request) {
        try {
            Objects.requireNonNull(request, "request");
            if (!multipartEnabled) {
                MediaType contentType = request.getContentType().orElse(null);
                if (contentType != null &&
                    contentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Multipart uploads have been disabled via configuration. Rejected request for URI {}, method {}, and content type {}", request.getUri(),
                            request.getMethodName(), contentType);
                    }
                    return onStatusError(
                        request,
                        HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                        "Content Type [" + contentType + "] not allowed"
                    );
                }
            }
            return runServerFilters(request);
        } catch (Throwable t) {
            return onError(request, t);
        }
    }

    private ExecutionFlow<HttpResponse<?>> executeRoute(HttpRequest<?> request,
                                                        PropagatedContext propagatedContext,
                                                        RouteMatch<?> routeMatch) {
        ExecutionFlow<RouteMatch<?>> routeMatchFlow = fulfillArguments(routeMatch, request);
        ExecutionFlow<HttpResponse<?>> responseFlow = callRoute(routeMatchFlow, request, propagatedContext);
        responseFlow = handleStatusException(responseFlow, request, routeMatch, propagatedContext);
        return onErrorNoFilter(responseFlow, request, propagatedContext);
    }

    private ExecutionFlow<HttpResponse<?>> callRoute(ExecutionFlow<RouteMatch<?>> flux,
                                                     HttpRequest<?> filteredRequest,
                                                     PropagatedContext propagatedContext) {
        Object o = ((ExecutionFlow<?>) flux).tryCompleteValue();
        // usually this is a DefaultUriRouteMatch, avoid scalability issues here
        RouteMatch<?> routeMatch = o instanceof DefaultUriRouteMatch<?, ?> urm ? urm : (RouteMatch<?>) o;
        if (routeMatch != null) {
            return routeExecutor.callRoute(propagatedContext, routeMatch, filteredRequest);
        }
        return flux.flatMap(rm -> routeExecutor.callRoute(propagatedContext, rm, filteredRequest));
    }

    private ExecutionFlow<HttpResponse<?>> handleStatusException(ExecutionFlow<HttpResponse<?>> flux,
                                                                 HttpRequest<?> request,
                                                                 RouteMatch<?> routeMatch,
                                                                 PropagatedContext propagatedContext) {
        Object o = ((ExecutionFlow<?>) flux).tryCompleteValue();
        // usually this is a MutableHttpResponse, avoid scalability issues here
        HttpResponse<?> response = o instanceof MutableHttpResponse<?> mut ? mut : (HttpResponse<?>) o;
        if (response != null) {
            return handleStatusException(request, response, routeMatch, propagatedContext);
        }
        return flux.flatMap(res -> handleStatusException(request, res, routeMatch, propagatedContext));
    }

    private ExecutionFlow<HttpResponse<?>> onErrorNoFilter(ExecutionFlow<HttpResponse<?>> flux,
                                                           HttpRequest<?> request,
                                                           PropagatedContext propagatedContext) {
        if (flux.tryCompleteValue() != null) {
            return flux;
        }
        Throwable throwable = flux.tryCompleteError();
        if (throwable != null) {
            return onErrorNoFilter(request, throwable, propagatedContext);
        }
        return flux.onErrorResume(exp -> onErrorNoFilter(request, exp, propagatedContext));
    }

    /**
     * Handle an error in this request. It also runs filters for the error handling.
     *
     * @param request   The request
     * @param throwable The error
     * @return The response for the error
     */
    protected final ExecutionFlow<HttpResponse<?>> onError(HttpRequest<?> request, Throwable throwable) {
        try {
            return runWithFilters(request, (filteredRequest, propagatedContext) -> onErrorNoFilter(filteredRequest, throwable, propagatedContext))
                .onErrorResume(t -> createDefaultErrorResponseFlow(request, t));
        } catch (Throwable e) {
            return createDefaultErrorResponseFlow(request, e);
        }
    }

    private ExecutionFlow<HttpResponse<?>> onErrorNoFilter(HttpRequest<?> request, Throwable t, PropagatedContext propagatedContext) {

        if ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            // top level exceptions returned by CompletableFutures. These always wrap the real exception thrown.
            t = t.getCause();
        }
        if (t instanceof ConversionErrorException cee && cee.getCause() instanceof JsonSyntaxException jse) {
            // with delayed parsing, json syntax errors show up as conversion errors
            t = jse;
        }
        final Throwable cause = t;

        RouteMatch<?> errorRoute = routeExecutor.findErrorRoute(cause, findDeclaringType(request), request);
        if (errorRoute != null) {
            return handleErrorRoute(request, propagatedContext, errorRoute, cause);
        } else {
            Optional<BeanDefinition<ExceptionHandler>> optionalDefinition = routeExecutor.beanContext.findBeanDefinition(ExceptionHandler.class, Qualifiers.byTypeArgumentsClosest(cause.getClass(), Object.class));
            if (optionalDefinition.isPresent()) {
                BeanDefinition<ExceptionHandler> handlerDefinition = optionalDefinition.get();
                return handlerExceptionHandler(request, propagatedContext, handlerDefinition, cause);
            }
            if (RouteExecutor.isIgnorable(cause)) {
                RouteExecutor.logIgnoredException(cause);
                return ExecutionFlow.empty();
            }
            return createDefaultErrorResponseFlow(request, cause);
        }
    }

    private Class<?> findDeclaringType(HttpRequest<?> request) {
        // find the origination of the route
        Optional<RouteInfo<?>> previousRequestRouteInfo = RouteAttributes.getRouteInfo(request);
        return previousRequestRouteInfo.map(RouteInfo::getDeclaringType).orElse(null);
    }

    private ExecutionFlow<HttpResponse<?>> handleErrorRoute(HttpRequest<?> request, PropagatedContext propagatedContext, RouteMatch<?> errorRoute, Throwable cause) {
        RouteExecutor.setRouteAttributes(request, errorRoute);
        if (routeExecutor.serverConfiguration.isLogHandledExceptions()) {
            routeExecutor.logException(cause);
        }
        try {
            return ExecutionFlow.just(errorRoute)
                .flatMap(routeMatch -> routeExecutor.callRoute(propagatedContext, routeMatch, request)
                    .flatMap(res -> handleStatusException(request, res, routeMatch, propagatedContext))
                )
                .onErrorResume(u -> createDefaultErrorResponseFlow(request, u))
                .<HttpResponse<?>>map(response -> {
                    RouteAttributes.setException(response, cause);
                    return response;
                })
                .onErrorResume(throwable -> createDefaultErrorResponseFlow(request, throwable));
        } catch (Throwable e) {
            return createDefaultErrorResponseFlow(request, e);
        }
    }

    private ExecutionFlow<HttpResponse<?>> handlerExceptionHandler(HttpRequest<?> request, PropagatedContext propagatedContext, BeanDefinition<ExceptionHandler> handlerDefinition, Throwable cause) {
        final Optional<ExecutableMethod<ExceptionHandler, Object>> optionalMethod = handlerDefinition.findPossibleMethods("handle").findFirst();
        RouteInfo<Object> routeInfo;
        if (optionalMethod.isPresent()) {
            routeInfo = new ExecutableRouteInfo<>(optionalMethod.get(), true);
        } else {
            routeInfo = new DefaultRouteInfo<>(
                AnnotationMetadata.EMPTY_METADATA,
                ReturnType.of(Object.class),
                List.of(),
                MediaType.fromType(handlerDefinition.getBeanType()).map(Collections::singletonList).orElse(Collections.emptyList()),
                handlerDefinition.getBeanType(),
                true,
                false,
                MessageBodyHandlerRegistry.EMPTY
            );
        }
        Supplier<ExecutionFlow<HttpResponse<?>>> responseSupplier = () -> {
            ExceptionHandler<Throwable, ?> handler = routeExecutor.beanContext.getBean(handlerDefinition);
            try {
                if (routeExecutor.serverConfiguration.isLogHandledExceptions()) {
                    routeExecutor.logException(cause);
                }
                Object result = handler.handle(request, cause);
                return routeExecutor.createResponseForBody(propagatedContext, request, result, routeInfo, null);
            } catch (Throwable e) {
                return createDefaultErrorResponseFlow(request, e);
            }
        };
        ExecutionFlow<HttpResponse<?>> responseFlow;
        final ExecutorService executor = routeExecutor.findExecutor(routeInfo);
        if (executor != null) {
            responseFlow = ExecutionFlow.async(executor, responseSupplier);
        } else {
            responseFlow = responseSupplier.get();
        }
        return responseFlow
            .<HttpResponse<?>>map(response -> {
                RouteAttributes.setException(response, cause);
                return response;
            })
            .onErrorResume(throwable -> createDefaultErrorResponseFlow(request, throwable));
    }

    /**
     * Run the filters for this request, and then run the given flow.
     *
     * @param request          The request
     * @param responseProvider Downstream flow, runs inside the filters
     * @return Execution flow that completes after the all the filters and the downstream flow
     */
    protected final ExecutionFlow<HttpResponse<?>> runWithFilters(HttpRequest<?> request, BiFunction<HttpRequest<?>, PropagatedContext, ExecutionFlow<HttpResponse<?>>> responseProvider) {
        try {
            List<GenericHttpFilter> httpFilters = routeExecutor.router.findFilters(request);
            FilterRunner filterRunner = new FilterRunner(httpFilters, responseProvider) {
                @Override
                protected ExecutionFlow<HttpResponse<?>> processResponse(HttpRequest<?> request, HttpResponse<?> response, PropagatedContext propagatedContext) {
                    RouteInfo<?> routeInfo = RouteAttributes.getRouteInfo(response).orElse(null);
                    return handleStatusException(request, response, routeInfo, propagatedContext)
                        .onErrorResume(throwable -> onErrorNoFilter(request, throwable, propagatedContext));
                }

                @Override
                protected ExecutionFlow<HttpResponse<?>> processFailure(HttpRequest<?> request, Throwable failure, PropagatedContext propagatedContext) {
                    return onErrorNoFilter(request, failure, propagatedContext);
                }
            };
            return filterRunner.run(request);
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    private ExecutionFlow<HttpResponse<?>> runServerFilters(HttpRequest<?> request) {
        try {
            PropagatedContext propagatedContext = PropagatedContext.get();
            List<GenericHttpFilter> preMatchingFilters = routeExecutor.router.findPreMatchingFilters(request);
            FilterRunner filterRunner = new FilterRunner(preMatchingFilters, null, null) {

                UriRouteMatch<Object, Object> routeMatch;

                @Override
                protected List<GenericHttpFilter> findFiltersAfterRouteMatch(HttpRequest<?> request) {
                    return routeExecutor.router.findFilters(request);
                }

                @Override
                protected ExecutionFlow<HttpResponse<?>> provideResponse(@NonNull HttpRequest<?> request, @NonNull PropagatedContext propagatedContext) {
//                    RouteMatch<?> routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
                    if (this.routeMatch == null) {
                        //Check if there is a file for the route before returning route not found
                        FileCustomizableResponseType fileCustomizableResponseType = findFile(request);
                        if (fileCustomizableResponseType != null) {
                            return ExecutionFlow.just(HttpResponse.ok(fileCustomizableResponseType));
                        }
                        return onRouteMiss(request, propagatedContext);
                    }
                    // all ok proceed to try and execute the route
                    if (routeMatch.getRouteInfo().isWebSocketRoute()) {
                        return onStatusError(
                            request,
                            new NotWebSocketRequestException(),
                            routeMatch.getDeclaringType(),
                            propagatedContext);
                    }
                    return executeRoute(request, propagatedContext, routeMatch);
                }

                @Override
                protected void doRouteMatch(HttpRequest<?> request) {
                    // Store it a field because RouteExecutor#findRouteMatch in some cases stores and sets something different
                    // This can be corrected after Cors / Options stuff migrated to pre-matching
                    routeMatch = routeExecutor.findRouteMatch(request);
                    if (routeMatch == null) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Not matched route for request {} - {}", request.getMethodName(), request.getUri().getPath());
                        }
                        return;
                    }
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Matched route {} - {} to controller {}", request.getMethodName(), request.getUri().getPath(), routeMatch.getDeclaringType());
                    }
                    RouteExecutor.setRouteAttributes(request, routeMatch);
                }

                @Override
                protected ExecutionFlow<HttpResponse<?>> processResponse(HttpRequest<?> request, HttpResponse<?> response, PropagatedContext propagatedContext) {
                    RouteInfo<?> routeInfo = RouteAttributes.getRouteInfo(response).orElse(null);
                    return handleStatusException(request, response, routeInfo, propagatedContext)
                        .onErrorResume(throwable -> onErrorNoFilter(request, throwable, propagatedContext));
                }

                @Override
                protected ExecutionFlow<HttpResponse<?>> processFailure(HttpRequest<?> request, Throwable failure, PropagatedContext propagatedContext) {
                    return onErrorNoFilter(request, failure, propagatedContext);
                }
            };
            return filterRunner.run(request, propagatedContext);
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    private ExecutionFlow<HttpResponse<?>> handleStatusException(HttpRequest<?> request,
                                                                 HttpResponse<?> response,
                                                                 @Nullable RouteMatch<?> routeMatch,
                                                                 PropagatedContext propagatedContext) {
        if (response.code() < 400) {
            return ExecutionFlow.just(response);
        }
        RouteInfo<?> routeInfo = routeMatch == null ? null : routeMatch.getRouteInfo();
        return handleStatusException(request, response, routeInfo, propagatedContext);
    }

    private ExecutionFlow<HttpResponse<?>> handleStatusException(HttpRequest<?> request,
                                                                 HttpResponse<?> response,
                                                                 RouteInfo<?> routeInfo,
                                                                 PropagatedContext propagatedContext) {
        if (response.code() >= 400 && routeInfo != null && !routeInfo.isErrorRoute()) {
            RouteMatch<Object> statusRoute = routeExecutor.findStatusRoute(request, response.code(), routeInfo);
            if (statusRoute != null) {
                return executeRoute(request, propagatedContext, statusRoute);
            }
        }
        return ExecutionFlow.just(response);
    }

    private ExecutionFlow<HttpResponse<?>> createDefaultErrorResponseFlow(HttpRequest<?> httpRequest, Throwable cause) {
        return ExecutionFlow.just(routeExecutor.createDefaultErrorResponse(httpRequest, cause));
    }

    final ExecutionFlow<HttpResponse<?>> onRouteMiss(HttpRequest<?> httpRequest) {
        return onRouteMiss(httpRequest, PropagatedContext.getOrEmpty());
    }

    final ExecutionFlow<HttpResponse<?>> onRouteMiss(HttpRequest<?> httpRequest, PropagatedContext propagatedContext) {
        HttpMethod httpMethod = httpRequest.getMethod();
        String requestMethodName = httpRequest.getMethodName();
        MediaType contentType = httpRequest.getContentType().orElse(null);

        if (LOG.isDebugEnabled()) {
            LOG.debug("No matching route: {} {}", httpMethod, httpRequest.getUri());
        }

        // if there is no route present try to locate a route that matches a different HTTP method
        final List<UriRouteMatch<Object, Object>> anyMatchingRoutes = routeExecutor.router.findAny(httpRequest);
        final Collection<MediaType> acceptedTypes = httpRequest.accept();
        final boolean hasAcceptHeader = CollectionUtils.isNotEmpty(acceptedTypes);

        Set<MediaType> acceptableContentTypes = contentType != null ? new HashSet<>(5) : null;
        Set<String> allowedMethods = new HashSet<>(5);
        Set<MediaType> produceableContentTypes = hasAcceptHeader ? new HashSet<>(5) : null;
        Class<?> declaringType = null;
        for (UriRouteMatch<?, ?> anyRoute : anyMatchingRoutes) {
            final String routeMethod = anyRoute.getRouteInfo().getHttpMethodName();
            if (!requestMethodName.equals(routeMethod)) {
                allowedMethods.add(routeMethod);
            }
            if (contentType != null && !anyRoute.getRouteInfo().doesConsume(contentType)) {
                acceptableContentTypes.addAll(anyRoute.getRouteInfo().getConsumes());
            }
            if (hasAcceptHeader && !anyRoute.getRouteInfo().doesProduce(acceptedTypes)) {
                produceableContentTypes.addAll(anyRoute.getRouteInfo().getProduces());
            }
            declaringType = anyRoute.getDeclaringType();
        }

        if (CollectionUtils.isNotEmpty(acceptableContentTypes)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Content type not allowed for URI {}, method {}, and content type {}", httpRequest.getUri(),
                    requestMethodName, contentType);
            }
            return onStatusError(
                httpRequest,
                new UnsupportedMediaException(contentType.toString(),  acceptableContentTypes.stream().map(MediaType::toString).toList()),
                declaringType,
                propagatedContext);
        }
        if (CollectionUtils.isNotEmpty(produceableContentTypes)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Content type not allowed for URI {}, method {}, and content type {}", httpRequest.getUri(),
                    requestMethodName,
                    contentType);
            }
            return onStatusError(
                httpRequest,
                new NotAcceptableException(acceptedTypes.stream().map(MediaType::toString).toList(), produceableContentTypes.stream().map(MediaType::toString).toList()),
                declaringType,
                propagatedContext);
        }
        if (!allowedMethods.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Method not allowed for URI {} and method {}", httpRequest.getUri(), requestMethodName);
            }
            return onStatusError(
                httpRequest,
                new NotAllowedException(requestMethodName, httpRequest.getUri(), allowedMethods),
                declaringType,
                propagatedContext);
        }
        return onStatusError(
            httpRequest,
            new NotFoundException(),
            declaringType,
            propagatedContext);
    }

    /**
     * Build a status response. Calls any status routes, if available.
     *
     * @param request         The request
     * @param defaultResponse The default response if there is no status route
     * @param message         The error message
     * @return The computed response flow
     */
    protected final ExecutionFlow<HttpResponse<?>> onStatusError(HttpRequest<?> request, MutableHttpResponse<?> defaultResponse, String message) {

        ExecutionFlow<HttpResponse<?>> flow = executionFlowWithStatusRoute(request, defaultResponse.getStatus());
        if (flow != null) {
            return flow;
        }
        if (request.getMethod() != HttpMethod.HEAD) {
            defaultResponse = routeExecutor.errorResponseProcessor.processResponse(ErrorContext.builder(request)
                .errorMessage(message)
                .build(), defaultResponse);
            if (defaultResponse.getContentType().isEmpty()) {
                defaultResponse = defaultResponse.contentType(MediaType.APPLICATION_JSON_TYPE);
            }
        }
        return ExecutionFlow.just(defaultResponse);
    }

    /**
     * Try to find a static file for this request. If there is a file, filters will still run, but
     * only after the call to this method.
     *
     * @param request The request
     * @return The file at this path, or {@code null} if none is found
     */
    @Nullable
    protected FileCustomizableResponseType findFile(HttpRequest<?> request) {
        return findFile();
    }

    /**
     * Fulfill the arguments of the given route with data from the request. If necessary, this also
     * waits for body data to be available, if there are arguments that need immediate binding.<br>
     * Note that in some cases some arguments may still be unsatisfied after this, if they are
     * missing and are {@link Optional}. They are satisfied with {@link Optional#empty()} later.
     *
     * @param routeMatch The route match to fulfill
     * @param request    The request
     * @return The fulfilled route match, after all necessary data is available
     */
    protected ExecutionFlow<RouteMatch<?>> fulfillArguments(RouteMatch<?> routeMatch, HttpRequest<?> request) {
        try {
            // try to fulfill the argument requirements of the route
            routeExecutor.requestArgumentSatisfier.fulfillArgumentRequirementsBeforeFilters(routeMatch, request);
            return ExecutionFlow.just(routeMatch);
        } catch (Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    /**
     * Build a status response. Calls any status routes, if available.
     *
     * @param request   The request
     * @param cause     The declaringType
     * @param declaringType  Declaring type
     * @param propagatedContext The propagated context
     * @return The computed response flow
     */
    @NonNull
    private ExecutionFlow<HttpResponse<?>> onStatusError(
            @NonNull HttpRequest<?> request,
            @NonNull HttpStatusException cause,
            @Nullable Class<?> declaringType,
            @NonNull PropagatedContext propagatedContext) {
        ExecutionFlow<HttpResponse<?>> flow  = executionFlowWithStatusRoute(request, cause.getStatus());
        if (flow != null) {
            return flow;
        }
        flow  = executionFlowWithErrorRoute(request, cause, declaringType, propagatedContext);
        if (flow != null) {
            return flow;
        }
        flow  = executionFlowWithExceptionHandler(request, cause, propagatedContext);
        if (flow != null) {
            return flow;
        }
        throw new ConfigurationException("no status route for status " + cause.getStatus() + " or exception handler or error route for " + cause.getClass().getName());
    }

    @Nullable
    private ExecutionFlow<HttpResponse<?>> executionFlowWithExceptionHandler(
            @NonNull HttpRequest<?> request,
            @NonNull HttpStatusException cause,
            @NonNull PropagatedContext propagatedContext) {
        return routeExecutor.beanContext.findBeanDefinition(ExceptionHandler.class, Qualifiers.byTypeArgumentsClosest(cause.getClass(), Object.class))
                .map(handlerDefinition -> handlerExceptionHandler(request, propagatedContext, handlerDefinition, cause))
                .orElse(null);
    }

    @Nullable
    private ExecutionFlow<HttpResponse<?>> executionFlowWithErrorRoute(
            @NonNull HttpRequest<?> request,
            @NonNull HttpStatusException cause,
            @Nullable Class<?> declaringType,
            @NonNull PropagatedContext propagatedContext) {
        if (declaringType == null) {
            declaringType = findDeclaringType(request);
        }
        RouteMatch<?> errorRoute = routeExecutor.findErrorRoute(cause, declaringType, request);
        return errorRoute != null
                ? handleErrorRoute(request, propagatedContext, errorRoute, cause)
                : null;
    }

    @Nullable
    private ExecutionFlow<HttpResponse<?>> executionFlowWithStatusRoute(@NonNull HttpRequest<?> request,
                                                                        @NonNull HttpStatus status) {
        return routeExecutor.router.findStatusRoute(status, request)
                .map(routeMatch -> executeRoute(request, PropagatedContext.getOrEmpty(), routeMatch))
                .orElse(null);
    }
}



