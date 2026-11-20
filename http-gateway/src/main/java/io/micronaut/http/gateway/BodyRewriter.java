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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Rewrites the body of a request or a response a gateway route relays, see
 * {@link GatewayFilters#modifyRequestBody(long, BodyRewriter)} and
 * {@link GatewayFilters#modifyResponseBody(long, BodyRewriter)}. The rewriter gets the whole
 * body, decoded when the message had a {@code gzip} or {@code deflate} {@code Content-Encoding},
 * and the headers of the message, e.g. to change the {@code Content-Type}; the gateway encodes the
 * new body again and sets its {@code Content-Length}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface BodyRewriter {

    /**
     * @param exchange The exchange of the request
     * @param headers  The headers of the message whose body this is, to change
     * @param body     The body, empty for none
     * @return The stage of the new body
     * @throws Exception An error, which fails the request
     */
    CompletionStage<byte[]> rewrite(ProxyExchange exchange, MutableHttpHeaders headers, byte[] body) throws Exception;

    /**
     * @param rewrite A synchronous rewrite of the bytes
     * @return The rewriter
     */
    static BodyRewriter of(java.util.function.UnaryOperator<byte[]> rewrite) {
        return (exchange, headers, body) -> CompletableFuture.completedFuture(rewrite.apply(body));
    }

    /**
     * A rewrite of the decoded body: the bytes are read as {@code in} with the
     * {@link MessageBodyReader} of the {@code Content-Type} of the message (JSON when it has
     * none), the function makes the new value, which is written as {@code out} with the
     * {@link MessageBodyWriter} of that media type. An empty body is read as {@code null}, and a
     * {@code null} result is an empty body. A body that cannot be read or written fails the
     * request.
     *
     * @param registry The body handlers, e.g. the {@code MessageBodyHandlerRegistry} bean
     * @param in       The type of the body
     * @param out      The type of the new body
     * @param rewrite  The rewrite, which gets the exchange and the value
     * @param <I>      The type of the body
     * @param <O>      The type of the new body
     * @return The rewriter
     * @since 5.3.0
     */
    static <I, O> BodyRewriter typed(MessageBodyHandlerRegistry registry, Argument<I> in, Argument<O> out,
                                     BiFunction<ProxyExchange, @Nullable I, @Nullable O> rewrite) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(rewrite, "rewrite");
        return (exchange, headers, body) -> {
            MediaType mediaType = headers.contentType().orElse(MediaType.APPLICATION_JSON_TYPE);
            I value = null;
            if (body.length > 0) {
                MessageBodyReader<I> reader = registry.findReader(in, List.of(mediaType))
                    .orElseThrow(() -> new CodecException("No reader of " + in + " for " + mediaType));
                value = reader.read(in, mediaType, headers, new ByteArrayInputStream(body));
            }
            O result = rewrite.apply(exchange, value);
            if (result == null) {
                return CompletableFuture.completedFuture(new byte[0]);
            }
            MessageBodyWriter<O> writer = registry.findWriter(out, List.of(mediaType))
                .orElseThrow(() -> new CodecException("No writer of " + out + " for " + mediaType));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(64, body.length));
            writer.writeTo(out, mediaType, result, headers, bytes);
            return CompletableFuture.completedFuture(bytes.toByteArray());
        };
    }

    /**
     * A {@link #typed(MessageBodyHandlerRegistry, Argument, Argument, BiFunction)} rewrite of a
     * body to a value of the same type.
     *
     * @param registry The body handlers
     * @param type     The type of the body
     * @param rewrite  The rewrite of the value
     * @param <T>      The type of the body
     * @return The rewriter
     * @since 5.3.0
     */
    static <T> BodyRewriter typed(MessageBodyHandlerRegistry registry, Class<T> type, UnaryOperator<@Nullable T> rewrite) {
        Objects.requireNonNull(rewrite, "rewrite");
        Argument<T> argument = Argument.of(type);
        return typed(registry, argument, argument, (exchange, value) -> rewrite.apply(value));
    }
}
