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
package io.micronaut.websocket.route;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A handler function of a WebSocket route presented as an executable method, so that the
 * WebSocket handler binds its arguments (the session, the upgrade request, the message, the close
 * reason, the error) and waits for its stage exactly like for a method of a
 * {@link io.micronaut.websocket.annotation.ServerWebSocket} bean.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class WebSocketRouteMethod implements ExecutableMethod<Object, CompletionStage<?>>, MethodExecutionHandle<Object, CompletionStage<?>> {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final ReturnType<CompletionStage<?>> RETURN_TYPE = (ReturnType) ReturnType.of((Class) CompletionStage.class, Argument.OBJECT_ARGUMENT);

    private final Object endpoint;
    private final String name;
    private final Argument<?>[] arguments;
    private final AnnotationMetadata annotationMetadata;
    private final Invoker invoker;

    /**
     * @param endpoint           The endpoint the handler belongs to
     * @param name               The name of the handler, e.g. {@code onMessage}
     * @param annotationMetadata The annotations of the handler, e.g. {@code @OnMessage(maxPayloadLength = ...)}
     * @param invoker            Calls the handler
     * @param arguments          The arguments of the handler
     */
    WebSocketRouteMethod(Object endpoint, String name, AnnotationMetadata annotationMetadata, Invoker invoker, Argument<?>... arguments) {
        this.endpoint = endpoint;
        this.name = name;
        this.arguments = arguments;
        this.annotationMetadata = annotationMetadata;
        this.invoker = invoker;
    }

    @Override
    public Object getTarget() {
        return endpoint;
    }

    @Override
    public ExecutableMethod<Object, CompletionStage<?>> getExecutableMethod() {
        return this;
    }

    @Override
    public CompletionStage<?> invoke(@Nullable Object... arguments) {
        CompletionStage<?> stage;
        try {
            stage = invoker.invoke(arguments);
        } catch (Exception e) {
            // like a method: the error handler sees the exception the handler threw
            return ExceptionUtils.sneakyThrow(e);
        }
        // a handler without a stage is done
        return stage == null ? CompletableFuture.completedFuture(null) : stage;
    }

    @Override
    public CompletionStage<?> invoke(Object instance, @Nullable Object... arguments) {
        return invoke(arguments);
    }

    @Override
    public Method getTargetMethod() {
        throw new UnsupportedOperationException("The handler " + this + " is a function, not a method");
    }

    @Override
    public ReturnType<CompletionStage<?>> getReturnType() {
        return RETURN_TYPE;
    }

    @Override
    public Argument<?>[] getArguments() {
        return arguments;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Object> getDeclaringType() {
        return (Class<Object>) endpoint.getClass();
    }

    @Override
    public String getMethodName() {
        return name;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public String toString() {
        return name + " of " + endpoint;
    }

    /**
     * Calls the handler function.
     */
    @FunctionalInterface
    interface Invoker {
        @Nullable CompletionStage<?> invoke(@Nullable Object[] arguments) throws Exception;
    }
}
