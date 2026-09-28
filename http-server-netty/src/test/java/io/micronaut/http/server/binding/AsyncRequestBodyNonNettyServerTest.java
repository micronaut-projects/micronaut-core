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
package io.micronaut.http.server.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The body of a request of a server that is not the Netty server, like a servlet server: its
 * bytes are byte arrays, not Netty buffers, and it decodes a new body each time it is asked for
 * one.
 */
class AsyncRequestBodyNonNettyServerTest {

    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void theBodyIsDecodedFromTheBytesOfTheRequest() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run();
             OtherServerRequest server = request("{\"a\":1}", MediaType.APPLICATION_JSON_TYPE)) {
            DefaultAsyncRequestBody body = body(ctx, server);

            assertEquals(Map.of("a", 1), body.body(Map.class).toCompletableFuture().get(10, TimeUnit.SECONDS));
            body.releaseBody().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    private static OtherServerRequest request(String json, MediaType contentType) {
        SimpleHttpRequest<Object> request = new SimpleHttpRequest<>(HttpMethod.POST, "/body", null);
        request.contentType(contentType);
        return new OtherServerRequest(request, json);
    }

    private static DefaultAsyncRequestBody body(ApplicationContext ctx, OtherServerRequest server) {
        return new DefaultAsyncRequestBody(server, server, ctx.getBean(AsyncRequestBodyArgumentBinder.class));
    }

    /**
     * A server request whose bytes are byte arrays, and which decodes a new body on each call.
     */
    private static final class OtherServerRequest extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object>, AutoCloseable {
        private final CloseableByteBody body;

        OtherServerRequest(HttpRequest<Object> delegate, String json) {
            super(delegate);
            this.body = BODIES.copyOf(json, StandardCharsets.UTF_8);
        }

        @Override
        public Optional<Object> getBody() {
            return Optional.of(new Object());
        }

        @Override
        public ByteBody byteBody() {
            return body;
        }

        @Override
        public ByteBodyFactory byteBodyFactory() {
            return BODIES;
        }

        @Override
        public void close() {
            // like a server at the end of the request
            body.close();
        }
    }
}
