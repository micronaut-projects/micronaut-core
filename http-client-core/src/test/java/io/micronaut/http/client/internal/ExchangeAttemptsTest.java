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
package io.micronaut.http.client.internal;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpRequestWrapper;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.DirectByteBodyAccess;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.client.multipart.MultipartBody;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeAttemptsTest {

    @Test
    void aBodyInMemoryIsReplayable() {
        assertTrue(ExchangeAttempts.isReplayable(HttpRequest.GET("/x"), body -> true));
        assertTrue(ExchangeAttempts.isReplayable(HttpRequest.PUT("/x", "text"), body -> true));
        assertTrue(ExchangeAttempts.isReplayable(HttpRequest.PUT("/x", new byte[] {1}), body -> true));
        MultipartBody bytes = MultipartBody.builder().addPart("file", "file.txt", new byte[] {1}).build();
        assertTrue(ExchangeAttempts.isReplayable(HttpRequest.PUT("/x", bytes), body -> true));
    }

    @Test
    void aBodyReadOnceIsNotReplayable() {
        ByteBody byteBody = AvailableByteArrayBody.create(ReadBufferFactory.getJdkFactory().adapt(new byte[] {1}));
        assertFalse(ExchangeAttempts.isReplayable(HttpRequest.PUT("/x", byteBody), body -> true));
        assertFalse(ExchangeAttempts.isReplayable(HttpRequest.PUT("/x", new ByteArrayInputStream(new byte[] {1})), body -> true));
        assertFalse(ExchangeAttempts.isReplayable(HttpRequest.PUT("/x", Flux.just("a")), body -> true));
        assertFalse(ExchangeAttempts.isReplayable(HttpRequest.PUT("/x", new Counted()), body -> true));
        MultipartBody stream = MultipartBody.builder()
            .addPart("file", "file.txt", new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), 7)
            .build();
        assertFalse(ExchangeAttempts.isReplayable(HttpRequest.PUT("/x", stream), body -> true));
        assertFalse(ExchangeAttempts.isReplayable(HttpRequest.PUT("/x", "text"), body -> false), "the client cannot send it again");
    }

    @Test
    void theRelayedBytesOfAServerRequestAreNotReplayable() {
        ByteBody bytes = AvailableByteArrayBody.create(ReadBufferFactory.getJdkFactory().adapt(new byte[] {1}));
        MutableHttpRequest<Object> relayed = new Relayed(HttpRequest.POST("/x", null), bytes);
        assertFalse(ExchangeAttempts.isReplayable(relayed, body -> true));
        // a wrapper that keeps the body is looked through
        assertFalse(ExchangeAttempts.isReplayable(new MutableHttpRequestWrapper<Object>(ConversionService.SHARED, relayed) { }, body -> true));
        assertTrue(ExchangeAttempts.isReplayable(new Relayed(HttpRequest.POST("/x", null), null), body -> true), "no bytes to relay");
    }

    @Test
    void anAttemptSendsACopyOfTheRequest() {
        MutableHttpRequest<String> request = HttpRequest.PUT("/x?q=1", "text").header("X-A", "1").header("X-A", "2");
        request.setAttribute("attr", "value");
        MutableHttpRequest<?> copy = ExchangeAttempts.copyForAttempt(request);
        assertEquals(request.getMethod(), copy.getMethod());
        assertEquals(request.getUri(), copy.getUri());
        assertEquals(java.util.List.of("1", "2"), copy.getHeaders().getAll("X-A"));
        assertEquals("value", copy.getAttribute("attr").orElseThrow());
        assertEquals("text", copy.getBody().orElseThrow());
        copy.getHeaders().add("X-B", "b");
        assertNull(request.getHeaders().get("X-B"), "the copy is independent");
    }

    @Test
    void aDiscardedResponseIsReleased() {
        Counted counted = new Counted();
        ExchangeAttempts.discard(HttpResponse.ok(counted));
        assertTrue(counted.released.get());
        ExchangeAttempts.discard(HttpResponse.noContent());
        ExchangeAttempts.discard(HttpResponse.ok("text"));
    }

    /**
     * A body that is released once it is sent.
     */
    static final class Counted implements ReferenceCounted {
        final AtomicBoolean released = new AtomicBoolean();

        @Override
        public ReferenceCounted retain() {
            return this;
        }

        @Override
        public boolean release() {
            released.set(true);
            return true;
        }
    }

    /**
     * A request with the relayed bytes of a server request.
     */
    static final class Relayed extends MutableHttpRequestWrapper<Object> implements DirectByteBodyAccess {
        private final @Nullable ByteBody bytes;

        @SuppressWarnings("unchecked")
        Relayed(MutableHttpRequest<?> delegate, @Nullable ByteBody bytes) {
            super(ConversionService.SHARED, (MutableHttpRequest<Object>) delegate);
            this.bytes = bytes;
        }

        @Override
        public @Nullable ByteBody byteBodyDirect() {
            return bytes;
        }
    }
}
