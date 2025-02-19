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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.scheduling.executor.ThreadSelection;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Common information shared between route and route match.
 *
 * @param <R> The result
 * @author Graeme Rocher
 * @since 1.0
 */
public interface RouteInfo<R> extends AnnotationMetadataProvider {

    /**
     * The default media type produced by routes.
     */
    List<MediaType> DEFAULT_PRODUCES = Collections.singletonList(MediaType.APPLICATION_JSON_TYPE);

    /**
     * @return The message body writer, if any.
     * @since 4.0.0
     */
    @Nullable
    default MessageBodyWriter<R> getMessageBodyWriter() {
        return null;
    }

    /**
     * @return The message body reader. if any.
     * @since 4.0.0
     */
    @Nullable
    default MessageBodyReader<?> getMessageBodyReader() {
        return null;
    }

    /**
     * @return The return type
     */
    ReturnType<? extends R> getReturnType();

    /**
     * @return The argument representing the data type being produced.
     */
    @NonNull
    Argument<?> getResponseBodyType();

    /**
     * Is the response body json formattable.
     * @return The response body.
     */
    default boolean isResponseBodyJsonFormattable() {
        Argument<?> argument = getResponseBodyType();
        // it would be nice to support netty ByteBuf here, but it's not clear how.
        return !(argument.getType() == byte[].class
            || ByteBuffer.class.isAssignableFrom(argument.getType()));
    }

    /**
     * @return The response body type
     * @deprecated Use {@link #getResponseBodyType()} instead
     */
    @Deprecated(since = "4.0", forRemoval = true)
    default Argument<?> getBodyType() {
        return getResponseBodyType();
    }

    /**
     * @return The argument that represents the body of the request
     */
    Optional<Argument<?>> getRequestBodyType();

    /**
     * @return The argument that represents the body of the request
     * @deprecated UYse {@link #getRequestBodyType()} instead
     */
    @Deprecated(since = "4.0", forRemoval = true)
    default Optional<Argument<?>> getBodyArgument() {
        return getRequestBodyType();
    }

    /**
     * Like {@link #getRequestBodyType()}, but excludes body arguments that may match only a part of
     * the body (i.e. that have no {@code @Body} annotation, or where the {@code @Body} has a value
     * set).
     *
     * @return The argument that represents the body
     */
    @Internal
    default Optional<Argument<?>> getFullRequestBodyType() {
        return getRequestBodyType()
            /*
            The getBodyArgument() method returns arguments for functions where it is
            not possible to dictate whether the argument is supposed to bind the entire
            body or just a part of the body. We check to ensure the argument has the body
            annotation to exclude that use case
            */
            .filter(argument -> {
                AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
                if (annotationMetadata.hasAnnotation(Body.class)) {
                    return annotationMetadata.stringValue(Body.class).isEmpty();
                } else {
                    return false;
                }
            });
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
    List<MediaType> getProduces();

    /**
     * The media types able to produced by this route.
     *
     * @return A list of {@link MediaType} that this route can produce
     */
    List<MediaType> getConsumes();

    /**
     * Whether this is consuming any content type.
     *
     * @return True if it is
     * @since 4.4.0
     */
    default boolean consumesAll() {
        return false;
    }

    /**
     * Whether the specified content type is an accepted type.
     *
     * @param contentType The content type
     * @return True if it is
     */
    boolean doesConsume(@Nullable MediaType contentType);

    /**
     * Whether this is producing any content type.
     *
     * @return True if it is
     * @since 4.3.0
     */
    default boolean producesAll() {
        return false;
    }

    /**
     * Whether the route does produce any of the given types.
     *
     * @param acceptableTypes The acceptable types
     * @return True if it is
     */
    boolean doesProduce(@Nullable Collection<MediaType> acceptableTypes);

    /**
     * Whether the route does produce any of the given types.
     *
     * @param acceptableType The acceptable type
     * @return True if it is
     */
    boolean doesProduce(@Nullable MediaType acceptableType);

    /**
     * Whether the specified content type is explicitly an accepted type.
     *
     * @param contentType The content type
     * @return True if it is
     */
    boolean explicitlyConsumes(@Nullable MediaType contentType);

    /**
     * Whether the specified content type is explicitly a producing type.
     *
     * @param contentType The content type
     * @return True if it is
     * @since 2.5.0
     */
    boolean explicitlyProduces(@Nullable MediaType contentType);

    /**
     * @return Is this route match a suspended function (Kotlin).
     * @since 2.0.0
     */
    boolean isSuspended();

    /**
     * Is this route recognized as imperative.
     * @return Is this route recognized as imperative.
     * @since 4.3.0
     */
    default boolean isImperative() {
        return false;
    }

    /**
     * @return Is the route a reactive route.
     * @since 2.0.0
     */
    boolean isReactive();

    /**
     * @return Does the route emit a single result or multiple results
     * @since 2.0
     */
    boolean isSingleResult();

    /**
     * @return Does the route emit a single result or multiple results
     * @since 2.0
     */
    boolean isSpecifiedSingle();

    /**
     * @return is the return type completable
     * @since 2.0
     */
    boolean isCompletable();

    /**
     * @return Is the route an async route.
     * @since 2.0.0
     */
    boolean isAsync();

    /**
     * @return Is the route an async or reactive route.
     * @since 2.0.0
     */
    boolean isAsyncOrReactive();

    /**
     * @return Does the route return void
     * @since 2.0.0
     */
    boolean isVoid();

    /**
     * @return True if the route was called due to an error
     * @since 3.0.0
     */
    boolean isErrorRoute();

    /**
     * Finds predefined route http status or uses default.
     *
     * @param defaultStatus The default status
     * @return The status
     * @since 2.5.2
     */
    @NonNull
    HttpStatus findStatus(HttpStatus defaultStatus);

    /**
     * Checks if route is for web socket.
     *
     * @return true if it's web socket route
     * @since 2.5.2
     */
    boolean isWebSocketRoute();

    /**
     * Whether the route permits a request body.
     * @return True if the route permits a request body
     * @since 4.0.0
     */
    boolean isPermitsRequestBody();

    /**
     * @param threadSelection The thread selection
     * @return The route executor
     * @since 4.0.0
     */
    @Nullable
    ExecutorService getExecutor(@Nullable ThreadSelection threadSelection);

    /**
     * @return true if the route needs request body to be read
     * @since 4.0.0
     */
    boolean needsRequestBody();
}
