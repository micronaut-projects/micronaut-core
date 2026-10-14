package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ReactiveByteBufferByteBodyTest {
    @Test
    @Timeout(10)
    public void reentrancy() throws ExecutionException, InterruptedException {
        // reentrant subscribe inside onComplete handler. servlet uses this pattern
        Sinks.One<ReadBuffer> sink = Sinks.one();
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        try (CloseableByteBody body = bbf.adapt(sink.asMono())) {
            CompletableFuture<byte[]> result = new CompletableFuture<>();
            InternalByteBody.bufferFlow(body.split(ByteBody.SplitBackpressureMode.FASTEST)).onComplete((cabb, e) -> {
                if (e != null) {
                    result.completeExceptionally(e);
                    return;
                }
                cabb.close();
                try {
                    result.complete(body.toInputStream().readAllBytes());
                } catch (IOException ex) {
                    result.completeExceptionally(ex);
                }
            });
            sink.tryEmitValue(bbf.readBufferFactory().copyOf("Hello", StandardCharsets.UTF_8)).orThrow();
            result.get();
        }
    }

    @Test
    @Timeout(10)
    public void reentrantUpstreamBytesReachEverySubscriberInOrder() throws Exception {
        // an upstream that emits the next bytes on the thread that asks for them, while the first
        // subscriber is given the previous bytes: the second subscriber gets them in order too
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        ByteBodyFactory.StreamingBody streaming = bbf.createStreamingBody(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            public void onBytesConsumed(long bytesConsumed) {
                // the test adds the bytes itself, whatever the demand
            }

            @Override
            public void allowDiscard() {
                // nothing to discard: the test completes the body
            }
        });
        CloseableByteBody root = streaming.rootBody();
        CloseableByteBody split = root.split(ByteBody.SplitBackpressureMode.FASTEST);
        CompletableFuture<String> first = Flux.from(split.toReadBufferPublisher())
            .map(buffer -> buffer.toString(StandardCharsets.UTF_8))
            .doOnNext(text -> {
                if (text.equals("a")) {
                    streaming.sharedBuffer().add(bbf.readBufferFactory().copyOf("b", StandardCharsets.UTF_8));
                    streaming.sharedBuffer().complete();
                }
            })
            .collect(StringBuilder::new, StringBuilder::append)
            .map(StringBuilder::toString)
            .toFuture();
        CompletableFuture<String> second = Flux.from(root.toReadBufferPublisher())
            .map(buffer -> buffer.toString(StandardCharsets.UTF_8))
            .collect(StringBuilder::new, StringBuilder::append)
            .map(StringBuilder::toString)
            .toFuture();
        streaming.sharedBuffer().add(bbf.readBufferFactory().copyOf("a", StandardCharsets.UTF_8));
        assertEquals("ab", first.get(10, TimeUnit.SECONDS));
        assertEquals("ab", second.get(10, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(10)
    public void bodyClosedReentrantlyWhenBufferingHitsTheLimit() throws Exception {
        // the buffering of a split fails on the buffer limit while the upstream adds the bytes,
        // and its failure closes the body in that same call, e.g. on a servlet container
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        ByteBodyFactory.StreamingBody streaming = bbf.createStreamingBody(new BodySizeLimits(Long.MAX_VALUE, 4), new BufferConsumer.Upstream() {
            @Override
            public void onBytesConsumed(long bytesConsumed) {
                // the test adds the bytes itself, whatever the demand
            }

            @Override
            public void allowDiscard() {
                // nothing to discard
            }
        });
        CloseableByteBody root = streaming.rootBody();
        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        InternalByteBody.bufferFlow(root.split(ByteBody.SplitBackpressureMode.FASTEST)).onComplete((cabb, e) -> {
            if (cabb != null) {
                cabb.close();
            }
            root.close();
            failure.complete(e);
        });
        streaming.sharedBuffer().add(bbf.readBufferFactory().copyOf("too large", StandardCharsets.UTF_8));
        assertInstanceOf(BufferLengthExceededException.class, failure.get(10, TimeUnit.SECONDS));
    }
}
