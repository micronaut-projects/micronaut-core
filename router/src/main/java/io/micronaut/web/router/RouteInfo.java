/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.sse.Event;
import io.micronaut.inject.util.KotlinExecutableMethodUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Common information shared between route and route match.
 *
 * @param <R> The route
 * @author Graeme Rocher
 * @since 1.0
 */
public interface RouteInfo<R> extends AnnotationMetadataProvider {

    /**
     * @return The return type
     */
    ReturnType<? extends R> getReturnType();

    /**
     * @return The argument representing the data type being produced.
     */
    default Argument<?> getBodyType() {
        final ReturnType<? extends R> returnType = getReturnType();
        if (returnType.isAsyncOrReactive()) {
            Argument<?> reactiveType = returnType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (HttpResponse.class.isAssignableFrom(reactiveType.getType())) {
                reactiveType = reactiveType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            }
            return reactiveType;
        } else if (HttpResponse.class.isAssignableFrom(returnType.getType())) {
            Argument<?> responseType = returnType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (responseType.isAsyncOrReactive()) {
                return responseType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            }
            return responseType;
        }
        return returnType.asArgument();
    }

    /**
     * @return The declaring type of the route.
     */
    Class<?> getDeclaringType();

    /**
     * The media types able to produced by this route.
     *
     * @return A list of {@link MediaType} that this route can produce
     */
    default List<MediaType> getProduces() {
        MediaType[] types = MediaType.of(getAnnotationMetadata().stringValues(Produces.class));
        Optional<Argument<?>> firstTypeVariable = getReturnType().getFirstTypeVariable();
        if (firstTypeVariable.isPresent() && Event.class.isAssignableFrom(firstTypeVariable.get().getType())) {
            return Collections.singletonList(MediaType.TEXT_EVENT_STREAM_TYPE);
        } else if (ArrayUtils.isNotEmpty(types)) {
            return Collections.unmodifiableList(Arrays.asList(types));
        } else {
            return Route.DEFAULT_PRODUCES;
        }
    }

    /**
     * The media types able to produced by this route.
     *
     * @return A list of {@link MediaType} that this route can produce
     */
    default List<MediaType> getConsumes() {
        MediaType[] types = MediaType.of(getAnnotationMetadata().stringValues(Consumes.class));
        if (ArrayUtils.isNotEmpty(types)) {
            return Collections.unmodifiableList(Arrays.asList(types));
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * @return Is this route match a suspended function (Kotlin).
     * @since 2.0.0
     */
    default boolean isSuspended() {
        return getReturnType().isSuspended();
    }

    /**
     * @return Is the route a reactive route.
     * @since 2.0.0
     */
    default boolean isReactive() {
        return getReturnType().isReactive();
    }

    /**
     * @return Does the route emit a single result or multiple results
     * @since 2.0
     */
    default boolean isSingleResult() {
        ReturnType<? extends R> returnType = getReturnType();
        return returnType.isSingleResult() ||
                (isReactive() && returnType.getFirstTypeVariable()
                        .filter(t -> HttpResponse.class.isAssignableFrom(t.getType())).isPresent()) ||
                returnType.isAsync() ||
                returnType.isSuspended();
    }

    /**
     * @return Does the route emit a single result or multiple results
     * @since 2.0
     */
    default boolean isSpecifiedSingle() {
        return getReturnType().isSpecifiedSingle();
    }

    /**
     * @return is the return type completable
     * @since 2.0
     */
    default boolean isCompletable() {
        return getReturnType().isCompletable();
    }

    /**
     * @return Is the route an async route.
     * @since 2.0.0
     */
    default boolean isAsync() {
        return getReturnType().isAsync();
    }

    /**
     * @return Is the route an async or reactive route.
     * @since 2.0.0
     */
    default boolean isAsyncOrReactive() {
        return getReturnType().isAsyncOrReactive();
    }

    /**
     * @return Does the route return void
     * @since 2.0.0
     */
    default boolean isVoid() {
        if (getReturnType().isVoid()) {
            return true;
        } else if (this instanceof MethodBasedRouteMatch && isSuspended()) {
            return KotlinExecutableMethodUtils.isKotlinFunctionReturnTypeUnit(((MethodBasedRouteMatch) this).getExecutableMethod());
        }
        return false;
    }

    /**
     * @return True if the route was called due to an error
     * @since 3.0.0
     */
    default boolean isErrorRoute() {
        return false;
    }

    /**
     * Finds predefined route http status or uses default.
     *
     * @param defaultStatus The default status
     * @return The status
     * @since 2.5.2
     */
    @NonNull
    default HttpStatus findStatus(HttpStatus defaultStatus) {
        return getAnnotationMetadata().enumValue(Status.class, HttpStatus.class).orElse(defaultStatus);
    }

    /**
     * Checks if route is for web socket.
     *
     * @return true if it's web socket route
     * @since 2.5.2
     */
    default boolean isWebSocketRoute() {
        return getAnnotationMetadata().hasAnnotation("io.micronaut.websocket.annotation.OnMessage");
    }
}
