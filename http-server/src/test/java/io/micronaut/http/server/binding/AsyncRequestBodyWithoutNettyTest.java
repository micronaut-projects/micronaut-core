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
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.server.exceptions.UnsupportedMediaException;
import io.micronaut.http.simple.SimpleHttpRequest;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The elements of a body on a server without micronaut-http-netty, which provides the readers
 * that decode the elements of JSON one at a time: this module's tests do not have it.
 */
class AsyncRequestBodyWithoutNettyTest {

    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void theElementsOfJsonNeedTheChunkedJsonReader() throws Exception {
        Throwable failure = firstElementFailure("[{\"a\":1}]", MediaType.APPLICATION_JSON_TYPE);

        // not a 415: the media type is supported, the reader is missing
        UnsupportedOperationException e = assertInstanceOf(UnsupportedOperationException.class, failure);
        assertTrue(e.getMessage().contains("micronaut-http-netty"), e.getMessage());
        assertTrue(e.getMessage().contains(MediaType.APPLICATION_JSON), e.getMessage());
    }

    @Test
    void theElementsOfAJsonStreamNeedTheChunkedJsonReader() throws Exception {
        Throwable failure = firstElementFailure("{\"a\":1}\n", MediaType.APPLICATION_JSON_STREAM_TYPE);

        UnsupportedOperationException e = assertInstanceOf(UnsupportedOperationException.class, failure);
        assertTrue(e.getMessage().contains("micronaut-http-netty"), e.getMessage());
    }

    @Test
    void aMediaTypeThatHasNoElementsIsUnsupported() throws Exception {
        Throwable failure = firstElementFailure("a", MediaType.TEXT_PLAIN_TYPE);

        assertInstanceOf(UnsupportedMediaException.class, failure);
    }

    private static Throwable firstElementFailure(String content, MediaType contentType) throws Exception {
        // a JSON mapper, which the JSON message body handler of json-core takes: this module's
        // tests have none, and reading the elements must not use it
        JsonMapper mapper = (JsonMapper) Proxy.newProxyInstance(JsonMapper.class.getClassLoader(), new Class<?>[]{JsonMapper.class}, (proxy, method, args) -> {
            throw new AssertionError("Not used: " + method);
        });
        try (ApplicationContext ctx = ApplicationContext.builder().singletons(mapper).start();
             OtherServerRequest server = request(content, contentType)) {
            DefaultAsyncRequestBody body = new DefaultAsyncRequestBody(server, server, ctx.getBean(AsyncRequestBodyArgumentBinder.class));
            try (BodyElements<Map> elements = body.elements(Map.class)) {
                ExecutionException e = assertThrows(ExecutionException.class, () -> elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS));
                return e.getCause();
            } finally {
                body.releaseBody().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static OtherServerRequest request(String content, MediaType contentType) {
        SimpleHttpRequest<Object> request = new SimpleHttpRequest<>(HttpMethod.POST, "/body", null);
        request.contentType(contentType);
        return new OtherServerRequest(request, content);
    }

    /**
     * A server request whose bytes are byte arrays, like the request of a servlet server.
     */
    private static final class OtherServerRequest extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object>, AutoCloseable {
        private final CloseableByteBody body;

        OtherServerRequest(HttpRequest<Object> delegate, String content) {
            super(delegate);
            this.body = BODIES.copyOf(content, StandardCharsets.UTF_8);
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
            body.close();
        }
    }
}
