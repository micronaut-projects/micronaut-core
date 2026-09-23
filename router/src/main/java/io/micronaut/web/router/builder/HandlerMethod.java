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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.AsyncServerHttpRequest;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.web.router.RouteLocator;
import io.micronaut.http.form.FormData;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * A route handler function presented as an executable method, so that a route to it binds
 * arguments, selects an executor, runs filters and encodes the result exactly like a route to a
 * controller method.
 *
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class HandlerMethod<R> implements ExecutableMethod<Object, R>, MethodExecutionHandle<Object, R> {

    /**
     * The name of the body argument of a {@link BodyRequestHandler}.
     */
    static final String BODY_ARGUMENT = "body";

    /**
     * The name of the method of every handler interface.
     */
    private static final String HANDLE = "handle";

    private static final Argument<HttpRequest> REQUEST = Argument.of(HttpRequest.class, "request");
    private static final Argument<PathVariables> PATH_VARIABLES = Argument.of(PathVariables.class, "pathVariables");
    private static final Argument<AsyncServerHttpRequest> ASYNC_REQUEST = Argument.of(AsyncServerHttpRequest.class, "request");
    private static final Argument<FormData> FORM = Argument.of(FormData.class, "form");

    /**
     * The metadata of a {@code @Body} parameter, which selects the body binder.
     */
    private static final AnnotationMetadata BODY = new DefaultAnnotationMetadata(
        Map.of(Body.class.getName(), Map.of()),
        Map.of(Bindable.class.getName(), Map.of()),
        Map.of(Bindable.class.getName(), Map.of()),
        Map.of(Body.class.getName(), Map.of()),
        Map.of(Bindable.class.getName(), List.of(Body.class.getName())),
        false
    );

    /**
     * The metadata of a nullable {@code @Body} parameter: a request without a body is handled
     * with {@code null}.
     */
    private static final AnnotationMetadata NULLABLE = new DefaultAnnotationMetadata(
        Map.of(AnnotationUtil.NULLABLE, Map.of()),
        Map.of(),
        Map.of(),
        Map.of(AnnotationUtil.NULLABLE, Map.of()),
        Map.of(),
        false
    );

    private static final AnnotationMetadata NULLABLE_BODY = new DefaultAnnotationMetadata(
        Map.of(Body.class.getName(), Map.of(), AnnotationUtil.NULLABLE, Map.of()),
        Map.of(Bindable.class.getName(), Map.of()),
        Map.of(Bindable.class.getName(), Map.of()),
        Map.of(Body.class.getName(), Map.of(), AnnotationUtil.NULLABLE, Map.of()),
        Map.of(Bindable.class.getName(), List.of(Body.class.getName())),
        false
    );

    private final Object handler;
    private final Class<?> handlerType;
    private final Supplier<Method> method;
    private final Argument<?>[] arguments;
    private ReturnType<R> returnType;
    private final Invoker<R> invoker;
    /**
     * The annotations the route has whatever annotations it is given, see {@link #webSocket}.
     */
    private final AnnotationMetadata baseAnnotationMetadata;
    private AnnotationMetadata annotationMetadata;
    private @Nullable AnnotationMetadataProvider annotationMetadataProvider;
    private @Nullable ReturnType<R> annotatedReturnType;

    private HandlerMethod(Object handler, Class<?> handlerType, Class<?>[] parameterTypes, Argument<?>[] arguments, ReturnType<R> returnType, Invoker<R> invoker) {
        // looked up only if asked for: routing never needs the method itself
        this(handler, handlerType, SupplierUtil.memoized(() -> ReflectionUtils.getRequiredMethod(handlerType, HANDLE, parameterTypes)),
            arguments, returnType, invoker, AnnotationMetadata.EMPTY_METADATA);
    }

    private HandlerMethod(Object handler, Class<?> handlerType, Supplier<Method> method, Argument<?>[] arguments, ReturnType<R> returnType,
                          Invoker<R> invoker, AnnotationMetadata baseAnnotationMetadata) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.handlerType = handlerType;
        this.method = method;
        this.arguments = arguments;
        this.returnType = returnType;
        this.invoker = invoker;
        this.baseAnnotationMetadata = baseAnnotationMetadata;
        this.annotationMetadata = baseAnnotationMetadata;
    }

    /**
     * @param handler The handler
     * @return The method that calls it
     */
    public static HandlerMethod<HttpResponse<?>> of(RequestHandler handler) {
        return new HandlerMethod<>(
            handler,
            RequestHandler.class,
            new Class<?>[]{HttpRequest.class, PathVariables.class},
            new Argument<?>[]{REQUEST, PATH_VARIABLES},
            returnType(HttpResponse.class, Argument.OBJECT_ARGUMENT),
            args -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1])
        );
    }

    /**
     * @param handler The handler
     * @return The method that calls it
     */
    public static HandlerMethod<CompletionStage<? extends HttpResponse<?>>> of(AsyncRequestHandler handler) {
        return new HandlerMethod<>(
            handler,
            AsyncRequestHandler.class,
            new Class<?>[]{AsyncServerHttpRequest.class, PathVariables.class},
            // the request reads the body for the handler: no binder decodes it
            new Argument<?>[]{ASYNC_REQUEST, PATH_VARIABLES},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> releaseWhenDone((AsyncServerHttpRequest<?>) args[0], () -> handler.handle((AsyncServerHttpRequest<?>) args[0], (PathVariables) args[1]))
        );
    }

    /**
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The method that calls it
     */
    @SuppressWarnings("unchecked")
    public static <B> HandlerMethod<HttpResponse<?>> of(Argument<B> bodyType, BodyRequestHandler<B> handler) {
        Objects.requireNonNull(bodyType, "bodyType");
        return new HandlerMethod<>(
            handler,
            BodyRequestHandler.class,
            new Class<?>[]{HttpRequest.class, PathVariables.class, Object.class},
            new Argument<?>[]{REQUEST, PATH_VARIABLES, bodyArgument(bodyType)},
            returnType(HttpResponse.class, Argument.OBJECT_ARGUMENT),
            args -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1], (B) args[2])
        );
    }

    /**
     * @param handler The handler
     * @return The method that calls it
     */
    public static HandlerMethod<HttpResponse<?>> of(FormRequestHandler handler) {
        return new HandlerMethod<>(
            handler,
            FormRequestHandler.class,
            new Class<?>[]{HttpRequest.class, PathVariables.class, FormData.class},
            new Argument<?>[]{REQUEST, PATH_VARIABLES, FORM},
            returnType(HttpResponse.class, Argument.OBJECT_ARGUMENT),
            args -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1], (FormData) args[2])
        );
    }

    /**
     * @param errorType The type of the exception, which the error route binds to the second argument
     * @param handler   The handler
     * @param <E>       The type of the exception
     * @return The method that calls it
     */
    @SuppressWarnings("unchecked")
    public static <E extends Throwable> HandlerMethod<HttpResponse<?>> of(Class<E> errorType, ErrorRouteHandler<E> handler) {
        Objects.requireNonNull(errorType, "type");
        return new HandlerMethod<>(
            handler,
            ErrorRouteHandler.class,
            new Class<?>[]{HttpRequest.class, Throwable.class},
            new Argument<?>[]{REQUEST, Argument.of(errorType, "error")},
            returnType(HttpResponse.class, Argument.OBJECT_ARGUMENT),
            args -> handler.handle((HttpRequest<?>) args[0], (E) args[1])
        );
    }

    /**
     * @param errorType The type of the exception, which the error route binds to the second argument
     * @param handler   The handler
     * @param <E>       The type of the exception
     * @return The method that calls it
     */
    @SuppressWarnings("unchecked")
    public static <E extends Throwable> HandlerMethod<CompletionStage<? extends HttpResponse<?>>> of(Class<E> errorType, AsyncErrorRouteHandler<E> handler) {
        Objects.requireNonNull(errorType, "type");
        return new HandlerMethod<>(
            handler,
            AsyncErrorRouteHandler.class,
            new Class<?>[]{HttpRequest.class, Throwable.class},
            new Argument<?>[]{REQUEST, Argument.of(errorType, "error")},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> stage(handler.handle((HttpRequest<?>) args[0], (E) args[1]))
        );
    }

    /**
     * @param handler The handler
     * @return The method that calls it
     */
    public static HandlerMethod<CompletionStage<? extends HttpResponse<?>>> of(AsyncStatusRouteHandler handler) {
        return new HandlerMethod<>(
            handler,
            AsyncStatusRouteHandler.class,
            new Class<?>[]{HttpRequest.class},
            new Argument<?>[]{REQUEST},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> stage(handler.handle((HttpRequest<?>) args[0]))
        );
    }

    /**
     * @param handler The handler
     * @return The method that calls it
     */
    public static HandlerMethod<HttpResponse<?>> of(StatusRouteHandler handler) {
        return new HandlerMethod<>(
            handler,
            StatusRouteHandler.class,
            new Class<?>[]{HttpRequest.class},
            new Argument<?>[]{REQUEST},
            returnType(HttpResponse.class, Argument.OBJECT_ARGUMENT),
            args -> handler.handle((HttpRequest<?>) args[0])
        );
    }

    /**
     * The target of a WebSocket route: the server upgrades a request to the route and hands the
     * connection to the endpoint, so the method is never invoked. The route has the given
     * annotations, which make it a WebSocket route, whatever annotations it is given too.
     *
     * @param endpoint         The endpoint of the route
     * @param endpointMetadata The annotations of a WebSocket route
     * @return The method
     */
    public static HandlerMethod<CompletionStage<Object>> webSocket(Object endpoint, AnnotationMetadata endpointMetadata) {
        return new HandlerMethod<>(
            endpoint,
            endpoint.getClass(),
            () -> {
                throw new UnsupportedOperationException("The WebSocket route " + endpoint + " has handler functions, not a method");
            },
            new Argument<?>[0],
            // the executor of the route runs the handlers of the endpoint, which may return a stage
            returnType(CompletionStage.class, Argument.OBJECT_ARGUMENT),
            args -> {
                throw new IllegalStateException("The server upgrades a request to the WebSocket route " + endpoint + ", and hands the connection to its handlers");
            },
            Objects.requireNonNull(endpointMetadata, "endpointMetadata")
        );
    }

    /**
     * The target of a locator route: the router resolves the route to a route of the located
     * target, so the method is never invoked.
     *
     * @param locator The locator
     * @return The method
     */
    public static HandlerMethod<Object> of(RouteLocator locator) {
        return new HandlerMethod<>(
            locator,
            RouteLocator.class,
            new Class<?>[0],
            new Argument<?>[0],
            returnType(Object.class),
            args -> {
                throw new IllegalStateException("The router resolves a locator route to a route of the located target: " + locator);
            }
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <R> ReturnType<R> returnType(Class<?> type, Argument<?>... typeArguments) {
        return (ReturnType<R>) ReturnType.of((Class) type, typeArguments);
    }

    @Override
    public Object getTarget() {
        return handler;
    }

    @Override
    public ExecutableMethod<Object, R> getExecutableMethod() {
        return this;
    }

    @Override
    public R invoke(@Nullable Object... arguments) {
        try {
            return invoker.invoke(arguments);
        } catch (Exception e) {
            // like a controller method: the error routes see the exception the handler threw
            return ExceptionUtils.sneakyThrow(e);
        }
    }

    @Override
    public R invoke(Object instance, @Nullable Object... arguments) {
        return invoke(arguments);
    }

    @Override
    public Method getTargetMethod() {
        ExecutableMethod<?, ?> target = implemented();
        return target == null ? method.get() : target.getTargetMethod();
    }

    @Override
    public ReturnType<R> getReturnType() {
        ReturnType<R> annotated = annotatedReturnType;
        return annotated == null ? returnType : annotated;
    }

    @Override
    public Argument<?>[] getArguments() {
        return arguments;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Object> getDeclaringType() {
        ExecutableMethod<?, ?> target = implemented();
        return (Class<Object>) (target == null ? handler.getClass() : target.getDeclaringType());
    }

    @Override
    public String getMethodName() {
        ExecutableMethod<?, ?> target = implemented();
        return target == null ? HANDLE : target.getMethodName();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    /**
     * Give the route to the handler the annotations of an element, see
     * {@link HttpRouteSpec#annotationMetadata}: an {@link ExecutableMethod} is the bean method the
     * route implements, whose target method, declaring type and method name the route has too.
     *
     * @param annotationMetadata The annotated element
     */
    @Internal
    public void annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        Objects.requireNonNull(annotationMetadata, "annotationMetadata");
        AnnotationMetadata metadata = Objects.requireNonNull(annotationMetadata.getAnnotationMetadata(), "annotationMetadata");
        AnnotationMetadata base = baseAnnotationMetadata;
        AnnotationMetadata effective = base == AnnotationMetadata.EMPTY_METADATA
            ? metadata
            // the given annotations override the ones of the base
            : new AnnotationMetadataHierarchy(base, metadata);
        this.annotationMetadataProvider = annotationMetadata;
        this.annotationMetadata = effective;
        // like the return type of a method, it has the annotations of the method
        this.annotatedReturnType = new AnnotatedReturnType<>(returnType, effective);
    }

    /**
     * The element the route to the handler has the annotations of, see
     * {@link HttpRouteSpec#annotationMetadata}.
     *
     * @return The element, an {@link ExecutableMethod} if the route implements a bean method, or
     * {@code null} if the route was given no annotations
     */
    @Internal
    public @Nullable AnnotationMetadataProvider getAnnotationMetadataProvider() {
        return annotationMetadataProvider;
    }

    /**
     * Declare the type of the body of the response of the handler, see
     * {@link HttpRouteSpec#responseType(Argument)}: the return type of the handler becomes
     * {@code HttpResponse<R>}, or {@code CompletionStage<HttpResponse<R>>} for a handler that
     * completes the response later, like the return type of a controller method.
     *
     * @param responseType The type of the body of the response
     */
    @Internal
    public void responseType(Argument<?> responseType) {
        Objects.requireNonNull(responseType, "responseType");
        Class<?> type = returnType.getType();
        if (type == CompletionStage.class) {
            returnType = returnType(CompletionStage.class, Argument.of(HttpResponse.class, responseType));
        } else if (type == HttpResponse.class) {
            returnType = returnType(HttpResponse.class, responseType);
        } else {
            throw new IllegalStateException("The handler has no response body type: " + this);
        }
        if (annotatedReturnType != null) {
            annotatedReturnType = new AnnotatedReturnType<>(returnType, annotationMetadata);
        }
    }

    /**
     * @return The bean method the route implements: the element of its annotations, if it is a method
     */
    private @Nullable ExecutableMethod<?, ?> implemented() {
        return annotationMetadataProvider instanceof ExecutableMethod<?, ?> method ? method : null;
    }

    /**
     * A body type that is {@code null} when the request has no body.
     *
     * @param bodyType The body type
     * @param <T>      The type
     * @return The nullable type, with the annotations of the given one
     */
    @Internal
    public static <T> Argument<T> nullable(Argument<T> bodyType) {
        if (bodyType.isNullable()) {
            return bodyType;
        }
        return Argument.of(bodyType.getType(), bodyType.getName(),
            new AnnotationMetadataHierarchy(bodyType.getAnnotationMetadata(), NULLABLE), bodyType.getTypeParameters());
    }

    /**
     * The body argument of a handler, bound like a {@code @Body} argument: annotated
     * {@code @Body}, and {@code @Nullable} if the type is nullable.
     *
     * @param bodyType The body type
     * @param <T>      The type
     * @return The argument
     */
    @Internal
    public static <T> Argument<T> bodyArgument(Argument<T> bodyType) {
        return Argument.of(bodyType.getType(), BODY_ARGUMENT, bodyMetadata(bodyType), bodyType.getTypeParameters());
    }

    /**
     * The metadata of the body argument of a handler.
     *
     * @param bodyType The body type
     * @return {@code @Body}, and {@code @Nullable} if the type is nullable
     */
    private static AnnotationMetadata bodyMetadata(Argument<?> bodyType) {
        return bodyType.isNullable() ? NULLABLE_BODY : BODY;
    }

    /**
     * Describes the handler for the messages that name a route, e.g.
     * {@code RequestHandler lambda in ItemRoutes}: the class of a lambda is a generated name, which
     * does not identify it. A handler route that implements a bean method is that method.
     *
     * @return The description of the handler
     */
    @Override
    public String toString() {
        ExecutableMethod<?, ?> target = implemented();
        if (target != null) {
            return withoutPackage(target.getDeclaringType().getName()) + '#' + target.getMethodName();
        }
        if (handlerType == RouteLocator.class) {
            return "locator " + handler;
        }
        String name = handler.getClass().getName();
        int lambda = name.indexOf("$$Lambda");
        String description = lambda < 0
            ? withoutPackage(name)
            : "lambda in " + withoutPackage(name.substring(0, lambda));
        return withoutPackage(handlerType.getName()) + ' ' + description;
    }

    private static String withoutPackage(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /**
     * Release what the handler's read of the body left open when the handler completes, e.g. the
     * parts of a form it did not read. The result of the handler is delivered once that was
     * released: a failure to release fails a successful result, and is added as suppressed to a
     * failure of the handler.
     *
     * @param request The request of the handler
     * @param handler Calls the handler
     * @return The stage of the handler
     * @throws Exception If the handler fails
     */
    private static CompletionStage<? extends HttpResponse<?>> releaseWhenDone(AsyncServerHttpRequest<?> request,
                                                                             Callable<CompletionStage<? extends HttpResponse<?>>> handler) throws Exception {
        AsyncHandlerRequest handlerRequest = request instanceof AsyncHandlerRequest r ? r : null;
        CompletionStage<? extends HttpResponse<?>> stage;
        try {
            stage = handler.call();
        } catch (Throwable e) {
            // an Error too: the body is released now, not when the request ends
            if (handlerRequest != null) {
                release(handlerRequest, e);
            }
            throw e;
        }
        if (stage == null) {
            NullPointerException noStage = new NullPointerException("The asynchronous handler returned no stage");
            if (handlerRequest != null) {
                release(handlerRequest, noStage);
            }
            throw noStage;
        }
        if (handlerRequest == null) {
            return stage;
        }
        CompletableFuture<HttpResponse<?>> result = new CompletableFuture<>();
        stage.whenComplete((response, error) -> {
            CompletionStage<Void> released;
            try {
                released = handlerRequest.releaseBody();
            } catch (Throwable e) {
                released = CompletableFuture.failedFuture(e);
            }
            released.whenComplete((ignored, releaseError) -> {
                if (error != null) {
                    Throwable cause = unwrap(error);
                    if (releaseError != null && releaseError != cause) {
                        cause.addSuppressed(releaseError);
                    }
                    result.completeExceptionally(cause);
                } else if (releaseError != null) {
                    result.completeExceptionally(unwrap(releaseError));
                } else {
                    result.complete(response);
                }
            });
        });
        return result;
    }

    /**
     * Release the body when the handler failed without a stage: a failure to release is added as
     * suppressed to the failure of the handler, which it does not replace.
     *
     * @param request The request of the handler
     * @param failure The failure of the handler
     */
    private static void release(AsyncHandlerRequest request, Throwable failure) {
        try {
            request.releaseBody();
        } catch (Throwable releaseError) {
            // a Throwable is equal to itself only: a failure cannot suppress itself
            if (!releaseError.equals(failure)) {
                failure.addSuppressed(releaseError);
            }
        }
    }

    /**
     * The stage of an asynchronous error or status handler: an error route that answers with no
     * stage fails, like one that throws, instead of answering {@code 404} or {@code 204}.
     *
     * @param stage The stage the handler returned
     * @return The stage
     */
    private static CompletionStage<? extends HttpResponse<?>> stage(@Nullable CompletionStage<? extends HttpResponse<?>> stage) {
        if (stage == null) {
            throw new NullPointerException("The asynchronous error or status handler returned no stage");
        }
        return stage;
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /**
     * The return type of a handler route that was given annotations.
     *
     * @param returnType         The return type of the handler
     * @param annotationMetadata The annotations of the route
     * @param <R>                The type
     */
    private record AnnotatedReturnType<R>(ReturnType<R> returnType, AnnotationMetadata annotationMetadata) implements ReturnType<R> {

        @Override
        public Class<R> getType() {
            return returnType.getType();
        }

        @Override
        public Argument<?>[] getTypeParameters() {
            return returnType.getTypeParameters();
        }

        @Override
        public Map<String, Argument<?>> getTypeVariables() {
            return returnType.getTypeVariables();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        @Override
        public Argument<R> asArgument() {
            return Argument.of(getType(), annotationMetadata, getTypeParameters());
        }
    }

    /**
     * Calls the handler.
     *
     * @param <R> The result type
     */
    @FunctionalInterface
    private interface Invoker<R> {
        R invoke(@Nullable Object[] arguments) throws Exception;
    }

}
