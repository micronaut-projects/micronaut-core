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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Failures reported to a body after it was discarded must not be attached (addSuppressed) to the
 * JVM-wide {@link ByteBody.BodyDiscardedException} singleton, which would grow without bound.
 */
class SharedBufferErrorSuppressionTest {

    @Test
    void errorsAfterDiscardDoNotAccumulateOnTheSingleton() {
        ByteBody.BodyDiscardedException singleton = ByteBody.BodyDiscardedException.create();
        int before = singleton.getSuppressed().length;
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        for (int i = 0; i < 10; i++) {
            Sinks.Many<ReadBuffer> source = Sinks.many().unicast().onBackpressureBuffer();
            CloseableByteBody body = bbf.adapt(source.asFlux(), OptionalLong.empty());
            BaseSharedBuffer sharedBuffer = ((BaseStreamingByteBody<?>) body).sharedBuffer;
            // a subscriber that is cancelled discards the body
            Flux.from(body.toReadBufferPublisher()).subscribe().dispose();
            body.close();
            // the same path a server takes: the body is discarded, then the upstream fails
            sharedBuffer.error(singleton);
            sharedBuffer.error(new IOException("decompression failure " + i));
        }
        assertEquals(before, singleton.getSuppressed().length, "failures were attached to the shared BodyDiscardedException singleton");
    }
}
