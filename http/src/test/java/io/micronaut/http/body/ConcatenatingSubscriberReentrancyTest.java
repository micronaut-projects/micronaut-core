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

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A subscriber may consume a reserved split of a body from inside its own completion callback (the
 * servlet integration does this). That has to hold when the final bytes and the completion arrive
 * together, as they do for the closing bracket of a JSON array: the buffer must have stored those
 * bytes and be complete before any subscriber learns of the completion, otherwise the reentrant
 * consumer misses the final piece and then waits for a completion that is never delivered.
 */
class ConcatenatingSubscriberReentrancyTest {

    @Test
    @Timeout(10)
    void reentrantConsumptionFromAStreamingCompletionCallbackSeesTheTrailingBytes() throws Exception {
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        Sinks.Many<ByteBody> items = Sinks.many().unicast().onBackpressureBuffer();
        CompletableFuture<String> result = new CompletableFuture<>();
        try (CloseableByteBody body = ConcatenatingSubscriber.concatenate(bbf, items.asFlux(), ConcatenatingSubscriber.Separators.JDK_JSON)) {
            // a streaming consumer of a split, which in its completion callback reads the primary
            Flux.from(body.split(ByteBody.SplitBackpressureMode.FASTEST).toReadBufferPublisher())
                .doOnNext(ReadBuffer::close)
                .doOnComplete(() -> {
                    try {
                        result.complete(new String(body.toInputStream().readAllBytes(), StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .doOnError(result::completeExceptionally)
                .subscribe();

            items.tryEmitNext(bbf.adapt("{}".getBytes(StandardCharsets.UTF_8))).orThrow();
            // completion arrives together with the closing bracket
            items.tryEmitComplete().orThrow();

            assertEquals("[{}]", result.get());
        }
    }

    /**
     * The final bytes are handed to the streaming subscribers after the buffer has been completed,
     * and completing it runs the buffering subscribers' callbacks first. A callback that subscribes
     * another split at that point must not confuse the delivery to the subscribers that existed
     * when the final bytes arrived: the new split is served from the buffer, the older streaming
     * split still gets its closing bracket and completion.
     */
    @Test
    @Timeout(10)
    void splitSubscribedFromABufferingCallbackDuringCompletion() throws Exception {
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        Sinks.Many<ByteBody> items = Sinks.many().unicast().onBackpressureBuffer();
        CompletableFuture<String> streamed = new CompletableFuture<>();
        CompletableFuture<String> buffered = new CompletableFuture<>();
        CompletableFuture<String> late = new CompletableFuture<>();
        try (CloseableByteBody body = ConcatenatingSubscriber.concatenate(bbf, items.asFlux(), ConcatenatingSubscriber.Separators.JDK_JSON)) {
            CloseableByteBody first = body.split(ByteBody.SplitBackpressureMode.FASTEST);
            CloseableByteBody second = body.split(ByteBody.SplitBackpressureMode.FASTEST);
            // one split is streamed from the start
            StringBuilder firstText = new StringBuilder();
            Flux.from(first.toReadBufferPublisher())
                .doOnNext(rb -> {
                    firstText.append(new String(rb.toArray(), StandardCharsets.UTF_8));
                    rb.close();
                })
                .doOnComplete(() -> streamed.complete(firstText.toString()))
                .doOnError(streamed::completeExceptionally)
                .subscribe();
            // the primary is buffered, and its callback streams the second split
            body.buffer().whenComplete((available, e) -> {
                if (e != null) {
                    buffered.completeExceptionally(e);
                    return;
                }
                buffered.complete(new String(available.toByteArray(), StandardCharsets.UTF_8));
                available.close();
                StringBuilder secondText = new StringBuilder();
                Flux.from(second.toReadBufferPublisher())
                    .doOnNext(r -> {
                        secondText.append(new String(r.toArray(), StandardCharsets.UTF_8));
                        r.close();
                    })
                    .doOnComplete(() -> late.complete(secondText.toString()))
                    .doOnError(late::completeExceptionally)
                    .subscribe();
            });

            items.tryEmitNext(bbf.adapt("{}".getBytes(StandardCharsets.UTF_8))).orThrow();
            items.tryEmitComplete().orThrow();

            assertEquals("[{}]", buffered.get());
            assertEquals("[{}]", streamed.get());
            assertEquals("[{}]", late.get());
        }
    }
}
