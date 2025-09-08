package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
}
