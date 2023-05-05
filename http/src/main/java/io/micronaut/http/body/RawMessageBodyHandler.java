package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

@Internal
@Experimental
public interface RawMessageBodyHandler<T> extends MessageBodyHandler<T>, ChunkedMessageBodyReader<T> {
    Class<T> getType();
}
