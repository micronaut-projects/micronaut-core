package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import org.reactivestreams.Publisher;

/**
 * Variant of {@link MessageBodyReader} that allows piecewise reading of the input, e.g. for
 * json-stream.<br>
 * todo: what are the semantics of {@code createSpecific}?
 *
 * @param <T> The type to read
 */
@Experimental
public interface PiecewiseMessageBodyReader<T> extends MessageBodyReader<T> {
    Publisher<T> readPiecewise(
        @NonNull Argument<T> type,
        @Nullable MediaType mediaType,
        @NonNull Headers httpHeaders,
        Publisher<ByteBuffer<?>> input
    );
}
