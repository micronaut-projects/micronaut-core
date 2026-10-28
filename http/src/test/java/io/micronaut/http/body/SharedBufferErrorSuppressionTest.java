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
package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Failures reported to a body after it was discarded must not be attached (addSuppressed) to the
 * {@link ByteBody.BodyDiscardedException}, which used to be a JVM-wide singleton.
 */
class SharedBufferErrorSuppressionTest {

    @Test
    void errorsAfterDiscardDoNotAccumulateOnTheDiscardException() {
        ByteBody.BodyDiscardedException discarded = ByteBody.BodyDiscardedException.create();
        for (int i = 0; i < 10; i++) {
            ReactiveByteBufferByteBody.SharedBuffer sharedBuffer = new ReactiveByteBufferByteBody.SharedBuffer(
                ReadBufferFactory.getJdkFactory(), BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
                    @Override
                    public void onBytesConsumed(long bytesConsumed) {
                    }
                });
            // the same path a server takes: the body is discarded, then the upstream fails
            sharedBuffer.error(discarded);
            sharedBuffer.error(new IOException("decompression failure " + i));
        }
        assertEquals(0, discarded.getSuppressed().length, "failures were attached to the BodyDiscardedException");
        assertEquals(0, ByteBody.BodyDiscardedException.create().getSuppressed().length);
    }
}
