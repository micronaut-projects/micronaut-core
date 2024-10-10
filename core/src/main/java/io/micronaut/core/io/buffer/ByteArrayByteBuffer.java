/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * A {@link ByteBuffer} implementation that is backed by a byte array.
 *
 * @since 4.7
 */
@Internal
@Experimental
public final class ByteArrayByteBuffer implements ByteBuffer<byte[]> {

    private final byte[] underlyingBytes;
    private int readerIndex;
    private int writerIndex;

    /**
     * Construct a new {@link ByteArrayByteBuffer} for the given bytes.
     *
     * @param underlyingBytes the bytes to wrap
     */
    ByteArrayByteBuffer(byte[] underlyingBytes) {
        this(underlyingBytes, underlyingBytes.length);
    }

    /**
     * Construct a new {@link ByteArrayByteBuffer} for the given bytes and capacity.
     * If capacity is greater than the length of the underlyingBytes, extra bytes will be zeroed out.
     * If capacity is less than the length of the underlyingBytes, the underlyingBytes will be truncated.
     *
     * @param underlyingBytes the bytes to wrap
     * @param capacity        the capacity of the buffer
     */
    ByteArrayByteBuffer(byte[] underlyingBytes, int capacity) {
        if (capacity < underlyingBytes.length) {
            this.underlyingBytes = Arrays.copyOf(underlyingBytes, capacity);
        } else if (capacity > underlyingBytes.length) {
            this.underlyingBytes = new byte[capacity];
            System.arraycopy(underlyingBytes, 0, this.underlyingBytes, 0, underlyingBytes.length);
        } else {
            this.underlyingBytes = underlyingBytes;
        }
    }

    @Override
    public byte[] asNativeBuffer() {
        return underlyingBytes;
    }

    @Override
    public int readableBytes() {
        return underlyingBytes.length - readerIndex;
    }

    @Override
    public int writableBytes() {
        return underlyingBytes.length - writerIndex;
    }

    @Override
    public int maxCapacity() {
        return underlyingBytes.length;
    }

    @Override
    public ByteArrayByteBuffer capacity(int capacity) {
        return new ByteArrayByteBuffer(underlyingBytes, capacity);
    }

    @Override
    public int readerIndex() {
        return readerIndex;
    }

    @Override
    public ByteArrayByteBuffer readerIndex(int readPosition) {
        this.readerIndex = Math.min(readPosition, underlyingBytes.length);
        return this;
    }

    @Override
    public int writerIndex() {
        return writerIndex;
    }

    @Override
    public ByteArrayByteBuffer writerIndex(int position) {
        this.writerIndex = Math.min(position, underlyingBytes.length);
        return this;
    }

    @Override
    public byte read() {
        return underlyingBytes[readerIndex++];
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        String s = new String(underlyingBytes, readerIndex, length, charset);
        readerIndex += length;
        return s;
    }

    @Override
    public ByteArrayByteBuffer read(byte[] destination) {
        int count = Math.min(readableBytes(), destination.length);
        System.arraycopy(underlyingBytes, readerIndex, destination, 0, count);
        readerIndex += count;
        return this;
    }

    @Override
    public ByteArrayByteBuffer read(byte[] destination, int offset, int length) {
        int count = Math.min(readableBytes(), Math.min(destination.length - offset, length));
        System.arraycopy(underlyingBytes, readerIndex, destination, offset, count);
        readerIndex += count;
        return this;
    }

    @Override
    public ByteArrayByteBuffer write(byte b) {
        underlyingBytes[writerIndex++] = b;
        return this;
    }

    @Override
    public ByteArrayByteBuffer write(byte[] source) {
        int count = Math.min(writableBytes(), source.length);
        System.arraycopy(source, 0, underlyingBytes, writerIndex, count);
        writerIndex += count;
        return this;
    }

    @Override
    public ByteArrayByteBuffer write(CharSequence source, Charset charset) {
        write(source.toString().getBytes(charset));
        return this;
    }

    @Override
    public ByteArrayByteBuffer write(byte[] source, int offset, int length) {
        int count = Math.min(writableBytes(), length);
        System.arraycopy(source, offset, underlyingBytes, writerIndex, count);
        writerIndex += count;
        return this;
    }

    @Override
    public ByteArrayByteBuffer write(ByteBuffer... buffers) {
        for (ByteBuffer<?> buffer : buffers) {
            write(buffer.toByteArray());
        }
        return this;
    }

    @Override
    public ByteArrayByteBuffer write(java.nio.ByteBuffer... buffers) {
        for (java.nio.ByteBuffer buffer : buffers) {
            write(buffer.array());
        }
        return this;
    }

    @Override
    public ByteArrayByteBuffer slice(int index, int length) {
        return new ByteArrayByteBuffer(Arrays.copyOfRange(underlyingBytes, index, index + length), length);
    }

    @Override
    public java.nio.ByteBuffer asNioBuffer() {
        return java.nio.ByteBuffer.wrap(underlyingBytes, readerIndex, readableBytes());
    }

    @Override
    public java.nio.ByteBuffer asNioBuffer(int index, int length) {
        return java.nio.ByteBuffer.wrap(underlyingBytes, index, length);
    }

    @Override
    public InputStream toInputStream() {
        return new ByteArrayInputStream(underlyingBytes, readerIndex, readableBytes());
    }

    @Override
    public OutputStream toOutputStream() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public byte[] toByteArray() {
        return Arrays.copyOfRange(underlyingBytes, readerIndex, readableBytes());
    }

    @Override
    public String toString(Charset charset) {
        return new String(underlyingBytes, readerIndex, readableBytes(), charset);
    }

    @Override
    public int indexOf(byte b) {
        for (int i = readerIndex; i < underlyingBytes.length; i++) {
            if (underlyingBytes[i] == b) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public byte getByte(int index) {
        return underlyingBytes[index];
    }
}
