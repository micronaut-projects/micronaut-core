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

import io.micronaut.http.body.ByteBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Closing a {@link PublisherAsBlocking} from another thread must wake a reader that is blocked
 * waiting for data, since the cancelled upstream will never signal it again.
 */
class PublisherAsBlockingCloseTest {
    @Test
    @Timeout(10)
    void closeWakesBlockedReader() throws Exception {
        Sinks.Many<io.micronaut.core.io.buffer.ReadBuffer> sink = Sinks.many().unicast().onBackpressureBuffer();
        PublisherAsBlocking blocking = new PublisherAsBlocking();
        sink.asFlux().subscribe(blocking);
        InputStream stream = new PublisherAsStream(blocking);

        CompletableFuture<Integer> reader = CompletableFuture.supplyAsync(() -> {
            try {
                return stream.read();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        while (sink.currentSubscriberCount() == 0 || !blockedReading(reader)) {
            Thread.onSpinWait();
        }
        blocking.close();

        ExecutionException e = assertThrows(ExecutionException.class, () -> reader.get(5, TimeUnit.SECONDS));
        assertInstanceOf(ByteBody.BodyDiscardedException.class, e.getCause().getCause().getCause());
    }

    private static boolean blockedReading(CompletableFuture<?> reader) throws InterruptedException {
        // give the reader time to request and park on the condition
        Thread.sleep(200);
        return !reader.isDone();
    }
}
