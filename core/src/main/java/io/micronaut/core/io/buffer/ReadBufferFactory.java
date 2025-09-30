/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.core.io.buffer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.functional.ThrowingConsumer;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Factory for {@link ReadBuffer}s.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
public class ReadBufferFactory {
    private static final ReadBufferFactory INSTANCE = new ReadBufferFactory();

    /**
     * Internal constructor. Extension is only allowed in micronaut-core.
     */
    @Internal
    protected ReadBufferFactory() {
    }

    /**
     * Get the default {@link ReadBufferFactory} backed by JDK-only data structures.
     *
     * @return The factory
     */
    @NonNull
    public static ReadBufferFactory getJdkFactory() {
        return INSTANCE;
    }

    /**
     * Create an empty {@link ReadBuffer}.
     *
     * @return An empty buffer
     */
    @NonNull
    public ReadBuffer createEmpty() {
        return adapt(ByteBuffer.allocate(0));
    }

    /**
     * Create a new buffer containing the given text.
     *
     * @param cs      The input text
     * @param charset The charset to use for encoding
     * @return The text buffer
     */
    public @NonNull ReadBuffer copyOf(@NonNull CharSequence cs, @NonNull Charset charset) {
        return adapt(cs.toString().getBytes(charset));
    }

    /**
     * Create a new buffer containing all data from the given stream. This is a blocking operation.
     *
     * @param stream The stream to read from
     * @return The buffer
     */
    public @NonNull ReadBuffer copyOf(@NonNull InputStream stream) throws IOException {
        return adapt(stream.readAllBytes());
    }

    /**
     * Create a buffer, copying the given input data.
     *
     * @param nioBuffer A NIO buffer to read data from
     * @return The copied buffer
     */
    @NonNull
    public ReadBuffer copyOf(@NonNull ByteBuffer nioBuffer) {
        ByteBuffer copy = ByteBuffer.allocate(nioBuffer.remaining());
        copy.put(nioBuffer.slice());
        copy.flip();
        return adapt(copy);
    }

    /**
     * Create a buffer with the given input data. Whether the data is copied or used as-is is
     * implementation-defined. Ownership of the given buffer transfers to this class, so it should
     * not be modified elsewhere after this method is called.
     *
     * @param nioBuffer A NIO buffer
     * @return The adapted buffer
     */
    @NonNull
    public ReadBuffer adapt(@NonNull ByteBuffer nioBuffer) {
        return new NioReadBuffer(nioBuffer);
    }

    /**
     * Create a buffer with the given input data. Whether the data is copied or used as-is is
     * implementation-defined. Ownership of the given buffer transfers to this class, so it should
     * not be modified elsewhere after this method is called. If the input buffer is
     * {@link ReferenceCounted reference counted}, release ownership also transfers to this class.
     *
     * @param buffer A buffer
     * @return The adapted buffer
     */
    @NonNull
    public ReadBuffer adapt(@NonNull io.micronaut.core.io.buffer.ByteBuffer<?> buffer) {
        byte[] byteArray = buffer.toByteArray();
        if (buffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return adapt(byteArray);
    }

    /**
     * Create a buffer with the given input data. Whether the data is copied or used as-is is
     * implementation-defined. Ownership of the given array transfers to this class, so it should
     * not be modified elsewhere after this method is called.
     *
     * @param array A byte array
     * @return The adapted buffer
     */
    @NonNull
    public ReadBuffer adapt(byte @NonNull [] array) {
        return adapt(ByteBuffer.wrap(array));
    }

    /**
     * Write to a new buffer using an {@link OutputStream}. When the given writer completes, the
     * written data is combined into a {@link ReadBuffer} that is then returned.
     *
     * @param writer The writer
     * @param <T>    An exception thrown by the writer
     * @return The written data
     * @throws T If the writer throws an exception
     */
    @NonNull
    public <T extends Throwable> ReadBuffer buffer(@NonNull ThrowingConsumer<@NonNull ? super OutputStream, T> writer) throws T {
        var s = new NoCopyByteArrayOutputStream(NoCopyByteArrayOutputStream.DEFAULT_CAPACITY);
        writer.accept(s);
        return adapt(s.toByteBuffer());
    }

    /**
     * Create a new {@link OutputStream} that buffers into a {@link ReadBuffer}.
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
     * @return The {@link ReadBufferFactory.BufferingOutputStream} wrapper
     */
    @NonNull
    public ReadBufferFactory.BufferingOutputStream outputStreamBuffer() {
        return outputStreamBuffer(NoCopyByteArrayOutputStream.DEFAULT_CAPACITY);
    }

    @NonNull
    private BufferingOutputStream outputStreamBuffer(int capacity) {
        return new BufferingOutputStream() {
            NoCopyByteArrayOutputStream out = new NoCopyByteArrayOutputStream(capacity);

            @Override
            public OutputStream stream() {
                OutputStream out = this.out;
                if (out == null) {
                    throw new IllegalStateException("Already converted to buffer");
                }
                return out;
            }

            @Override
            public ReadBuffer finishBuffer() {
                NoCopyByteArrayOutputStream out = this.out;
                if (out == null) {
                    throw new IllegalStateException("Already converted to buffer");
                }
                this.out = null;
                return adapt(out.toByteBuffer());
            }

            @Override
            public void close() {
                this.out = null;
            }
        };
    }

    /**
     * Create a new composite buffer out of the given collection of buffers. This operation
     * consumes all input buffers, even if there is an exception along the way.
     *
     * @param buffers The input buffers to compose
     * @return The composite buffer
     * @throws IllegalStateException If any given buffer is already closed or consumed
     */
    @NonNull
    public ReadBuffer compose(@NonNull Iterable<@NonNull ReadBuffer> buffers) {
        try {
            int capacity = 0;
            for (ReadBuffer buffer : buffers) {
                capacity = Math.addExact(capacity, buffer.readable());
            }
            try (BufferingOutputStream bos = outputStreamBuffer(capacity)) {
                for (ReadBuffer buffer : buffers) {
                    buffer.transferTo(bos.stream());
                }
                return bos.finishBuffer();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } catch (Throwable e) {
            for (ReadBuffer buffer : buffers) {
                try {
                    buffer.close();
                } catch (Throwable f) {
                    e.addSuppressed(f);
                }
            }
            throw e;
        }
    }

    /**
     * Wrapper around a {@link OutputStream} that buffers into a
     * {@link ReadBuffer}. Must be closed after use, even if
     * {@link #finishBuffer()} has not been called (e.g. on error), to avoid resource leaks.
     */
    public interface BufferingOutputStream extends Closeable {
        /**
         * Get the stream you can write to. May be called multiple times.
         *
         * @return The {@link OutputStream}
         * @throws IllegalStateException If the buffer has already
         *                               {@link #finishBuffer() been finalized}
         */
        @NonNull
        OutputStream stream() throws IllegalStateException;

        /**
         * Finalize this buffer, returning it as a {@link ReadBuffer}. This method
         * can only be called once. Release ownership of the buffer transfers to the caller:
         * Closing this {@link BufferingOutputStream} after this method has been called will
         * <i>not</i> close the returned {@link ReadBuffer}.
         *
         * @return The finished buffer
         * @throws IOException           If there was an exception finishing up the buffer
         * @throws IllegalStateException If this method has already been called
         */
        @NonNull
        ReadBuffer finishBuffer() throws IOException, IllegalStateException;

        /**
         * Close this buffer. {@link #finishBuffer()} cannot be called after this method. If it has
         * not been called yet, the content of this buffer will be discarded.
         *
         * @throws IOException If there was an exception finishing up the buffer
         */
        @Override
        void close() throws IOException;
    }

    private static final class NoCopyByteArrayOutputStream extends ByteArrayOutputStream {
        static final int DEFAULT_CAPACITY = 32; // default ByteArrayOutputStream parameter

        NoCopyByteArrayOutputStream(int capacity) {
            super(capacity);
        }

        ByteBuffer toByteBuffer() {
            return ByteBuffer.wrap(buf, 0, count);
        }
    }
}
