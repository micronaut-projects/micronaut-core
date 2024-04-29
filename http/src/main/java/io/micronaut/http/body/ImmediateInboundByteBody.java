package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.io.buffer.ByteBuffer;
import org.reactivestreams.Publisher;

import java.nio.charset.Charset;
import java.util.OptionalLong;

/**
 * This is an extension of {@link InboundByteBody} when the entire body is immediately available
 * (without waiting). It has the same semantics as {@link InboundByteBody}, but it adds a few other
 * primary operations for convenience.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Experimental
public interface ImmediateInboundByteBody extends InboundByteBody {
    /**
     * For immediate buffers, backpressure is not relevant, so the backpressure modes passed to
     * {@link #split(SplitBackpressureMode)} are ignored. You can use this method always.
     *
     * @see InboundByteBody#split()
     * @return A body with the same content as this one
     */
    @NonNull
    CloseableImmediateInboundByteBody split();

    /**
     * This method is equivalent to {@link #split()}, the backpressure parameter is ignored.
     *
     * @param backpressureMode ignored
     * @return A body with the same content as this one
     * @deprecated This method is unnecessary for {@link ImmediateInboundByteBody}. Use
     * {@link #split()} directly.
     */
    @Override
    @Deprecated
    default @NonNull CloseableImmediateInboundByteBody split(@NonNull SplitBackpressureMode backpressureMode) {
        return split();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated This method is unnecessary for {@link ImmediateInboundByteBody}, it does nothing.
     */
    @Override
    @NonNull
    @Deprecated
    default ImmediateInboundByteBody allowDiscard() {
        return this;
    }

    /**
     * The length in bytes of the body.
     *
     * @return The length
     * @see InboundByteBody#expectedLength()
     */
    long length();

    /**
     * The length in bytes of the body. Never returns {@link OptionalLong#empty()} for
     * {@link ImmediateInboundByteBody}. Use {@link #length()} directly instead.
     *
     * @return The length
     * @deprecated This method is unnecessary for {@link ImmediateInboundByteBody}. Use
     * {@link #length()} directly.
     */
    @Override
    @NonNull
    @Deprecated
    default OptionalLong expectedLength() {
        return OptionalLong.of(length());
    }

    /**
     * Get this body as a byte array.
     * <p>This is a primary operation. After this operation, no other primary operation or
     * {@link #split()} may be done.
     *
     * @return The bytes
     */
    byte @NonNull [] toByteArray();

    /**
     * Get this body as a {@link ByteBuffer}. Note that the buffer may be
     * {@link io.micronaut.core.io.buffer.ReferenceCounted reference counted}, and the caller must
     * take care of releasing it.
     * <p>This is a primary operation. After this operation, no other primary operation or
     * {@link #split()} may be done.
     *
     * @return The bytes
     */
    @NonNull
    ByteBuffer<?> toByteBuffer();

    /**
     * Convert this body to a string with the given charset.
     * <p>This is a primary operation. After this operation, no other primary operation or
     * {@link #split()} may be done.
     *
     * @return The body as a string
     */
    @NonNull
    default String toString(@NonNull Charset charset) {
        return new String(toByteArray(), charset);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated This method is unnecessary for {@link ImmediateInboundByteBody}. Use
     * {@link #toByteBuffer()} directly.
     */
    @Override
    @NonNull
    @Deprecated
    default Publisher<ByteBuffer<?>> toByteBufferPublisher() {
        return Publishers.just(toByteBuffer());
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated This method is unnecessary for {@link ImmediateInboundByteBody}. Use
     * {@link #toByteArray()} directly.
     */
    @Override
    @NonNull
    default Publisher<byte[]> toByteArrayPublisher() {
        return Publishers.just(toByteArray());
    }
}
