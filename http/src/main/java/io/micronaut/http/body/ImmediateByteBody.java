/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.io.buffer.ByteBuffer;
import org.reactivestreams.Publisher;

import java.nio.charset.Charset;
import java.util.OptionalLong;

/**
 * This is an extension of {@link ByteBody} when the entire body is immediately available
 * (without waiting). It has the same semantics as {@link ByteBody}, but it adds a few other
 * primary operations for convenience.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Experimental
public interface ImmediateByteBody extends ByteBody {
    /**
     * For immediate buffers, backpressure is not relevant, so the backpressure modes passed to
     * {@link #split(SplitBackpressureMode)} are ignored. You can use this method always.
     *
     * @see ByteBody#split()
     * @return A body with the same content as this one
     */
    @NonNull
    CloseableImmediateByteBody split();

    /**
     * This method is equivalent to {@link #split()}, the backpressure parameter is ignored.
     *
     * @param backpressureMode ignored
     * @return A body with the same content as this one
     * @deprecated This method is unnecessary for {@link ImmediateByteBody}. Use
     * {@link #split()} directly.
     */
    @Override
    @Deprecated
    default @NonNull CloseableImmediateByteBody split(@NonNull SplitBackpressureMode backpressureMode) {
        return split();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated This method is unnecessary for {@link ImmediateByteBody}, it does nothing.
     */
    @Override
    @NonNull
    @Deprecated
    default ImmediateByteBody allowDiscard() {
        return this;
    }

    /**
     * The length in bytes of the body.
     *
     * @return The length
     * @see ByteBody#expectedLength()
     */
    long length();

    /**
     * The length in bytes of the body. Never returns {@link OptionalLong#empty()} for
     * {@link ImmediateByteBody}. Use {@link #length()} directly instead.
     *
     * @return The length
     * @deprecated This method is unnecessary for {@link ImmediateByteBody}. Use
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
     * @deprecated This method is unnecessary for {@link ImmediateByteBody}. Use
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
     * @deprecated This method is unnecessary for {@link ImmediateByteBody}. Use
     * {@link #toByteArray()} directly.
     */
    @Override
    @NonNull
    default Publisher<byte[]> toByteArrayPublisher() {
        return Publishers.just(toByteArray());
    }
}
