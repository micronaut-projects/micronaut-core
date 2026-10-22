/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.web.router.MethodBasedRouteInfo;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.RouteMatch;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

import static io.micronaut.inject.beans.KotlinExecutableMethodUtils.isKotlinFunctionReturnTypeUnit;

/**
 * Turns the result of a route into its response: the value the route returned, the value of the
 * stage or the publisher it returned, or the result of its suspend function.
 *
 * <p>The base class reads what it needs about the route from the route on every call: it adapts
 * the result of any route, e.g. of a custom {@link RouteInfo}, the route of an exception handler,
 * or any route when the compatibility execution is forced, see {@link RouteExecutor}. A
 * {@link Prepared} adapter is selected once for a route by its declared return type: it read the
 * facts about the route when it was prepared, and checks at runtime only what depends on the
 * value the route returned.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
abstract sealed class RouteResult {

    final RouteExecutor owner;

    RouteResult(RouteExecutor owner) {
        this.owner = owner;
    }

    ExecutionFlow<MutableHttpResponse<?>> fromImperativeExecute(PropagatedContext propagatedContext,
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
            MutableHttpResponse<Object> mutableHttpResponse = owner.forStatus(routeInfo, null);
            if (body != null) {
                mutableHttpResponse = mutableHttpResponse.body(body);
            }
            return ExecutionFlow.just(mutableHttpResponse);
        }

        if (responseBodyAsyncOrReactive(routeInfo)) {
            return owner.processPublisherBody(propagatedContext, request, outgoingResponse, routeInfo, this);
        }
        return ExecutionFlow.just(outgoingResponse);
    }

    CompletionStage<MutableHttpResponse<?>> fromCompletionStage(HttpRequest<?> request,
                                                                CompletionStage<Object> completionStage,
                                                                RouteInfo<?> routeInfo) {
        return completionStage.thenCompose(asyncBody -> {
            MutableHttpResponse<?> mutableResponse;
            if (asyncBody instanceof Optional<?> optional) {
                if (optional.isPresent()) {
                    asyncBody = optional.get();
                } else if (owner.serverConfiguration.isNotFoundOnMissingBody()) {
                    return CompletableFuture.completedStage(notFoundErrorResponse(request));
                } else {
                    return CompletableFuture.completedStage(noContentResponse(routeInfo));
                }
            }
            boolean explicitResponse = false;
            if (asyncBody instanceof HttpResponse<?> httpResponse) {
                mutableResponse = httpResponse.toMutableResponse();
                // CompletionStage<HttpResponse<CompletionStage<T>>>
                if (nestedResponseBodyAsync(routeInfo)) {
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
                mutableResponse = owner.forStatus(routeInfo, status);
            } else {
                mutableResponse = owner.forStatus(routeInfo, null)
                    .body(asyncBody);
            }
            if (mutableResponse.body() == null && !explicitResponse) {
                if (routeInfo.isVoid()) {
                    return CompletableFuture.completedStage(voidResponse(routeInfo));
                } else if (owner.serverConfiguration.isNotFoundOnMissingBody()) {
                    return CompletableFuture.completedStage(notFoundErrorResponse(request));
                } else {
                    return CompletableFuture.completedStage(noContentResponse(routeInfo));
                }
            }
            return CompletableFuture.completedStage(mutableResponse);
        });
    }

    MutableHttpResponse<?> emptyResponse(HttpRequest<?> request, RouteInfo<?> routeInfo) {
        if (routeInfo.isVoid()) {
            return voidResponse(routeInfo);
        } else if (owner.serverConfiguration.isNotFoundOnMissingBody()) {
            return notFoundErrorResponse(request);
        } else {
            return noContentResponse(routeInfo);
        }
    }

    MutableHttpResponse<Object> voidResponse(RouteInfo<?> routeInfo) {
        return owner.forStatus(routeInfo, HttpStatus.OK)
            .header(HttpHeaders.CONTENT_LENGTH, "0");
    }

    MutableHttpResponse<Object> noContentResponse(RouteInfo<?> routeInfo) {
        return owner.forStatus(routeInfo, HttpStatus.NO_CONTENT)
            .header(HttpHeaders.CONTENT_LENGTH, "0");
    }

    MutableHttpResponse<?> notFoundErrorResponse(HttpRequest<?> request) {
        MutableHttpResponse<?> response = owner.errorResponseProcessor.processResponse(
            ErrorContext.builder(request)
                .errorMessage("Page Not Found")
                .build(), HttpResponse.notFound());
        if (response.getContentType().isEmpty() && request.getMethod() != HttpMethod.HEAD) {
            return response.contentType(MediaType.APPLICATION_JSON_TYPE);
        }
        return response;
    }

    /**
     * The adapter of any route, which reads the facts of the route on every call.
     *
     * @param owner The route executor
     * @return The adapter
     */
    static RouteResult dynamic(RouteExecutor owner) {
        return new Dynamic(owner);
    }

    /**
     * Prepare the adapter of a route by its declared return type.
     *
     * @param owner     The route executor
     * @param routeInfo The route
     * @return The adapter, the dynamic one for a return type whose results differ in shape
     */
    static RouteResult prepare(RouteExecutor owner, RouteInfo<?> routeInfo) {
        if (routeInfo.isImperative()
            // the value of a response type is a response: it is never converted to a publisher
            || !routeInfo.isAsync() && !routeInfo.isReactive() && !routeInfo.isSuspended()
            && HttpResponse.class.isAssignableFrom(routeInfo.getReturnType().getType())) {
            return new Imperative(owner, routeInfo);
        }
        if (routeInfo.isAsync()) {
            return new Stage(owner, routeInfo);
        }
        if (routeInfo.isReactive()) {
            return new Reactive(owner, routeInfo);
        }
        if (routeInfo.isSuspended()) {
            return new Suspend(owner, routeInfo);
        }
        // e.g. Object: the value decides
        return new PreparedDynamic(owner, routeInfo);
    }

    /**
     * The response of a route for the value it returned, the empty {@link java.util.Optional}
     * being {@code null}, with the attributes and the headers of the server.
     *
     * @param propagatedContext The propagated context
     * @param request           The request
     * @param body              The value the route returned
     * @param routeInfo         The route
     * @param routeMatch        The route match, or {@code null}
     * @return The response
     */
    final ExecutionFlow<HttpResponse<?>> respond(PropagatedContext propagatedContext,
                                                 HttpRequest<?> request,
                                                 @Nullable Object body,
                                                 RouteInfo<?> routeInfo,
                                                 @Nullable RouteMatch<?> routeMatch) {
        if (body == null) {
            return ExecutionFlow.just(owner.finaliseResponse(request, routeInfo, routeMatch, emptyResponse(request, routeInfo)));
        }
        MutableHttpResponse<?> response = null;
        if (body instanceof String) {
            // Micro-optimization for String values
            response = owner.forStatus(routeInfo, null).body(body);
        } else if (body instanceof HttpStatus httpStatus) {
            response = HttpResponse.status(httpStatus);
        }
        if (response != null) {
            return ExecutionFlow.just(owner.finaliseResponse(request, routeInfo, routeMatch, response));
        }
        ExecutionFlow<MutableHttpResponse<?>> outgoingResponse = adapt(propagatedContext, request, body, routeInfo);
        response = outgoingResponse.tryCompleteValue();
        if (response != null) {
            return ExecutionFlow.just(owner.keepRouteBodiesForStream(request, owner.finaliseResponse(request, routeInfo, routeMatch, response)));
        }
        return outgoingResponse.map(res -> owner.keepRouteBodiesForStream(request, owner.finaliseResponse(request, routeInfo, routeMatch, res)));
    }

    /**
     * Adapt a value that is not {@code null}, a {@link String} or an {@link HttpStatus}.
     *
     * @param propagatedContext The propagated context
     * @param request           The request
     * @param body              The value
     * @param routeInfo         The route
     * @return The response, without the attributes and the headers of the server
     */
    abstract ExecutionFlow<MutableHttpResponse<?>> adapt(PropagatedContext propagatedContext,
                                                         HttpRequest<?> request,
                                                         Object body,
                                                         RouteInfo<?> routeInfo);

    /**
     * @param routeInfo The route
     * @return Whether the body of a response the route returns is a stage or a publisher
     */
    boolean responseBodyAsyncOrReactive(RouteInfo<?> routeInfo) {
        return typeVariable(routeInfo.getReturnType()).isAsyncOrReactive();
    }

    /**
     * @param routeInfo The route
     * @return Whether the body of the response the stage or the publisher the route returns
     * completes with is a stage or a publisher
     */
    boolean nestedResponseBodyAsyncOrReactive(RouteInfo<?> routeInfo) {
        return typeVariable(typeVariable(routeInfo.getReturnType())).isAsyncOrReactive();
    }

    /**
     * @param routeInfo The route
     * @return Whether the body of the response the stage the route returns completes with is a
     * stage
     */
    boolean nestedResponseBodyAsync(RouteInfo<?> routeInfo) {
        return typeVariable(typeVariable(routeInfo.getReturnType())).isAsync();
    }

    /**
     * @param routeInfo The route, which returns a publisher
     * @return Whether the publisher is declared single-valued; the type of the publisher may
     * tell it too
     */
    boolean declaredSingle(RouteInfo<?> routeInfo) {
        return routeInfo.isSpecifiedSingle() || routeInfo.isReactive() && routeInfo.isSingleResult();
    }

    /**
     * @param routeInfo The route, which returns a stream
     * @return Whether the stream is a stream of responses
     */
    boolean streamOfResponses(RouteInfo<?> routeInfo) {
        return HttpResponse.class.isAssignableFrom(typeVariable(routeInfo.getReturnType()).getType());
    }

    /**
     * @param routeInfo The route, a suspend function
     * @return Whether the Kotlin function returns {@code Unit}
     */
    boolean kotlinUnit(RouteInfo<?> routeInfo) {
        return routeInfo instanceof MethodBasedRouteInfo<?, ?> mbri
            && isKotlinFunctionReturnTypeUnit(mbri.getTargetMethod().getExecutableMethod());
    }

    /**
     * @param routeInfo The route
     * @return Whether the route runs on an executor
     */
    boolean hasExecutor(RouteInfo<?> routeInfo) {
        return routeInfo.getExecutor(owner.serverConfiguration.getThreadSelection()) != null;
    }

    /**
     * @param routeInfo The route
     * @return The executor a streamed body of the route is subscribed to and observed on, or
     * {@code null}
     */
    @Nullable ExecutorService streamExecutor(RouteInfo<?> routeInfo) {
        return owner.streamExecutor(routeInfo);
    }

    static Argument<?> typeVariable(Argument<?> type) {
        return type.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
    }

    static Argument<?> typeVariable(ReturnType<?> type) {
        return type.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
    }

    /**
     * The adapter of any route, which reads the facts of the route on every call.
     */
    private static final class Dynamic extends RouteResult {
        Dynamic(RouteExecutor owner) {
            super(owner);
        }

        /**
         * Adapt a value by the facts of the route and the type of the value.
         */
        @Override
        ExecutionFlow<MutableHttpResponse<?>> adapt(PropagatedContext propagatedContext, HttpRequest<?> request, Object body, RouteInfo<?> routeInfo) {
            if (routeInfo.isImperative()) {
                return fromImperativeExecute(propagatedContext, request, routeInfo, body);
            }
            if (routeInfo.isAsync()) {
                return CompletableFutureExecutionFlow.just(
                    fromCompletionStage(request, (CompletionStage<Object>) body, routeInfo)
                );
            }
            // special case HttpResponse because FullNettyClientHttpResponse implements Completable...
            boolean isReactive = routeInfo.isReactive() || (Publishers.isConvertibleToPublisher(body) && !(body instanceof HttpResponse<?>));
            if (isReactive) {
                Publisher<Object> publisher = Publishers.convertToPublisher(owner.conversionService, body);
                return owner.fromReactiveExecute(propagatedContext, request, publisher, routeInfo, this);
            }
            if (routeInfo.isSuspended()) {
                return owner.fromKotlinCoroutineExecute(propagatedContext, request, body, routeInfo, this);
            }
            return fromImperativeExecute(propagatedContext, request, routeInfo, body);
        }
    }

    /**
     * An adapter selected for a route by its declared return type, with the facts of the route
     * read once.
     */
    abstract static sealed class Prepared extends RouteResult {
        /**
         * The selected executor of a streamed body when the route has none.
         */
        private static final Object NO_EXECUTOR = new Object();

        final boolean responseBodyAsyncOrReactive;
        final boolean nestedResponseBodyAsyncOrReactive;
        final boolean nestedResponseBodyAsync;
        final boolean declaredSingle;
        final boolean streamOfResponses;
        final boolean kotlinUnit;
        final boolean hasExecutor;
        private final RouteInfo<?> routeInfo;
        /**
         * The executor of a streamed body, {@link #NO_EXECUTOR}, or {@code null} until a body is
         * first streamed: published racily in one reference, a thread that does not see it
         * selects the executor again.
         */
        private @Nullable Object streamExecutor;

        Prepared(RouteExecutor owner, RouteInfo<?> routeInfo) {
            super(owner);
            this.routeInfo = routeInfo;
            ReturnType<?> returnType = routeInfo.getReturnType();
            Argument<?> first = typeVariable(returnType);
            Argument<?> nested = typeVariable(first);
            responseBodyAsyncOrReactive = first.isAsyncOrReactive();
            nestedResponseBodyAsyncOrReactive = nested.isAsyncOrReactive();
            nestedResponseBodyAsync = nested.isAsync();
            declaredSingle = routeInfo.isSpecifiedSingle() || routeInfo.isReactive() && routeInfo.isSingleResult();
            streamOfResponses = HttpResponse.class.isAssignableFrom(first.getType());
            kotlinUnit = routeInfo.isSuspended() && routeInfo instanceof MethodBasedRouteInfo<?, ?> mbri
                && isKotlinFunctionReturnTypeUnit(mbri.getTargetMethod().getExecutableMethod());
            hasExecutor = routeInfo.getExecutor(owner.serverConfiguration.getThreadSelection()) != null;
        }

        @Override
        final boolean responseBodyAsyncOrReactive(RouteInfo<?> routeInfo) {
            return responseBodyAsyncOrReactive;
        }

        @Override
        final boolean nestedResponseBodyAsyncOrReactive(RouteInfo<?> routeInfo) {
            return nestedResponseBodyAsyncOrReactive;
        }

        @Override
        final boolean nestedResponseBodyAsync(RouteInfo<?> routeInfo) {
            return nestedResponseBodyAsync;
        }

        @Override
        final boolean declaredSingle(RouteInfo<?> routeInfo) {
            return declaredSingle;
        }

        @Override
        final boolean streamOfResponses(RouteInfo<?> routeInfo) {
            return streamOfResponses;
        }

        @Override
        final boolean kotlinUnit(RouteInfo<?> routeInfo) {
            return kotlinUnit;
        }

        @Override
        final boolean hasExecutor(RouteInfo<?> routeInfo) {
            return hasExecutor;
        }

        @Override
        final @Nullable ExecutorService streamExecutor(RouteInfo<?> routeInfo) {
            Object selected = streamExecutor;
            if (selected == null) {
                ExecutorService executor = owner.streamExecutor(this.routeInfo);
                streamExecutor = executor == null ? NO_EXECUTOR : executor;
                return executor;
            }
            return selected == NO_EXECUTOR ? null : (ExecutorService) selected;
        }
    }

    /**
     * A route that returns a value of a type that is not a stage or a publisher, e.g. a
     * {@code String}, a bean, or an {@link HttpResponse}, whose body may be a stage or a
     * publisher.
     */
    static final class Imperative extends Prepared {
        Imperative(RouteExecutor owner, RouteInfo<?> routeInfo) {
            super(owner, routeInfo);
        }

        @Override
        ExecutionFlow<MutableHttpResponse<?>> adapt(PropagatedContext propagatedContext, HttpRequest<?> request, Object body, RouteInfo<?> routeInfo) {
            return fromImperativeExecute(propagatedContext, request, routeInfo, body);
        }
    }

    /**
     * A route that returns a {@link CompletionStage}.
     */
    static final class Stage extends Prepared {
        Stage(RouteExecutor owner, RouteInfo<?> routeInfo) {
            super(owner, routeInfo);
        }

        @Override
        ExecutionFlow<MutableHttpResponse<?>> adapt(PropagatedContext propagatedContext, HttpRequest<?> request, Object body, RouteInfo<?> routeInfo) {
            return CompletableFutureExecutionFlow.just(
                fromCompletionStage(request, (CompletionStage<Object>) body, routeInfo)
            );
        }
    }

    /**
     * A route that returns a publisher, or a type that converts to one.
     */
    static final class Reactive extends Prepared {
        Reactive(RouteExecutor owner, RouteInfo<?> routeInfo) {
            super(owner, routeInfo);
        }

        @Override
        ExecutionFlow<MutableHttpResponse<?>> adapt(PropagatedContext propagatedContext, HttpRequest<?> request, Object body, RouteInfo<?> routeInfo) {
            Publisher<Object> publisher = Publishers.convertToPublisher(owner.conversionService, body);
            return owner.fromReactiveExecute(propagatedContext, request, publisher, routeInfo, this);
        }
    }

    /**
     * A Kotlin suspend function: the result may be a publisher, e.g. of a flow.
     */
    static final class Suspend extends Prepared {
        Suspend(RouteExecutor owner, RouteInfo<?> routeInfo) {
            super(owner, routeInfo);
        }

        @Override
        ExecutionFlow<MutableHttpResponse<?>> adapt(PropagatedContext propagatedContext, HttpRequest<?> request, Object body, RouteInfo<?> routeInfo) {
            // special case HttpResponse because FullNettyClientHttpResponse implements Completable...
            if (Publishers.isConvertibleToPublisher(body) && !(body instanceof HttpResponse<?>)) {
                Publisher<Object> publisher = Publishers.convertToPublisher(owner.conversionService, body);
                return owner.fromReactiveExecute(propagatedContext, request, publisher, routeInfo, this);
            }
            return owner.fromKotlinCoroutineExecute(propagatedContext, request, body, routeInfo, this);
        }
    }

    /**
     * A route whose declared return type does not tell the shape of its results, e.g.
     * {@code Object}, and is not a stage, a publisher, a suspend function or a response: the
     * value decides whether it is a publisher.
     */
    static final class PreparedDynamic extends Prepared {
        PreparedDynamic(RouteExecutor owner, RouteInfo<?> routeInfo) {
            super(owner, routeInfo);
        }

        @Override
        ExecutionFlow<MutableHttpResponse<?>> adapt(PropagatedContext propagatedContext, HttpRequest<?> request, Object body, RouteInfo<?> routeInfo) {
            // special case HttpResponse because FullNettyClientHttpResponse implements Completable...
            if (Publishers.isConvertibleToPublisher(body) && !(body instanceof HttpResponse<?>)) {
                Publisher<Object> publisher = Publishers.convertToPublisher(owner.conversionService, body);
                return owner.fromReactiveExecute(propagatedContext, request, publisher, routeInfo, this);
            }
            return fromImperativeExecute(propagatedContext, request, routeInfo, body);
        }
    }
}
