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
package io.micronaut.http.client.netty.trailers;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BaseStreamingByteBody;
import io.micronaut.http.body.stream.NoTrailers;
import io.micronaut.http.simple.SimpleHttpHeaders;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A body with trailers keeps them when it is split or streamed, and a stage of trailers that
 * fails, fails the body.
 */
class TrailingBodyTest {
    private static final long TIMEOUT_SECONDS = 10;
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void noTrailersIsEmpty() {
        HttpHeaders headers = NoTrailers.HEADERS;
        Assertions.assertTrue(headers.isEmpty());
        Assertions.assertTrue(headers.getAll("grpc-status").isEmpty());
        Assertions.assertNull(headers.get("grpc-status"));
        Assertions.assertTrue(headers.names().isEmpty());
        Assertions.assertTrue(headers.values().isEmpty());
        Assertions.assertTrue(headers.get("grpc-status", String.class).isEmpty());
        Assertions.assertSame(headers, NoTrailers.STAGE.toCompletableFuture().join());
    }

    @Test
    void aSplitBodyKeepsTheTrailers() throws Exception {
        try (CloseableByteBody body = FACTORY.withTrailers(bytes("hello"), CompletableFuture.completedFuture(trailers()))) {
            Assertions.assertTrue(body.expectedLength().isEmpty(), "A body with trailers has no expected length");
            try (CloseableByteBody split = body.split(ByteBody.SplitBackpressureMode.FASTEST)) {
                Assertions.assertEquals("0", split.trailers().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).get("grpc-status"));
                Assertions.assertEquals("hello", split.buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
            }
            try (InputStream in = body.allowDiscard().toInputStream()) {
                Assertions.assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void aStreamedBodyKeepsTheTrailers() throws Exception {
        CloseableByteBody body = FACTORY.withTrailers(bytes("hello"), CompletableFuture.completedFuture(trailers()));
        BaseStreamingByteBody<?> streaming = FACTORY.toStreaming(body);
        Assertions.assertEquals("hello", streaming.buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
        Assertions.assertEquals("0", streaming.trailers().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).get("grpc-status"));
    }

    @Test
    void failedTrailersFailTheBody() {
        IllegalStateException failure = new IllegalStateException("no checksum");
        CloseableByteBody body = FACTORY.withTrailers(bytes("hello"), CompletableFuture.failedFuture(failure));
        BaseStreamingByteBody<?> streaming = FACTORY.toStreaming(body);
        ExecutionException e = Assertions.assertThrows(ExecutionException.class,
            () -> streaming.buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Assertions.assertSame(failure, e.getCause());
    }

    private static CloseableByteBody bytes(String text) {
        return FACTORY.copyOf(text, StandardCharsets.UTF_8);
    }

    private static HttpHeaders trailers() {
        return new SimpleHttpHeaders(Map.of("grpc-status", "0"), ConversionService.SHARED);
    }
}
