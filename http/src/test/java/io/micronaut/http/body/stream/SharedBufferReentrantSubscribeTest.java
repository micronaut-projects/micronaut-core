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
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A consumer of a split body can claim another split while it is told that the body ended, e.g. a
 * filter that reads the body and then lets the route read it. The new subscriber is added to the
 * shared buffer while the buffer walks its subscribers, and gets the same end of the body.
 */
class SharedBufferReentrantSubscribeTest {

    private static final BufferConsumer.Upstream NO_UPSTREAM = bytesConsumed -> {
    };

    @Test
    @Timeout(10)
    void splitClaimedWhileAnotherCompletes() {
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        ByteBodyFactory.StreamingBody streamingBody = bbf.createStreamingBody(BodySizeLimits.UNLIMITED, NO_UPSTREAM);
        List<String> events = new ArrayList<>();
        try (CloseableByteBody first = streamingBody.rootBody();
             CloseableByteBody second = first.split(ByteBody.SplitBackpressureMode.FASTEST)) {
            Flux.from(first.toReadBufferPublisher())
                .doOnNext(ReadBuffer::close)
                .doOnComplete(() -> Flux.from(second.toReadBufferPublisher())
                    .doOnNext(ReadBuffer::close)
                    .doOnComplete(() -> events.add("second"))
                    .subscribe())
                .doOnComplete(() -> events.add("first"))
                .subscribe();

            streamingBody.sharedBuffer().complete();
        }

        assertEquals(List.of("second", "first"), events);
    }

    @Test
    @Timeout(10)
    void splitClaimedWhileAnotherFails() {
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        ByteBodyFactory.StreamingBody streamingBody = bbf.createStreamingBody(BodySizeLimits.UNLIMITED, NO_UPSTREAM);
        List<Throwable> errors = new ArrayList<>();
        RuntimeException failure = new RuntimeException("upstream failed");
        try (CloseableByteBody first = streamingBody.rootBody();
             CloseableByteBody second = first.split(ByteBody.SplitBackpressureMode.FASTEST)) {
            Flux.from(first.toReadBufferPublisher())
                .doOnNext(ReadBuffer::close)
                .doOnError(_ -> Flux.from(second.toReadBufferPublisher())
                    .doOnNext(ReadBuffer::close)
                    .subscribe(_ -> {
                    }, errors::add))
                .subscribe(_ -> {
                }, errors::add);

            streamingBody.sharedBuffer().error(failure);
        }

        assertEquals(2, errors.size());
        assertSame(failure, errors.get(0));
        assertSame(failure, errors.get(1));
    }
}
