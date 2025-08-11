/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;

/**
 * This interface extends {@link HttpRequest} with methods that are specific to a request received
 * by an HTTP server.
 *
 * @param <B> The body type
 */
@Experimental
public interface ServerHttpRequest<B> extends HttpRequest<B> {
    /**
     * Get the bytes of the body. The body is owned by the request, so the caller should generally
     * not close it or do any primary operations. The body is usually consumed by the argument
     * binder of the controller, e.g. if it has a {@code @Body} argument. If you want to use the
     * body, {@link ByteBody#split(ByteBody.SplitBackpressureMode)} it first.
     *
     * @return The body bytes of this request
     */
    @NonNull
    ByteBody byteBody();

    /**
     * Get a {@link ByteBodyFactory} that can be used to construct an efficient response to this
     * request.
     *
     * @return The body factory
     */
    @NonNull
    default ByteBodyFactory byteBodyFactory() {
        return ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    }
}
