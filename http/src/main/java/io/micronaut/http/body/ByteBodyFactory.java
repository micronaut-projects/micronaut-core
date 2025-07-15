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

import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.functional.ThrowingConsumer;
import io.micronaut.http.body.stream.AvailableByteArrayBody;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Factory methods for {@link ByteBody}s.
 * <p>While this is public API, extension is only allowed by micronaut-core.
 *
 * @author Jonas Konrad
 * @since 4.8.0
 */
@Experimental
public class ByteBodyFactory {
    private final ByteBufferFactory<?, ?> byteBufferFactory;

    /**
     * Internal constructor.
     *
     * @param byteBufferFactory The buffer factory
     */
    @Internal
    protected ByteBodyFactory(@NonNull ByteBufferFactory<?, ?> byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    /**
     * Create a default body factory. Where possible, prefer using an existing factory that may
     * have runtime-specific optimizations, such as the factory passed to
     * {@link ResponseBodyWriter}.
     *
     * @param byteBufferFactory The base buffer factory
     * @return The body factory
     */
    @NonNull
    public static ByteBodyFactory createDefault(@NonNull ByteBufferFactory<?, ?> byteBufferFactory) {
        return new ByteBodyFactory(byteBufferFactory);
    }

    /**
     * Get the underlying {@link ByteBufferFactory}. Where possible, prefer using methods on the
     * body factory directly.
     *
     * @return The buffer factory
     */
    @NonNull
    public final ByteBufferFactory<?, ?> byteBufferFactory() {
        return byteBufferFactory;
    }

    /**
     * Create a new {@link CloseableAvailableByteBody} from the given buffer. Ownership of the
     * buffer is transferred to this method; the original buffer may be copied or used as-is
     * depending on implementation. If the buffer is {@link ReferenceCounted}, release ownership is
     * also transferred to this method.
     *
     * @param buffer The buffer
     * @return A {@link ByteBody} with the same content as the buffer
     */
    @NonNull
    public CloseableAvailableByteBody adapt(@NonNull ByteBuffer<?> buffer) {
        byte[] byteArray = buffer.toByteArray();
        if (buffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return adapt(byteArray);
    }

    /**
     * Create a new {@link CloseableAvailableByteBody} from the given array. Ownership of the array
     * is transferred to this method; the array may be copied or used as-is, so do not modify the
     * array after passing it to this method.
     *
     * @param array The array
     * @return A {@link ByteBody} with the same content as the array
     */
    @NonNull
    public CloseableAvailableByteBody adapt(byte @NonNull [] array) {
        return AvailableByteArrayBody.create(byteBufferFactory(), array);
    }

    /**
     * Buffer any data written to an {@link OutputStream} and return it as a {@link ByteBody}.
     *
     * @param writer The function that will write to the {@link OutputStream}
     * @return The data written to the stream
     * @param <T> Exception type thrown by the consumer
     * @throws T Exception thrown by the consumer
     */
    @NonNull
    public <T extends Throwable> CloseableAvailableByteBody buffer(@NonNull ThrowingConsumer<? super OutputStream, T> writer) throws T {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        writer.accept(s);
        return AvailableByteArrayBody.create(byteBufferFactory(), s.toByteArray());
    }

    /**
     * Create a new {@link OutputStream} that buffers into a {@link CloseableAvailableByteBody}.
     * Used like this:
     *
     * <pre>{@code
     * CloseableAvailableByteBody body;
     * try (BufferingOutputStream bos = byteBodyFactory.outputStreamBuffer()) {
     *     bos.stream().write(123);
     *     // ...
     *     body = bos.finishBuffer();
     * }
     * // use body
     * }</pre>
     *
     * <p>Note that for simple use cases, {@link #buffer(ThrowingConsumer)} may be a bit more
     * convenient, but this method offers more control over the stream lifecycle.
     *
     * @return The {@link BufferingOutputStream} wrapper
     * @since 4.10.0
     */
    @NonNull
    public BufferingOutputStream outputStreamBuffer() {
        return new BufferingOutputStream() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            @Override
            public OutputStream stream() {
                OutputStream out = this.out;
                if (out == null) {
                    throw new IllegalStateException("Already converted to buffer");
                }
                return out;
            }

            @Override
            public CloseableAvailableByteBody finishBuffer() {
                ByteArrayOutputStream out = this.out;
                if (out == null) {
                    throw new IllegalStateException("Already converted to buffer");
                }
                this.out = null;
                return AvailableByteArrayBody.create(byteBufferFactory(), out.toByteArray());
            }

            @Override
            public void close() {
                this.out = null;
            }
        };
    }

    /**
     * Create an empty body.
     *
     * @return The empty body
     */
    @NonNull
    public CloseableAvailableByteBody createEmpty() {
        return adapt(ArrayUtils.EMPTY_BYTE_ARRAY);
    }

    /**
     * Encode the given {@link CharSequence} and create a {@link ByteBody} from it.
     *
     * @param cs      The input string
     * @param charset The charset to use for encoding
     * @return The encoded body
     */
    @NonNull
    public CloseableAvailableByteBody copyOf(@NonNull CharSequence cs, @NonNull Charset charset) {
        return adapt(cs.toString().getBytes(charset));
    }

    /**
     * Copy the data of the given {@link InputStream} into an available {@link ByteBody}. If the
     * input is blocking, this method will also block.
     *
     * @param stream The input to copy
     * @return A body containing the data read from the input
     * @throws IOException Any exception thrown by the {@link InputStream} read methods
     */
    @NonNull
    @Blocking
    public CloseableAvailableByteBody copyOf(@NonNull InputStream stream) throws IOException {
        return adapt(stream.readAllBytes());
    }

    /**
     * Wrapper around a {@link OutputStream} that buffers into a
     * {@link CloseableAvailableByteBody}. Must be closed after use, even if
     * {@link #finishBuffer()} has not been called (e.g. on error), to avoid resource leaks.
     *
     * @since 4.10.0
     */
    public interface BufferingOutputStream extends Closeable {
        /**
         * Get the stream you can write to.
         *
         * @return The {@link OutputStream}
         * @throws IllegalStateException If the buffer has already
         * {@link #finishBuffer() been finalized}
         */
        @NonNull
        OutputStream stream() throws IllegalStateException;

        /**
         * Finalize this buffer, returning it as a {@link CloseableAvailableByteBody}. This method
         * can only be called once. Release ownership of the buffer transfers to the caller:
         * Closing this {@link BufferingOutputStream} after this method has been called will
         * <i>not</i> close the returned {@link CloseableAvailableByteBody}.
         *
         * @return The finished buffer
         * @throws IOException If there was an exception finishing up the buffer
         * @throws IllegalStateException If this method has already been called
         */
        @NonNull
        CloseableAvailableByteBody finishBuffer() throws IOException, IllegalStateException;

        /**
         * Close this buffer. {@link #finishBuffer()} cannot be called after this method. If it has
         * not been called yet, the content of this buffer will be discarded.
         *
         * @throws IOException If there was an exception finishing up the buffer
         */
        @Override
        void close() throws IOException;
    }
}
