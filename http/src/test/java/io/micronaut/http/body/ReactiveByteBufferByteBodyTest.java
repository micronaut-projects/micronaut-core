package io.micronaut.http.body;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

class ReactiveByteBufferByteBodyTest {
    @Test
    @Timeout(10)
    public void reentrancy() throws ExecutionException, InterruptedException {
        // reentrant subscribe inside onComplete handler. servlet uses this pattern
        Sinks.One<ByteBuffer> sink = Sinks.one();
        try (ReactiveByteBufferByteBody body = ByteBufferBodyAdapter.adapt(sink.asMono())) {
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
            sink.tryEmitValue(ByteBuffer.wrap("Hello".getBytes(StandardCharsets.UTF_8))).orThrow();
            result.get();
        }
    }
}
