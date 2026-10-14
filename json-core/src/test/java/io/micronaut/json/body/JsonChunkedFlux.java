package io.micronaut.json.body;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * The processing of a {@link JsonChunkedProcessor} as a Flux, as it was before the piece
 * readers, for the tests of the processor.
 */
final class JsonChunkedFlux {
    private static final NettyReadBufferFactory READ_BUFFERS = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);

    private JsonChunkedFlux() {
    }

    static Flux<ReadBuffer> process(JsonChunkedProcessor processor, Flux<ByteBuf> input) {
        return Flux.concat(input
                .concatMap(b -> Flux.<ReadBuffer>create(s -> {
                    try {
                        // the processor takes over the piece, and releases it
                        processor.feed(READ_BUFFERS.adapt(b), s::next);
                        s.complete();
                    } catch (IOException | ContentLengthExceededException e) {
                        s.error(e);
                    }
                })), Flux.create(s -> {
                try {
                    processor.finish(s::next);
                    s.complete();
                } catch (Throwable e) {
                    s.error(e);
                }
            }))
            // also when the subscriber cancels, e.g. a reader that stops before the last element:
            // the partial element and the elements and input not delivered yet are released
            .doFinally(signal -> processor.discard())
            .doOnDiscard(ReadBuffer.class, ReadBuffer::close)
            .doOnDiscard(ByteBuf.class, ByteBuf::release);
    }
}
