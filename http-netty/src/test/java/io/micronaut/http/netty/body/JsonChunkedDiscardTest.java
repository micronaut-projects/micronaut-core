package io.micronaut.http.netty.body;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Netty buffer that a Reactor input of the JSON readers discards, e.g. one it held before it
 * was mapped to a piece, is released by the discard hook of the reader.
 */
class JsonChunkedDiscardTest {

    @Test
    void theDiscardHookReleasesANettyBuffer() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            NettyJsonStreamHandler<Object> handler = ctx.getBean(NettyJsonStreamHandler.class);
            AtomicReference<CoreSubscriber<?>> inputSubscriber = new AtomicReference<>();
            Publisher<ByteBuffer<?>> input = s -> {
                inputSubscriber.set((CoreSubscriber<?>) s);
                s.onSubscribe(Operators.emptySubscription());
            };
            handler.readChunked(Argument.OBJECT_ARGUMENT, MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(), input)
                .subscribe(new Subscriber<Object>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                    }

                    @Override
                    public void onNext(Object o) {
                    }

                    @Override
                    public void onError(Throwable t) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            ByteBuf discarded = Unpooled.buffer(4).writeInt(1);
            Operators.onDiscard(discarded, inputSubscriber.get().currentContext());
            assertEquals(0, discarded.refCnt());
        }
    }
}
