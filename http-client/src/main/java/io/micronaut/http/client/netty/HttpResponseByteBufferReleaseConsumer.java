package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpResponse;
import reactor.core.publisher.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Experimental
@Internal
public class HttpResponseByteBufferReleaseConsumer implements Consumer<Signal<HttpResponse<ByteBuffer<?>>>> {

    private final List<HttpResponse<ByteBuffer<?>>> elements = new ArrayList<>();

    @Override
    public void accept(Signal<HttpResponse<ByteBuffer<?>>> signal) {
        if (signal.isOnNext()) {
            HttpResponse<ByteBuffer<?>> next = signal.get();
            if (next != null) {
                elements.add(next);
            }
        }
        if (signal.isOnComplete() || signal.isOnError()) {
            for (HttpResponse<ByteBuffer<?>> buffer : elements) {
                ByteBufferUtils.release(buffer);
            }
        }
    }
}
