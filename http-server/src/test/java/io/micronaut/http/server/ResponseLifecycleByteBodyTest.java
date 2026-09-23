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
package io.micronaut.http.server;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseLifecycleByteBodyTest {

    static final class Tracking<B> extends HttpResponseWrapper<B> implements ByteBodyHttpResponse<B> {
        final CloseableByteBody bytes = AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, "raw".getBytes(StandardCharsets.UTF_8));
        int closed;

        Tracking(HttpResponse<B> delegate) {
            super(delegate);
        }

        @Override
        public ByteBody byteBody() {
            return bytes;
        }

        @Override
        public void close() {
            closed++;
            bytes.close();
        }
    }

    static final MessageBodyWriter<Object> WRITER = new MessageBodyWriter<>() {
        @Override
        public void writeTo(Argument<Object> type, MediaType mediaType, Object object, MutableHeaders outgoingHeaders, OutputStream outputStream) {
            try {
                outputStream.write(object.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    static final MessageBodyHandlerRegistry REGISTRY = new MessageBodyHandlerRegistry() {
        @Override
        public <T> Optional<MessageBodyReader<T>> findReader(Argument<T> type, List<MediaType> mediaType) {
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<MessageBodyWriter<T>> findWriter(Argument<T> type, List<MediaType> mediaType) {
            return Optional.of((MessageBodyWriter<T>) WRITER);
        }
    };

    private static ResponseLifecycle lifecycle() {
        // anonymous: JUnit discovery would reflect over a nested class, and ResponseLifecycle
        // references optional JSON types
        return new ResponseLifecycle(null, REGISTRY, ConversionService.SHARED, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)) {
            @Override
            protected Executor ioExecutor() {
                return Runnable::run;
            }
        };
    }

    private static ByteBodyHttpResponse<?> encode(HttpRequest<?> request, HttpResponse<?> response) throws Exception {
        CompletableFuture<ByteBodyHttpResponse<?>> future = new CompletableFuture<>();
        lifecycle().encodeHttpResponseSafe(request, response).onComplete((v, e) -> {
            if (e == null) {
                future.complete(v);
            } else {
                future.completeExceptionally(e);
            }
        });
        return future.get();
    }

    private static String text(ByteBodyHttpResponse<?> response) throws Exception {
        return new String(response.byteBody().buffer().get().toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void byteBodyResponsePassedThrough() throws Exception {
        Tracking<Object> original = new Tracking<>(HttpResponse.ok().header(HttpHeaders.TRANSFER_ENCODING, "chunked"));
        ByteBodyHttpResponse<?> result = encode(HttpRequest.GET("/"), original);
        assertSame(original, result);
        assertEquals(0, original.closed);
        assertEquals("raw", text(result));
        result.close();
    }

    @Test
    void mutableByteBodyResponseDropsTransferEncoding() throws Exception {
        Tracking<Object> original = new Tracking<>(HttpResponse.ok());
        MutableByteBodyHttpResponse<?> mutable = MutableByteBodyHttpResponse.of(original);
        mutable.header(HttpHeaders.TRANSFER_ENCODING, "chunked");
        mutable.header("X-Kept", "yes");
        ByteBodyHttpResponse<?> result = encode(HttpRequest.GET("/"), mutable);
        assertSame(mutable, result);
        assertFalse(result.getHeaders().contains(HttpHeaders.TRANSFER_ENCODING));
        assertEquals("yes", result.getHeaders().get("X-Kept"));
        assertEquals("raw", text(result));
        result.close();
    }

    @Test
    void wrappedByteBodyResponseKeepsWrapperStatus() throws Exception {
        Tracking<Object> original = new Tracking<>(HttpResponse.ok());
        HttpResponseWrapper<Object> wrapper = new HttpResponseWrapper<>(new HttpResponseWrapper<>(original)) {
            @Override
            public int code() {
                return 202;
            }
        };
        ByteBodyHttpResponse<?> result = encode(HttpRequest.GET("/"), wrapper);
        assertNotSame(original, result);
        assertEquals(202, result.code());
        assertEquals("raw", text(result));
        result.close();
    }

    @Test
    void headRequestClosesBytes() throws Exception {
        Tracking<Object> original = new Tracking<>(HttpResponse.ok());
        ByteBodyHttpResponse<?> result = encode(HttpRequest.HEAD("/"), original);
        assertEquals(1, original.closed);
        assertEquals(0, result.byteBody().expectedLength().orElse(-1));
        result.close();
    }

    @Test
    void wrapperWithObjectBodyClosesWrappedBytes() throws Exception {
        Tracking<Object> original = new Tracking<>(HttpResponse.ok());
        HttpResponseWrapper<Object> wrapper = new HttpResponseWrapper<>(new HttpResponseWrapper<>(original)) {
            @Override
            public Optional<Object> getBody() {
                return Optional.of("object");
            }

            @Override
            public Object body() {
                return "object";
            }
        };
        ByteBodyHttpResponse<?> result = encode(HttpRequest.GET("/"), wrapper);
        // closed by encodeByteBodyResponse and again by HttpResponseWrapper.toMutableResponse
        assertTrue(original.closed > 0);
        assertEquals("object", text(result));
        result.close();
    }

    @Test
    void replacedBytesEncodeObjectBody() throws Exception {
        Tracking<Object> original = new Tracking<>(HttpResponse.ok());
        MutableByteBodyHttpResponse<?> mutable = MutableByteBodyHttpResponse.of(original);
        mutable.body("replacement");
        ByteBodyHttpResponse<?> result = encode(HttpRequest.GET("/"), mutable);
        assertEquals(1, original.closed);
        assertEquals("replacement", text(result));
        result.close();
    }

    @Test
    void wrapperOfPlainResponse() throws Exception {
        ByteBodyHttpResponse<?> result = encode(HttpRequest.GET("/"), new HttpResponseWrapper<>(HttpResponse.ok()));
        assertEquals(200, result.code());
        assertEquals(0, result.byteBody().expectedLength().orElse(-1));
        result.close();
    }
}
