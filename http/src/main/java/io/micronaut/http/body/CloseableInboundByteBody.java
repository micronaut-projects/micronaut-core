package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;

import java.io.Closeable;

/**
 * A {@link Closeable} version of {@link InboundByteBody}. {@link #close()} releases any resources
 * that may still be held. No other operations on this body are valid after {@link #close()}, but
 * multiple calls to {@link #close()} are allowed (though only the first will do anything). If a
 * terminal operation (see {@link InboundByteBody}) is performed on this body, you can but do not
 * need to close it anymore. Closing becomes a no-op in that case.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Experimental
public interface CloseableInboundByteBody extends InboundByteBody, Closeable {
    /**
     * Clean up any resources held by this instance. See class documentation.
     */
    @Override
    void close();
}
