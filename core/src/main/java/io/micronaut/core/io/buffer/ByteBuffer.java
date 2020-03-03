/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.io.buffer;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Interface to allow interfacing with different byte buffer implementations, primarily as an abstraction over Netty.
 *
 * @param <T> buffer type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ByteBuffer<T> {

    /**
     * @return The native buffer type
     */
    T asNativeBuffer();

    /**
     * Returns the number of readable bytes which is equal to
     * {@code (this.writerIndex - this.readerIndex)}.
     * @return bytes
     */
    int readableBytes();

    /**
     * Returns the number of writable bytes which is equal to
     * {@code (this.capacity - this.writerIndex)}.
     * @return The bytes
     */
    int writableBytes();

    /**
     * Returns the maximum allowed capacity of this buffer.  If a user attempts to increase the
     * capacity of this buffer beyond the maximum capacity using {@link #capacity(int)} or
     * {@link IllegalArgumentException}.
     * @return The max capacity
     */
    int maxCapacity();

    /**
     * Adjusts the capacity of this buffer.  If the {@code newCapacity} is less than the current
     * capacity, the content of this buffer is truncated.  If the {@code newCapacity} is greater
     * than the current capacity, the buffer is appended with unspecified data whose length is
     * {@code (newCapacity - currentCapacity)}.
     * @param capacity capacity
     * @return The bytebuffer
     */
    ByteBuffer capacity(int capacity);

    /**
     * Returns the {@code readerIndex} of this buffer.
     * @return The index
     */
    int readerIndex();

    /**
     * Sets the {@code readerIndex} of this buffer.
     * @param readPosition readPosition
     * @return The buffer
     * @throws IndexOutOfBoundsException if the specified {@code readerIndex} is
     *                                   less than {@code 0} or
     *                                   greater than {@code this.writerIndex}
     */
    ByteBuffer readerIndex(int readPosition);

    /**
     * Returns the {@code writerIndex} of this buffer.
     * @return The index
     */
    int writerIndex();

    /**
     * Sets the {@code writerIndex} of this buffer.
     *
     * @param position The position
     * @throws IndexOutOfBoundsException if the specified {@code writerIndex} is
     *                                   less than {@code this.readerIndex} or
     *                                   greater than {@code this.capacity}
     * @return The index as buffer
     */
    ByteBuffer writerIndex(int position);

    /**
     * Gets a byte at the current {@code readerIndex} and increases
     * the {@code readerIndex} by {@code 1} in this buffer.
     * @return bytes
     * @throws IndexOutOfBoundsException if {@code this.readableBytes} is less than {@code 1}
     */
    byte read();

    /**
     * Gets a {@link CharSequence} with the given length at the current {@code readerIndex}
     * and increases the {@code readerIndex} by the given length.
     *
     * @param length  the length to read
     * @param charset that should be used
     * @return the sequence
     * @throws IndexOutOfBoundsException if {@code length} is greater than {@code this.readableBytes}
     */
    CharSequence readCharSequence(int length, Charset charset);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code dst.length}).
     * @param destination destination
     * @return bytesBuffer
     * @throws IndexOutOfBoundsException if {@code dst.length} is greater than {@code this.readableBytes}
     */
    ByteBuffer read(byte[] destination);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code length}).
     *
     * @param destination The destination byte array
     * @param offset      the first index of the destination
     * @param length      the number of bytes to transfer
     * @return bytesBuffer
     * @throws IndexOutOfBoundsException if the specified {@code dstIndex} is less than {@code 0},
     *                                   if {@code length} is greater than {@code this.readableBytes}, or
     *                                   if {@code dstIndex + length} is greater than {@code dst.length}
     */
    ByteBuffer read(byte[] destination, int offset, int length);

    /**
     * Sets the specified byte at the current {@code writerIndex}
     * and increases the {@code writerIndex} by {@code 1} in this buffer.
     * The 24 high-order bits of the specified value are ignored.
     * @return bytesBuffer
     * @param b The byte to write
     * @throws IndexOutOfBoundsException if {@code this.writableBytes} is less than {@code 1}
     */
    ByteBuffer write(byte b);

    /**
     * Transfers the specified source array's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code src.length}).
     *
     * @param source The source bytes
     * @return bytesBuffer
     * @throws IndexOutOfBoundsException if {@code src.length} is greater than {@code this.writableBytes}
     */
    ByteBuffer write(byte[] source);

    /**
     * Transfers the specified source CharSequence's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code src.length}).
     *
     * @param source  The char sequence
     * @param charset The charset
     * @return This buffer
     */
    ByteBuffer write(CharSequence source, Charset charset);

    /**
     * Transfers the specified source array's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code length}).
     *
     * @param source The source byte array
     * @param offset the first index of the source
     * @param length the number of bytes to transfer
     * @return bytesBuffer
     * @throws IndexOutOfBoundsException if the specified {@code srcIndex} is less than {@code 0},
     *                                   if {@code srcIndex + length} is greater than
     *                                   {@code src.length}, or
     *                                   if {@code length} is greater than {@code this.writableBytes}
     */
    ByteBuffer write(byte[] source, int offset, int length);

    /**
     * Write the given {@link ByteBuffer} instances to this buffer.
     *
     * @param buffers The buffers to write
     * @return this buffer
     */
    ByteBuffer write(ByteBuffer... buffers);

    /**
     * Write the given {@link java.nio.ByteBuffer} instances to this buffer.
     *
     * @param buffers The buffers to write
     * @return this buffer
     */
    ByteBuffer write(java.nio.ByteBuffer... buffers);

    /**
     * Create a new {@code ByteBuffer} whose contents is a shared subsequence of this
     * data buffer's content.  Data between this byte buffer and the returned buffer is
     * shared; though changes in the returned buffer's position will not be reflected
     * in the reading nor writing position of this data buffer.
     *
     * @param index  the index at which to start the slice
     * @param length the length of the slice
     * @return the specified slice of this data buffer
     */
    ByteBuffer slice(int index, int length);

    /**
     * Exposes this buffer's readable bytes as an NIO {@link java.nio.ByteBuffer}.  The returned buffer
     * shares the content with this buffer, while changing the position and limit of the returned
     * NIO buffer does not affect the indexes and marks of this buffer.  This method is identical
     * to {@code buf.nioBuffer(buf.readerIndex(), buf.readableBytes())}.  This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.  Please note that the
     * returned NIO buffer will not see the changes of this buffer if this buffer is a dynamic
     * buffer and it adjusted its capacity.
     *
     * @return byteBuffer
     * @throws UnsupportedOperationException if this buffer cannot create a {@link java.nio.ByteBuffer}
     *                                       that shares the content with itself
     */
    java.nio.ByteBuffer asNioBuffer();

    /**
     * Exposes this buffer's sub-region as an NIO {@link java.nio.ByteBuffer}.  The returned buffer
     * shares the content with this buffer, while changing the position and limit of the returned
     * NIO buffer does not affect the indexes and marks of this buffer.  This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.  Please note that the
     * returned NIO buffer will not see the changes of this buffer if this buffer is a dynamic
     * buffer and it adjusted its capacity.
     * @return byteBuffer
     * @param index  The index
     * @param length The length
     * @throws UnsupportedOperationException if this buffer cannot create a {@link java.nio.ByteBuffer}
     *                                       that shares the content with itself
     */
    java.nio.ByteBuffer asNioBuffer(int index, int length);

    /**
     * Convert the {@link ByteBuffer} into an input stream.
     *
     * @return this buffer as an input stream
     */
    InputStream toInputStream();

    /**
     * Convert the {@link ByteBuffer} into an output stream.
     *
     * @return this buffer as an input stream
     */
    OutputStream toOutputStream();

    /**
     * Create a copy of the underlying storage from {@code buf} into a byte array.
     * The copy will start at {@link ByteBuffer#readerIndex()} and copy {@link ByteBuffer#readableBytes()} bytes.
     * @return byte array
     */
    byte[] toByteArray();

    /**
     * To string.
     * @param charset converted charset
     * @return string
     */
    String toString(Charset charset);

    /**
     * Find the index of the first occurrence of the given byte.
     *
     * @param b The byte to find
     * @return The index of the byte
     */
    int indexOf(byte b);

    /**
     * Get the byte at the specified index.
     *
     * @param index The index
     * @return The byte
     */
    byte getByte(int index);
}
