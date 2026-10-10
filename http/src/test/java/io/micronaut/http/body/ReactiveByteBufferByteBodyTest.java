package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
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
            }

            @Override
            public void allowDiscard() {
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
}
