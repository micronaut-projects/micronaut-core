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
package io.micronaut.web.router.direct;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.web.router.builder.ValueMatcher;

/**
 * A request a direct route answers by commands: the checks read the request as the server
 * received it, and the writes compose the response the server writes, without creating a request
 * or a response object, see
 * {@link io.micronaut.web.router.builder.DirectRouteSpec#handle(io.micronaut.web.router.builder.DirectHandler)}.
 *
 * <pre>{@code
 * routes.GET("/version").handle((exchange, pathVariables) -> exchange.checkHeader(IF_NONE_MATCH, CURRENT)
 *     ? exchange.writeStatus(304).writeHeader(ETAG, VERSION).end()
 *     : exchange.writeHeader(ETAG, VERSION).writeHeader(CONTENT_TYPE, TEXT_PLAIN).writeBody(BODY).end());
 * }</pre>
 *
 * <p>The exchange is valid only while the handler runs, on the thread that received the request:
 * it is never kept, nor given to another thread. The status is {@code 200} unless written. The
 * server adds what it adds to every direct response, the framing, and the {@code Date} and
 * {@code Server} headers unless they are written. A name or value given as a constant
 * {@link CharSequence} of the runtime, e.g. a Netty {@code AsciiString}, is written without being
 * encoded again.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface DirectExchange extends DirectRequest {

    /**
     * @param name    The name of a header, compared ignoring case
     * @param matcher The matcher of its values, e.g. {@link ValueMatcher#entityTag(String)}
     * @return Whether one of the values of the header matches, read in place: no value is copied
     */
    boolean checkHeader(CharSequence name, ValueMatcher matcher);

    /**
     * @param status The status code of the response, {@code 200} unless written
     * @return This exchange
     */
    DirectExchange writeStatus(int status);

    /**
     * Add a header to the response, validated like any header the server writes.
     *
     * @param name  The name
     * @param value The value
     * @return This exchange
     */
    DirectExchange writeHeader(CharSequence name, CharSequence value);

    /**
     * Write the body of the response: the array is wrapped, never copied nor changed, so one
     * array may serve every response. A later body replaces it.
     *
     * @param body The body
     * @return This exchange
     */
    DirectExchange writeBody(byte[] body);

    /**
     * Write the body of the response, the text encoded as UTF-8. A later body replaces it.
     *
     * @param body The body
     * @return This exchange
     */
    DirectExchange writeBody(CharSequence body);

    /**
     * End the commands: the handler returns it to say it answered the request.
     *
     * @return {@code true}
     */
    default boolean end() {
        return true;
    }
}
