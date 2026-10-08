package io.micronaut.http.netty.body;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * The processing of a {@link JsonChunkedProcessor} as a Flux, as it was before the piece
 * readers, for the tests of the processor.
 */
final class JsonChunkedFlux {
    private JsonChunkedFlux() {
    }

    static Flux<ByteBuffer<?>> process(JsonChunkedProcessor processor, Flux<ByteBuf> input) {
        return Flux.concat(input
                .concatMap(b -> Flux.<ByteBuffer<?>>create(s -> {
                    try {
                        processor.feed(b, s::next);
                        s.complete();
                    } catch (IOException | ContentLengthExceededException e) {
                        s.error(e);
                    } finally {
                        b.release();
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
            .doOnDiscard(ByteBuffer.class, JsonChunkedProcessor::release)
            .doOnDiscard(ByteBuf.class, ByteBuf::release);
    }
}
