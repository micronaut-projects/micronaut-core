package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import reactor.core.publisher.Signal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Experimental
@Internal
public class ByteBufferSafeReleaseConsumer implements Consumer<Signal<ByteBuffer<?>>> {

    private final List<ByteBuffer<?>> elements = new ArrayList<>();

    @Override
    public void accept(Signal<ByteBuffer<?>> signal) {
        if (signal.isOnNext()) {
            ByteBuffer<?> next = signal.get();
            if (next != null) {
                elements.add(next);
            }
        }
        if (signal.isOnComplete() || signal.isOnError()) {
            for (ByteBuffer<?> buffer : elements) {
                ByteBufferUtils.safeRelease(buffer);
            }
        }
    }
}
