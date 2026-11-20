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

import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.filter.ReactiveFilterChainElement;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.function.Supplier;
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

import static io.micronaut.core.util.KotlinUtils.isKotlinCoroutineSuspended;
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

    /**
     * The value of an empty single-valued publisher, the flows do not carry {@code null}.
     */
    private static final Object EMPTY = new Object();

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

        if (publisherBody(routeInfo, outgoingResponse)) {
            return processPublisherBody(propagatedContext, request, outgoingResponse, routeInfo);
        }
        return ExecutionFlow.just(outgoingResponse);
    }

    /**
     * Whether the body of a response the route returned is a stage or a publisher, which
     * {@link #processPublisherBody} adapts: by the declared type of the body, or, when the route
     * does not declare it and streams on an executor, by the body itself. Otherwise a publisher
     * in the response would stream on the thread that consumes the response, the event loop when
     * the route completed before the request thread waited for it.
     *
     * @param routeInfo The route
     * @param response  The response the route returned
     * @return Whether the body is adapted as a stage or a publisher
     */
    private boolean publisherBody(RouteInfo<?> routeInfo, MutableHttpResponse<?> response) {
        return responseBodyAsyncOrReactive(routeInfo)
            || untypedResponseBodyOnExecutor(routeInfo) && streamedBody(response.body());
    }

    /**
     * Like {@link #publisherBody}, for a response a publisher the route returned emits, e.g. of
     * a {@code Mono<HttpResponse<?>>}.
     *
     * @param routeInfo The route
     * @param response  The response the publisher of the route emitted
     * @return Whether the body is adapted as a stage or a publisher
     */
    private boolean nestedPublisherBody(RouteInfo<?> routeInfo, MutableHttpResponse<?> response) {
        return nestedResponseBodyAsyncOrReactive(routeInfo)
            || untypedNestedResponseBodyOnExecutor(routeInfo) && streamedBody(response.body());
    }

    /**
     * A single value body, e.g. a {@code Mono}, is left to the response writer: only a stream is
     * subscribed to by the writer, and the status and the headers of the response are kept.
     *
     * @param body The body of the response
     * @return Whether the body is a publisher of a stream
     */
    private static boolean streamedBody(@Nullable Object body) {
        return body != null && Publishers.isConvertibleToPublisher(body) && !Publishers.isSingle(body.getClass());
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
                explicitResponse = true;
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

    ExecutionFlow<MutableHttpResponse<?>> fromKotlinCoroutineExecute(PropagatedContext propagatedContext, HttpRequest<?> request,
                                                                     @Nullable Object body, RouteInfo<?> routeInfo) {
        boolean isKotlinFunctionReturnTypeUnit = kotlinUnit(routeInfo);
        if (isKotlinCoroutineSuspended(body)) {
            final Supplier<CompletableFuture<?>> supplier = ContinuationArgumentBinder.extractContinuationCompletableFutureSupplier(request);
            if (supplier == null) {
                return ExecutionFlow.error(new IllegalStateException("Missing coroutine continuation for suspended route"));
            }
            boolean notFoundOnMissingBody = owner.serverConfiguration.isNotFoundOnMissingBody();
            // the result is wrapped so that an empty (null) result still reaches the transformer
            CompletionStage<Optional<Object>> completion = ((CompletableFuture<Object>) supplier.get()).thenApply(Optional::ofNullable);
            return CompletableFutureExecutionFlow.just(completion).flatMap(optional -> {
                Object obj = optional.orElse(null);
                if (obj == null) {
                    return notFoundOnMissingBody ? ExecutionFlow.just(notFoundErrorResponse(request)) : ExecutionFlow.empty();
                }
                MutableHttpResponse<?> response;
                if (obj instanceof HttpResponse<?> httpResponse) {
                    response = httpResponse.toMutableResponse();
                    if (publisherBody(routeInfo, response)) {
                        return processPublisherBody(propagatedContext, request, response, routeInfo);
                    }
                } else {
                    response = owner.forStatus(routeInfo, null);
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

    ExecutionFlow<MutableHttpResponse<?>> fromReactiveExecute(PropagatedContext propagatedContext,
                                                              HttpRequest<?> request,
                                                              Publisher<Object> publisher,
                                                              RouteInfo<?> routeInfo) {
        boolean isSingle = declaredSingle(routeInfo) || Publishers.isSingle(publisher.getClass());
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
                        // Mono<HttpResponse<Mono<T>>>
                        if (nestedPublisherBody(routeInfo, singleResponse)) {
                            return processPublisherBody(propagatedContext, request, singleResponse, routeInfo);
                        }
                    } else if (o instanceof HttpStatus status) {
                        singleResponse = owner.forStatus(routeInfo, status);
                    } else {
                        singleResponse = owner.forStatus(routeInfo, null)
                            .body(o);
                    }
                    return ExecutionFlow.just(singleResponse);
                });
        }
        // streaming case
        if (streamOfResponses(routeInfo)) {
            // a response stream
            Publisher<HttpResponse<?>> bodyPublisher = (Publisher) publisher;
            Flux<MutableHttpResponse<?>> response = Flux.from(bodyPublisher)
                .map(HttpResponse::toMutableResponse);
            if (nestedResponseBodyAsyncOrReactive(routeInfo) || untypedNestedResponseBodyOnExecutor(routeInfo)) {
                response = response.flatMap(resp -> nestedPublisherBody(routeInfo, resp)
                    ? ReactiveExecutionFlow.toPublisher(processPublisherBody(propagatedContext, request, resp, routeInfo))
                    : Mono.just(resp));
            }
            // the outer publisher sees the request and the propagated context, whether or not the
            // bodies of its responses are reactive
            response = response.contextWrite(context -> ReactorPropagation.addPropagatedContext(context, propagatedContext).put(ServerRequestContext.KEY, request));
            return ReactiveExecutionFlow.fromPublisher(ReactivePropagation.propagate(propagatedContext, response));
        }
        // a streamed response keeps the bodies of the route until its stream ends
        return processPublisherBody(propagatedContext, request, owner.forStatus(routeInfo, null), false, RouteExecutor.releaseWhenStreamEnds(request, publisher), routeInfo);
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
    ExecutionFlow<Object> subscribeSingle(PropagatedContext propagatedContext, HttpRequest<?> request, Publisher<Object> publisher,
                                                  RouteInfo<?> routeInfo) {
        if (publisher instanceof Fuseable.ScalarCallable<?>) {
            // Mono.just, Mono.empty, Mono.error: nothing observes the context
            return ReactiveExecutionFlow.fromPublisherEager(publisher, propagatedContext)
                .map(o -> o == null ? EMPTY : o);
        }
        if (ReactiveFilterChainElement.isPresent(propagatedContext) || hasExecutor(routeInfo)) {
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

    ExecutionFlow<MutableHttpResponse<?>> processPublisherBody(PropagatedContext propagatedContext,
                                                                       HttpRequest<?> request,
                                                                       MutableHttpResponse<?> response,
                                                                       RouteInfo<?> routeInfo) {
        Object body = response.body();
        if (body == null) {
            return ExecutionFlow.just(response);
        }
        Publisher<Object> bodyPublisher = Publishers.convertToPublisher(owner.conversionService, body);
        boolean isSinglePublisher = Publishers.isSingle(body.getClass());
        if (!isSinglePublisher) {
            // a streamed response keeps the bodies of the route until its stream ends
            bodyPublisher = RouteExecutor.releaseWhenStreamEnds(request, bodyPublisher);
        }
        return processPublisherBody(propagatedContext, request, response, isSinglePublisher, bodyPublisher, routeInfo);
    }

    ExecutionFlow<MutableHttpResponse<?>> processPublisherBody(PropagatedContext propagatedContext,
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
        MediaType mediaType = response.getContentType().orElseGet(() -> owner.resolveDefaultResponseContentType(request, routeInfo));

        // the streaming body is subscribed to by the response writer: the request and the
        // propagated context are in its Reactor context, and the propagated context is bound for
        // the subscription and the signals
        Flux<Object> streamingBody = owner.applyExecutorToPublisher(
            bodyPublisher,
            streamExecutor(routeInfo),
            propagatedContext
        ).contextWrite(cv -> ReactorPropagation.addPropagatedContext(cv, propagatedContext).put(ServerRequestContext.KEY, request));

        return ExecutionFlow.just(response
            .contentType(mediaType)
            .body(ReactivePropagation.propagate(propagatedContext, streamingBody)));
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
     * @return Whether the route does not declare the type of the body of the response it
     * returns, e.g. {@code HttpResponse<?>} or the response of a handler route, and a streamed
     * body of the route has an executor, see {@link #streamExecutor}
     */
    boolean untypedResponseBodyOnExecutor(RouteInfo<?> routeInfo) {
        return typeVariable(routeInfo.getReturnType()).getType() == Object.class && streamExecutor(routeInfo) != null;
    }

    /**
     * @param routeInfo The route
     * @return Whether the route does not declare the type of the body of the responses the
     * publisher it returns emits, e.g. {@code Mono<HttpResponse<?>>}, and a streamed body of the
     * route has an executor
     */
    boolean untypedNestedResponseBodyOnExecutor(RouteInfo<?> routeInfo) {
        return typeVariable(typeVariable(routeInfo.getReturnType())).getType() == Object.class && streamExecutor(routeInfo) != null;
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
                return fromReactiveExecute(propagatedContext, request, publisher, routeInfo);
            }
            if (routeInfo.isSuspended()) {
                return fromKotlinCoroutineExecute(propagatedContext, request, body, routeInfo);
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

        final boolean preparedResponseBodyAsyncOrReactive;
        final boolean preparedNestedResponseBodyAsyncOrReactive;
        final boolean preparedNestedResponseBodyAsync;
        final boolean preparedDeclaredSingle;
        final boolean preparedStreamOfResponses;
        final boolean preparedKotlinUnit;
        final boolean preparedHasExecutor;
        final boolean preparedUntypedResponseBody;
        final boolean preparedUntypedNestedResponseBody;
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
            preparedResponseBodyAsyncOrReactive = first.isAsyncOrReactive();
            preparedNestedResponseBodyAsyncOrReactive = nested.isAsyncOrReactive();
            preparedNestedResponseBodyAsync = nested.isAsync();
            preparedDeclaredSingle = routeInfo.isSpecifiedSingle() || routeInfo.isReactive() && routeInfo.isSingleResult();
            preparedStreamOfResponses = HttpResponse.class.isAssignableFrom(first.getType());
            preparedKotlinUnit = routeInfo.isSuspended() && routeInfo instanceof MethodBasedRouteInfo<?, ?> mbri
                && isKotlinFunctionReturnTypeUnit(mbri.getTargetMethod().getExecutableMethod());
            preparedHasExecutor = routeInfo.getExecutor(owner.serverConfiguration.getThreadSelection()) != null;
            preparedUntypedResponseBody = first.getType() == Object.class;
            preparedUntypedNestedResponseBody = nested.getType() == Object.class;
        }

        @Override
        final boolean responseBodyAsyncOrReactive(RouteInfo<?> routeInfo) {
            return preparedResponseBodyAsyncOrReactive;
        }

        @Override
        final boolean nestedResponseBodyAsyncOrReactive(RouteInfo<?> routeInfo) {
            return preparedNestedResponseBodyAsyncOrReactive;
        }

        @Override
        final boolean nestedResponseBodyAsync(RouteInfo<?> routeInfo) {
            return preparedNestedResponseBodyAsync;
        }

        @Override
        final boolean declaredSingle(RouteInfo<?> routeInfo) {
            return preparedDeclaredSingle;
        }

        @Override
        final boolean streamOfResponses(RouteInfo<?> routeInfo) {
            return preparedStreamOfResponses;
        }

        @Override
        final boolean kotlinUnit(RouteInfo<?> routeInfo) {
            return preparedKotlinUnit;
        }

        @Override
        final boolean hasExecutor(RouteInfo<?> routeInfo) {
            return preparedHasExecutor;
        }

        @Override
        final boolean untypedResponseBodyOnExecutor(RouteInfo<?> routeInfo) {
            // the stream executor is selected once, when it is first needed
            return preparedUntypedResponseBody && streamExecutor(routeInfo) != null;
        }

        @Override
        final boolean untypedNestedResponseBodyOnExecutor(RouteInfo<?> routeInfo) {
            return preparedUntypedNestedResponseBody && streamExecutor(routeInfo) != null;
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
            return fromReactiveExecute(propagatedContext, request, publisher, routeInfo);
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
                return fromReactiveExecute(propagatedContext, request, publisher, routeInfo);
            }
            return fromKotlinCoroutineExecute(propagatedContext, request, body, routeInfo);
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
                return fromReactiveExecute(propagatedContext, request, publisher, routeInfo);
            }
            return fromImperativeExecute(propagatedContext, request, routeInfo, body);
        }
    }
}
