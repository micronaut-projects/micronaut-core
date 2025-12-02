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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.function.Function;

/**
 * A buffer of bytes. Can be read from exactly once. Must be either consumed fully, or
 * {@link #close() closed}.
 *
 * <h2 id="consuming">Consuming operations</h2>
 *
 * <p>Certain operations on ReadBuffers are <i>consuming</i>, meaning that they read all the
 * remaining bytes in the buffer. Those operations are called out in the javadoc. After such an
 * operation, the buffer does not need to be closed (though it's still allowed to do so).</p>
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
public abstract class ReadBuffer implements AutoCloseable {
    /**
     * Internal constructor. Extension is only allowed in micronaut-core.
     */
    @Internal
    protected ReadBuffer() {
    }

    /**
     * Returns the number of bytes that can be read from this buffer.
     *
     * @return The number of readable bytes
     * @throws IllegalStateException If this buffer is already closed or consumed
     */
    public abstract int readable();

    /**
     * Create a new independent buffer that reads the same data as this one. This buffer remains
     * unchanged.
     *
     * @return A new, independent buffer
     * @throws IllegalStateException If this buffer is already closed or consumed
     */
    @NonNull
    public abstract ReadBuffer duplicate();

    /**
     * Split this buffer in two, returning the bytes up until {@code splitPosition} as a new
     * buffer. Those bytes will be removed from this buffer. This is <i>not</i> a
     * <a href="#consuming">consuming operation</a>, even if {@code n == readable()}.
     *
     * @param splitPosition Position where to split the data
     * @return A new, independent buffer that reads the first {@code splitPosition} bytes
     * @throws IllegalStateException     If this buffer is already closed or consumed
     * @throws IndexOutOfBoundsException If {@code n > readable()}
     */
    @NonNull
    public abstract ReadBuffer split(int splitPosition) throws IndexOutOfBoundsException;

    /**
     * Create a new buffer with the same content as this one, consuming this buffer in the process.
     * If this buffer is later closed, the returned buffer remains usable.
     *
     * <p>This is a <a href="#consuming">consuming operation</a>.
     *
     * @return a new ReadBuffer containing all the bytes that were previously in this ReadBuffer
     * @throws IllegalStateException If this buffer is already closed or consumed
     */
    @NonNull
    public abstract ReadBuffer move();

    /**
     * Reads the contents of this ReadBuffer into the specified destination array, which must have
     * enough space for this buffer.
     *
     * <p>This is a <a href="#consuming">consuming operation</a>.
     *
     * @param destination the byte array to copy the contents into
     * @param offset      the starting index in the destination array
     * @throws IllegalStateException     If this buffer is already closed or consumed
     * @throws IndexOutOfBoundsException if the destination array is not large
     *                                   enough to hold the contents of this ReadBuffer, or if the
     *                                   offset is negative or exceeds the destination array's
     *                                   length
     */
    public abstract void toArray(byte @NonNull [] destination, int offset) throws IndexOutOfBoundsException;

    /**
     * Returns the contents of this ReadBuffer as a byte array. Some implementations may share the
     * array with other buffers, so the returned array <b>should not be written to</b>.
     *
     * <p>This is a <a href="#consuming">consuming operation</a>.
     *
     * @return a byte array containing the contents of this ReadBuffer
     * @throws IllegalStateException If this buffer is already closed or consumed
     */
    public byte @NonNull [] toArray() {
        byte[] bytes = new byte[readable()];
        toArray(bytes, 0);
        return bytes;
    }

    /**
     * Returns the contents of this ReadBuffer as a string.
     *
     * <p>This is a <a href="#consuming">consuming operation</a>.
     *
     * @param charset the character encoding to use for converting the bytes to
     *                a string
     * @return a string representation of the contents of this ReadBuffer
     * @throws IllegalStateException If this buffer is already closed or consumed
     */
    @NonNull
    public String toString(Charset charset) {
        return new String(this.toArray(), charset);
    }

    /**
     * Converts this {@link ReadBuffer} into a {@link ByteBuffer}. The returned
     * {@link ByteBuffer} will contain all the readable bytes from this {@link ReadBuffer}.
     *
     * <p>This is a <a href="#consuming">consuming operation</a>.
     *
     * @return a {@link ByteBuffer} containing the contents of this {@link ReadBuffer}
     * @throws IllegalStateException If this buffer is already closed or consumed
     */
    @NonNull
    public ByteBuffer<?> toByteBuffer() {
        return new ByteArrayByteBuffer(toArray());
    }

    /**
     * Create a new {@link InputStream} that reads the data of this buffer. The returned stream
     * must be closed.
     *
     * <p>This is a <a href="#consuming">consuming operation</a>.
     *
     * @return A stream reading from this buffer
     * @throws IllegalStateException If this buffer is already closed or consumed
     */
    @NonNull
    public InputStream toInputStream() {
        return new ByteArrayInputStream(toArray());
    }

    /**
     * Access the contents of this buffer as a {@link java.nio.ByteBuffer} with
     * {@link java.nio.ByteBuffer#hasArray()}, if doing so is possible without copying the data.
     * The lifetime of the buffer is limited to the function scope, user code must not keep it
     * around. User code must also never modify the backing array of the buffer.
     *
     * <p>This is useful for performing operations on the data that can take a (array, offset,
     * length) parameter, such as {@link OutputStream#write(byte[], int, int)} or serialization.
     *
     * <p>This is a <a href="#consuming">consuming operation</a> if the function is called.
     *
     * @param function A function to call with a nio buffer view of this {@link ReadBuffer}, if
     *                 possible
     * @return The return value of the function, or {@code null} if this buffer cannot be accessed
     * using a nio buffer
     * @param <R> The return type of the function
     * @throws IllegalStateException If this buffer is already closed or consumed
     * @since 5.0.0
     */
    @Nullable
    public <R> R useFastHeapBuffer(@NonNull Function<java.nio.@NonNull ByteBuffer, @NonNull R> function) {
        return null;
    }

    /**
     * Write this buffer to the given {@link OutputStream}.
     *
     * <p>This is a <a href="#consuming">consuming operation</a>.
     *
     * @param stream The stream to write to
     * @throws IllegalStateException If this buffer is already closed or consumed
     * @throws IOException           If the {@link OutputStream} throws an exception
     */
    public void transferTo(@NonNull OutputStream stream) throws IOException {
        // if possible, write using array directly
        if (useFastHeapBuffer(bb -> {
            try {
                stream.write(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
            } catch (IOException e) {
                sneakyThrow(e);
            }
            return true;
        }) == null) {
            // fall back to copying
            stream.write(toArray());
        }
    }

    private static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    /**
     * Closes this ReadBuffer, releasing any system resources associated with it. May be called
     * multiple times without effect. Note that <a href="#consuming">consuming operations</a>
     * implicitly close the buffer as well.
     */
    @Override
    public abstract void close();

    // Protected methods for toString. Please don't make them public for now, they don't fit into
    // the API design well

    protected abstract boolean isConsumed();

    protected abstract byte[] peekArray(int n);

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        if (isConsumed()) {
            sb.append("[consumed]");
        } else {
            int readable = readable();
            sb.append("[len=").append(readable).append(", data='");
            byte[] bytes = peekArray(Math.min(readable, 32));
            for (byte b : bytes) {
                if (b < 0x20 || b > 0x7e) {
                    sb.append("\\x");
                    int i = b & 0xff;
                    if (i < 0x10) {
                        sb.append('0');
                    }
                    sb.append(Integer.toHexString(i));
                } else {
                    sb.append((char) b);
                }
            }
            sb.append("'");
            if (readable > bytes.length) {
                sb.append('…');
            }
            sb.append("]");
        }

        return sb.toString();
    }
}
