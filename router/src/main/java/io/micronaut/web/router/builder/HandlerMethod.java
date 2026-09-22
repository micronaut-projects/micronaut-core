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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormParts;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
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
    private static final Argument<FormData> FORM = Argument.of(FormData.class, "form");
    private static final Argument<FormParts> FORM_PARTS = Argument.of(FormParts.class, "parts");

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

    private final Object handler;
    private final Class<?> handlerType;
    private final Supplier<Method> method;
    private final Argument<?>[] arguments;
    private final ReturnType<R> returnType;
    private final Invoker<R> invoker;

    private HandlerMethod(Object handler, Class<?> handlerType, Class<?>[] parameterTypes, Argument<?>[] arguments, ReturnType<R> returnType, Invoker<R> invoker) {
        this.handler = handler;
        this.handlerType = handlerType;
        // looked up only if asked for: routing never needs the method itself
        this.method = SupplierUtil.memoized(() -> ReflectionUtils.getRequiredMethod(handlerType, HANDLE, parameterTypes));
        this.arguments = arguments;
        this.returnType = returnType;
        this.invoker = invoker;
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
     * @param bodyType The body type, which the handler binds like a {@code @Body} argument
     * @param handler  The handler
     * @param <B>      The body type
     * @return The method that calls it
     */
    @SuppressWarnings("unchecked")
    public static <B> HandlerMethod<CompletionStage<? extends HttpResponse<?>>> of(Argument<B> bodyType, AsyncBodyRequestHandler<B> handler) {
        return new HandlerMethod<>(
            handler,
            AsyncBodyRequestHandler.class,
            new Class<?>[]{HttpRequest.class, PathVariables.class, Object.class},
            new Argument<?>[]{REQUEST, PATH_VARIABLES, Argument.of(bodyType.getType(), BODY_ARGUMENT, BODY, bodyType.getTypeParameters())},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1], (B) args[2])
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
            new Class<?>[]{HttpRequest.class, PathVariables.class},
            new Argument<?>[]{REQUEST, PATH_VARIABLES},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1])
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
        return new HandlerMethod<>(
            handler,
            BodyRequestHandler.class,
            new Class<?>[]{HttpRequest.class, PathVariables.class, Object.class},
            new Argument<?>[]{REQUEST, PATH_VARIABLES, Argument.of(bodyType.getType(), BODY_ARGUMENT, BODY, bodyType.getTypeParameters())},
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
     * @param handler The handler
     * @return The method that calls it
     */
    public static HandlerMethod<CompletionStage<? extends HttpResponse<?>>> of(AsyncFormRequestHandler handler) {
        return new HandlerMethod<>(
            handler,
            AsyncFormRequestHandler.class,
            new Class<?>[]{HttpRequest.class, PathVariables.class, FormData.class},
            new Argument<?>[]{REQUEST, PATH_VARIABLES, FORM},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1], (FormData) args[2])
        );
    }

    /**
     * @param handler The handler
     * @return The method that calls it
     */
    public static HandlerMethod<CompletionStage<? extends HttpResponse<?>>> of(StreamingFormRequestHandler handler) {
        return new HandlerMethod<>(
            handler,
            StreamingFormRequestHandler.class,
            new Class<?>[]{HttpRequest.class, PathVariables.class, FormParts.class},
            new Argument<?>[]{REQUEST, PATH_VARIABLES, FORM_PARTS},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> closeWhenDone((FormParts) args[2], () -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1], (FormParts) args[2]))
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
        return method.get();
    }

    @Override
    public ReturnType<R> getReturnType() {
        return returnType;
    }

    @Override
    public Argument<?>[] getArguments() {
        return arguments;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Object> getDeclaringType() {
        return (Class<Object>) handler.getClass();
    }

    @Override
    public String getMethodName() {
        return HANDLE;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    @Override
    public String toString() {
        return "handler " + handler;
    }

    /**
     * Close the form parts when the handler completes, throwing out what it did not read. The
     * result of the handler is delivered once the parts were closed: a failure to release them
     * fails a successful result, and is added as suppressed to a failure of the handler.
     *
     * @param parts   The form parts
     * @param handler Calls the handler
     * @return The stage of the handler
     * @throws Exception If the handler fails
     */
    private static CompletionStage<? extends HttpResponse<?>> closeWhenDone(FormParts parts, Callable<CompletionStage<? extends HttpResponse<?>>> handler) throws Exception {
        CompletionStage<? extends HttpResponse<?>> stage;
        try {
            stage = handler.call();
        } catch (Exception e) {
            parts.close();
            throw e;
        }
        if (stage == null) {
            parts.close();
            throw new NullPointerException("The form handler returned no stage");
        }
        CompletableFuture<HttpResponse<?>> result = new CompletableFuture<>();
        stage.whenComplete((response, error) -> {
            CompletionStage<Void> closed;
            try {
                closed = parts.closeAsync();
            } catch (Throwable e) {
                closed = CompletableFuture.failedFuture(e);
            }
            closed.whenComplete((ignored, closeError) -> {
                if (error != null) {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    if (closeError != null && closeError != cause) {
                        cause.addSuppressed(closeError);
                    }
                    result.completeExceptionally(cause);
                } else if (closeError != null) {
                    result.completeExceptionally(closeError instanceof CompletionException && closeError.getCause() != null ? closeError.getCause() : closeError);
                } else {
                    result.complete(response);
                }
            });
        });
        return result;
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
