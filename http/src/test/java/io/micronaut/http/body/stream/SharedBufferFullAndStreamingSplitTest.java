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
package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A full read of one split is still pending when a streaming read takes the last reservation.
 * The streaming reader must not take away the bytes the full reader is waiting for.
 */
class SharedBufferFullAndStreamingSplitTest {

    private static final BufferConsumer.Upstream NO_UPSTREAM = bytesConsumed -> {
    };

    @Test
    @Timeout(10)
    void streamingSubscribeAfterPendingFullKeepsBufferedData() throws Exception {
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        ByteBodyFactory.StreamingBody streamingBody = bbf.createStreamingBody(BodySizeLimits.UNLIMITED, NO_UPSTREAM);
        StringBuilder streamed = new StringBuilder();
        try (CloseableByteBody first = streamingBody.rootBody();
             CloseableByteBody second = first.split(ByteBody.SplitBackpressureMode.FASTEST)) {
            streamingBody.sharedBuffer().add(ReadBufferFactory.getJdkFactory().copyOf("abc", StandardCharsets.UTF_8));
            CompletableFuture<? extends CloseableAvailableByteBody> full = first.buffer();
            Flux.from(second.toReadBufferPublisher())
                .subscribe(rb -> {
                    try (ReadBuffer b = rb) {
                        streamed.append(b.toString(StandardCharsets.UTF_8));
                    }
                });
            streamingBody.sharedBuffer().add(ReadBufferFactory.getJdkFactory().copyOf("def", StandardCharsets.UTF_8));
            streamingBody.sharedBuffer().complete();

            try (CloseableAvailableByteBody body = full.get()) {
                assertEquals("abcdef", new String(body.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        assertEquals("abcdef", streamed.toString());
    }
}
