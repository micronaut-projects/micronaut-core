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
import io.micronaut.http.MutableHttpHeaders;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
}
