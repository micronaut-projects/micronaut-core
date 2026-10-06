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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.ChunkSource;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.web.router.RouteLocator;
import io.micronaut.http.form.FormData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
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
    private static final Logger LOG = LoggerFactory.getLogger(HandlerMethod.class);
    private static final Argument<PathVariables> PATH_VARIABLES = Argument.of(PathVariables.class, "pathVariables");
    private static final Argument<AsyncRequestBody> ASYNC_BODY = Argument.of(AsyncRequestBody.class, BODY_ARGUMENT);
    private static final Argument<FormData> FORM = Argument.of(FormData.class, "form");
    private static final Argument<SseResponder> SSE_RESPONDER = Argument.of(SseResponder.class, "events");

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
    private final Argument<?>[] arguments;
    private ReturnType<R> returnType;
    private final Invoker<R> invoker;
    private AnnotationMetadata annotationMetadata = AnnotationMetadata.EMPTY_METADATA;
    private @Nullable AnnotationMetadataProvider annotationMetadataProvider;
    /**
     * The annotations given to the route itself, see {@link HttpRouteSpec#annotate(AnnotationValue)}.
     */
    private final DefaultRouteAnnotations annotations = new DefaultRouteAnnotations();
    /**
     * The annotations of the innermost group of the route, which has those of the enclosing groups.
     */
    private @Nullable DefaultRouteAnnotations groupAnnotations;
    private @Nullable ReturnType<R> annotatedReturnType;

    private HandlerMethod(Object handler, Class<?> handlerType, Argument<?>[] arguments, ReturnType<R> returnType, Invoker<R> invoker) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.handlerType = handlerType;
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
            new Argument<?>[]{REQUEST, PATH_VARIABLES},
            returnType(HttpResponse.class, Argument.OBJECT_ARGUMENT),
            args -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1])
        );
    }

    /**
     * The method of a route that answers with the response of a supplier, without an argument:
     * nothing is bound from the request.
     *
     * @param response Creates the response
     * @return The method that calls it
     */
    static HandlerMethod<HttpResponse<?>> respond(Supplier<? extends @Nullable HttpResponse<?>> response) {
        return new HandlerMethod<>(
            response,
            Supplier.class,
            new Argument<?>[0],
            returnType(HttpResponse.class, Argument.OBJECT_ARGUMENT),
            args -> response.get()
        );
    }

    /**
     * The method of a route that answers with the response a function creates from the path
     * variables, the only argument bound.
     *
     * @param response Creates the response from the path variables
     * @return The method that calls it
     */
    static HandlerMethod<HttpResponse<?>> respond(Function<? super PathVariables, ? extends @Nullable HttpResponse<?>> response) {
        return new HandlerMethod<>(
            response,
            Function.class,
            new Argument<?>[]{PATH_VARIABLES},
            returnType(HttpResponse.class, Argument.OBJECT_ARGUMENT),
            args -> response.apply((PathVariables) args[0])
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
            // like a RequestHandler: nothing reads the body
            new Argument<?>[]{REQUEST, PATH_VARIABLES},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> handlerStage(handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1]))
        );
    }

    /**
     * The method of an asynchronous handler that receives the body: the {@link AsyncRequestBody}
     * the handler reads itself, which no binder decodes and whose open reads are released when
     * the stage of the handler completes, or the body decoded to a type, bound like a
     * {@code @Body} argument, see {@link #bodyArgument(Argument)}.
     *
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The method that calls it
     */
    @SuppressWarnings("unchecked")
    public static <B> HandlerMethod<CompletionStage<? extends HttpResponse<?>>> ofAsync(Argument<B> bodyType, AsyncBodyRequestHandler<B> handler) {
        Objects.requireNonNull(bodyType, "bodyType");
        if (isAsyncBody(bodyType)) {
            return new HandlerMethod<>(
                handler,
                AsyncBodyRequestHandler.class,
                // the handler reads the body itself: no binder decodes it
                new Argument<?>[]{REQUEST, PATH_VARIABLES, ASYNC_BODY},
                returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
                args -> releaseWhenDone((AsyncRequestBody) args[2], () -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1], (B) args[2]))
            );
        }
        return asyncBody(handler, bodyArgument(bodyType));
    }

    /**
     * The method of an asynchronous handler that receives the whole submitted form.
     *
     * @param handler The handler
     * @return The method that calls it
     */
    static HandlerMethod<CompletionStage<? extends HttpResponse<?>>> formAsync(AsyncBodyRequestHandler<FormData> handler) {
        return asyncBody(handler, FORM);
    }

    @SuppressWarnings("unchecked")
    private static <B> HandlerMethod<CompletionStage<? extends HttpResponse<?>>> asyncBody(AsyncBodyRequestHandler<B> handler, Argument<?> body) {
        return new HandlerMethod<>(
            handler,
            AsyncBodyRequestHandler.class,
            new Argument<?>[]{REQUEST, PATH_VARIABLES, body},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> handlerStage(handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1], (B) args[2]))
        );
    }

    /**
     * @param bodyType The type of the body of a handler
     * @return Whether the handler reads the body itself, as an {@link AsyncRequestBody}
     */
    private static boolean isAsyncBody(Argument<?> bodyType) {
        return bodyType.getType() == AsyncRequestBody.class;
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
            // the body a handler reads itself is bound like the body a controller method declares
            new Argument<?>[]{REQUEST, PATH_VARIABLES, isAsyncBody(bodyType) ? ASYNC_BODY : bodyArgument(bodyType)},
            returnType(HttpResponse.class, Argument.OBJECT_ARGUMENT),
            args -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1], (B) args[2])
        );
    }

    /**
     * The method of a handler that receives the whole submitted form.
     *
     * @param handler The handler
     * @return The method that calls it
     */
    static HandlerMethod<HttpResponse<?>> form(BodyRequestHandler<FormData> handler) {
        return new HandlerMethod<>(
            handler,
            BodyRequestHandler.class,
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
            new Argument<?>[]{REQUEST},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> stage(handler.handle((HttpRequest<?>) args[0]))
        );
    }

    /**
     * The method of a server-sent events route: the server binds the {@link SseResponder}, which
     * creates the emitter, and the result completes with the response when the first event is
     * sent.
     *
     * @param handler The handler
     * @return The method that calls it
     */
    public static HandlerMethod<CompletionStage<? extends HttpResponse<?>>> of(SseHandler handler) {
        return new HandlerMethod<>(
            handler,
            SseHandler.class,
            new Argument<?>[]{REQUEST, PATH_VARIABLES, SSE_RESPONDER},
            returnType(CompletionStage.class, Argument.of(HttpResponse.class, Argument.OBJECT_ARGUMENT)),
            args -> ((SseResponder) args[2]).respond(events -> handler.handle((HttpRequest<?>) args[0], (PathVariables) args[1], events))
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
            new Argument<?>[]{REQUEST},
            returnType(HttpResponse.class, Argument.OBJECT_ARGUMENT),
            args -> handler.handle((HttpRequest<?>) args[0])
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
            new Argument<?>[0],
            returnType(Object.class),
            args -> {
                throw new IllegalStateException("The router resolves a locator route to a route of the located target: " + locator);
            }
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <R> ReturnType<R> returnType(Class<?> type, Argument<?>... typeArguments) {
        return (ReturnType<R>) ReturnType.of(type, typeArguments);
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
    @SuppressWarnings("NullAway") // like the method of a controller, a handler may return null, see RequestHandler
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

    /**
     * A route to a handler function has no Java method: the router, the binding and the
     * execution of the route never need one. Only a route that implements a bean method, see
     * {@link HttpRouteSpec#annotationMetadata}, has the target method of that bean method. That
     * bean method is also the element of the route, see
     * {@link io.micronaut.web.router.MethodBasedRouteInfo#getAnnotationMetadataProvider()}.
     *
     * @return The target method of the bean method the route implements
     * @throws UnsupportedOperationException if the route implements no bean method
     */
    @Override
    public Method getTargetMethod() {
        ExecutableMethod<?, ?> target = implemented();
        if (target == null) {
            throw new UnsupportedOperationException("The route to " + this + " calls a handler function, which has no Java method");
        }
        return target.getTargetMethod();
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
        Objects.requireNonNull(annotationMetadata.getAnnotationMetadata(), "annotationMetadata");
        this.annotationMetadataProvider = annotationMetadata;
        updateAnnotationMetadata();
    }

    /**
     * Give the route to the handler an annotation, see {@link HttpRouteSpec#annotate(AnnotationValue)}.
     *
     * @param annotation The annotation
     */
    @Internal
    public void annotate(AnnotationValue<?> annotation) {
        annotations.add(annotation);
        updateAnnotationMetadata();
    }

    /**
     * The annotations of the groups of the route, which the annotations of the route override,
     * see {@link HttpRouteGroup#annotate(AnnotationValue)}.
     *
     * @param groupAnnotations The annotations of the innermost group, which has those of the enclosing groups
     */
    @Internal
    public void groupAnnotations(DefaultRouteAnnotations groupAnnotations) {
        this.groupAnnotations = Objects.requireNonNull(groupAnnotations, "groupAnnotations");
        updateAnnotationMetadata();
    }

    /**
     * The annotations of the route: of its element, then of its groups, then its own.
     */
    private void updateAnnotationMetadata() {
        AnnotationMetadataProvider provider = elementProvider();
        AnnotationMetadata base = provider == null ? AnnotationMetadata.EMPTY_METADATA : provider.getAnnotationMetadata();
        DefaultRouteAnnotations group = groupAnnotations;
        List<DefaultRouteAnnotations> levels = group == null ? new ArrayList<>(1) : group.levels();
        levels.add(annotations);
        AnnotationMetadata metadata = DefaultRouteAnnotations.layered(base, levels);
        if (provider == null && metadata.isEmpty()) {
            this.annotationMetadata = AnnotationMetadata.EMPTY_METADATA;
            this.annotatedReturnType = null;
            return;
        }
        this.annotationMetadata = metadata;
        // like the return type of a method, it has the annotations of the method
        this.annotatedReturnType = new AnnotatedReturnType<>(returnType, metadata);
    }

    /**
     * The same handler with annotations it inherits, e.g. a located route with the annotations
     * of the groups of its locator routes: the annotations of this method override them, like the
     * annotations of a route override the ones of its groups.
     *
     * @param inherited The inherited annotations
     * @return The method with the annotations of both, or this method if it inherits none
     */
    @Internal
    public HandlerMethod<R> inheriting(AnnotationMetadata inherited) {
        if (inherited.isEmpty()) {
            return this;
        }
        AnnotationMetadata own = annotationMetadata;
        AnnotationMetadata metadata = own.isEmpty() ? inherited : new AnnotationMetadataHierarchy(true, inherited, own);
        HandlerMethod<R> method = new HandlerMethod<>(handler, handlerType, arguments, returnType, invoker);
        method.annotationMetadataProvider = annotationMetadataProvider;
        method.groupAnnotations = groupAnnotations;
        method.annotationMetadata = metadata;
        // like the return type of a method, it has the annotations of the method
        method.annotatedReturnType = new AnnotatedReturnType<>(returnType, metadata);
        return method;
    }

    /**
     * The element the route to the handler has the annotations of, see
     * {@link HttpRouteSpec#annotationMetadata}.
     *
     * @return The element, an {@link ExecutableMethod} if the route implements a bean method, or
     * {@code null} if the route was given no element
     */
    @Internal
    public @Nullable AnnotationMetadataProvider getAnnotationMetadataProvider() {
        return elementProvider();
    }

    /**
     * @return The element of the route, or else the one of its innermost group that has one, see
     * {@link HttpRouteGroup#annotationMetadata}
     */
    private @Nullable AnnotationMetadataProvider elementProvider() {
        AnnotationMetadataProvider provider = annotationMetadataProvider;
        if (provider == null && groupAnnotations != null) {
            return groupAnnotations.element();
        }
        return provider;
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
        return elementProvider() instanceof ExecutableMethod<?, ?> method ? method : null;
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
     * The body argument of a handler, bound like a {@code @Body} argument: annotated with the
     * annotations of the body type, e.g. a {@code @JsonView} the body is decoded with, and
     * {@code @Body}, and {@code @Nullable} if the type is nullable, like a {@code @Body}
     * parameter of a controller.
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
     * @return The annotations of the body type, with {@code @Body}, and {@code @Nullable} if the type is nullable
     */
    private static AnnotationMetadata bodyMetadata(Argument<?> bodyType) {
        AnnotationMetadata body = bodyType.isNullable() ? NULLABLE_BODY : BODY;
        AnnotationMetadata annotations = bodyType.getAnnotationMetadata();
        if (annotations.isEmpty()) {
            return body;
        }
        // both are declared on the argument, as on a parameter of a controller
        return new AnnotationMetadataHierarchy(true, annotations, body);
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
     * @param body    The body of the handler
     * @param handler Calls the handler
     * @return The stage of the handler
     * @throws Exception If the handler fails
     */
    private static CompletionStage<? extends HttpResponse<?>> releaseWhenDone(AsyncRequestBody body,
                                                                             Callable<CompletionStage<? extends HttpResponse<?>>> handler) throws Exception {
        ReleasableRequestBody handlerRequest = body instanceof ReleasableRequestBody b ? b : null;
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
            NullPointerException noStage = noStage();
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
            if (error == null && response instanceof MutableHttpResponse<?> mutable
                && mutable.body() instanceof ChunkSource<?> source) {
                // the elements may be reads of the body: it is released when the source is closed
                mutable.body(releaseWhenClosed(source, handlerRequest));
                result.complete(response);
                return;
            }
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
     * A {@link ChunkSource} body of the response of the handler that releases what the handler's
     * read of the body left open when the server closes it: when the response ends, fails, or the
     * client disconnects. The response is committed by then: a failure to release is logged. A
     * source that is never written, e.g. replaced by a filter, leaves the body to the release
     * when the request ends.
     *
     * @param source  The source
     * @param request The body of the handler
     * @param <T>     The type of an element
     * @return The source that releases the body when it is closed
     */
    private static <T> ChunkSource<T> releaseWhenClosed(ChunkSource<T> source, ReleasableRequestBody request) {
        AtomicBoolean closed = new AtomicBoolean();
        return new ChunkSource<>() {
            @Override
            public CompletionStage<Optional<T>> next() {
                return source.next();
            }

            @Override
            public void close() {
                if (!closed.compareAndSet(false, true)) {
                    return;
                }
                try {
                    source.close();
                } finally {
                    CompletionStage<Void> released;
                    try {
                        released = request.releaseBody();
                    } catch (Throwable e) {
                        released = CompletableFuture.failedFuture(e);
                    }
                    released.whenComplete((ignored, error) -> {
                        if (error != null && LOG.isWarnEnabled()) {
                            LOG.warn("Failed to release what the reads of the body left open when the streamed response of {} ended", source, error);
                        }
                    });
                }
            }
        };
    }

    /**
     * Release the body when the handler failed without a stage: a failure to release is added as
     * suppressed to the failure of the handler, which it does not replace.
     *
     * @param request The body of the handler
     * @param failure The failure of the handler
     */
    private static void release(ReleasableRequestBody request, Throwable failure) {
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
     * The stage of an asynchronous handler that does not read the body: a handler that answers
     * with no stage fails, like one that throws.
     *
     * @param stage The stage the handler returned
     * @return The stage
     */
    private static CompletionStage<? extends HttpResponse<?>> handlerStage(@Nullable CompletionStage<? extends HttpResponse<?>> stage) {
        if (stage == null) {
            throw noStage();
        }
        return stage;
    }

    private static NullPointerException noStage() {
        return new NullPointerException("The asynchronous handler returned no stage");
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
        @Nullable R invoke(@Nullable Object[] arguments) throws Exception;
    }

}
