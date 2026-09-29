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
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.MessageBodyWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The write error handler of {@link ResponseLifecycle#encodeHttpResponseSafe(HttpRequest, HttpResponse, Function)}.
 */
class ResponseLifecycleWriteErrorTest {

    static final class Failing {
    }

    static final RuntimeException WRITE_FAILURE = new IllegalStateException("write failed");

    static final MessageBodyWriter<Object> WRITER = new MessageBodyWriter<>() {
        @Override
        public void writeTo(Argument<Object> type, MediaType mediaType, Object object, MutableHeaders outgoingHeaders, OutputStream outputStream) {
            if (object instanceof Failing) {
                throw WRITE_FAILURE;
            }
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

    private static ByteBodyHttpResponse<?> encode(HttpResponse<?> response, Function<Throwable, ExecutionFlow<HttpResponse<?>>> handler) throws Exception {
        // anonymous: JUnit discovery would reflect over a nested class, and ResponseLifecycle
        // references optional JSON types
        ResponseLifecycle lifecycle = new ResponseLifecycle(null, REGISTRY, ConversionService.SHARED, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)) {
            @Override
            protected Executor ioExecutor() {
                return Runnable::run;
            }
        };
        CompletableFuture<ByteBodyHttpResponse<?>> future = new CompletableFuture<>();
        lifecycle.encodeHttpResponseSafe(HttpRequest.GET("/"), response, handler).onComplete((v, e) -> {
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
    void handlerNotCalledWhenTheWriteSucceeds() throws Exception {
        List<Throwable> handled = new ArrayList<>();
        try (ByteBodyHttpResponse<?> result = encode(HttpResponse.ok("fine").contentType(MediaType.TEXT_PLAIN_TYPE), e -> {
            handled.add(e);
            return ExecutionFlow.just(HttpResponse.serverError());
        })) {
            assertEquals(200, result.code());
            assertEquals("fine", text(result));
        }
        assertTrue(handled.isEmpty());
    }

    @Test
    void handlerResponseReplacesTheFailedOne() throws Exception {
        List<Throwable> handled = new ArrayList<>();
        try (ByteBodyHttpResponse<?> result = encode(HttpResponse.ok(new Failing()).contentType(MediaType.TEXT_PLAIN_TYPE), e -> {
            handled.add(e);
            return ExecutionFlow.just(HttpResponse.status(HttpStatus.I_AM_A_TEAPOT).body("handled").contentType(MediaType.TEXT_PLAIN_TYPE));
        })) {
            assertEquals(418, result.code());
            assertEquals("handled", text(result));
        }
        assertEquals(List.of(WRITE_FAILURE), handled);
    }

    @Test
    void handlerThatThrowsFailsTheFlow() {
        IllegalArgumentException handlerFailure = new IllegalArgumentException("handler failed");
        ExecutionException e = assertThrows(ExecutionException.class, () -> encode(HttpResponse.ok(new Failing()).contentType(MediaType.TEXT_PLAIN_TYPE), t -> {
            throw handlerFailure;
        }));
        assertSame(handlerFailure, e.getCause());
        assertSame(WRITE_FAILURE, handlerFailure.getSuppressed()[0]);
    }
}
