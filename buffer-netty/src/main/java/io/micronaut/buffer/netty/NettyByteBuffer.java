/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.buffer.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferInputStream;
import io.netty5.buffer.BufferOutputStream;
import io.netty5.buffer.BufferUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * A {@link ByteBuffer} implementation for Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class NettyByteBuffer implements ByteBuffer<Buffer>, AutoCloseable {

    private Buffer delegate;

    /**
     * @param delegate The {@link Buffer}
     */
    NettyByteBuffer(Buffer delegate) {
        this.delegate = delegate;
    }

    @Override
    public Buffer asNativeBuffer() {
        return delegate;
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public int readableBytes() {
        return delegate.readableBytes();
    }

    @Override
    public int writableBytes() {
        return delegate.writableBytes();
    }

    @Override
    public int maxCapacity() {
        return delegate.implicitCapacityLimit();
    }

    @Override
    public ByteBuffer capacity(int capacity) {
        throw new UnsupportedOperationException(); // todo
    }

    @Override
    public int readerIndex() {
        return delegate.readerOffset();
    }

    @Override
    public ByteBuffer readerIndex(int readPosition) {
        delegate.readerOffset(readPosition);
        return this;
    }

    @Override
    public int writerIndex() {
        return delegate.writerOffset();
    }

    @Override
    public ByteBuffer writerIndex(int position) {
        delegate.writerOffset(position);
        return this;
    }

    @Override
    public byte read() {
        return delegate.readByte();
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        return delegate.readCharSequence(length, charset);
    }

    @Override
    public ByteBuffer read(byte[] destination) {
        return read(destination, 0, destination.length);
    }

    @Override
    public ByteBuffer read(byte[] destination, int offset, int length) {
        delegate.readBytes(destination, offset, length);
        return this;
    }

    @Override
    public ByteBuffer write(byte b) {
        delegate.writeByte(b);
        return this;
    }

    @Override
    public ByteBuffer write(byte[] source) {
        delegate.writeBytes(source);
        return this;
    }

    @Override
    public ByteBuffer write(CharSequence source, Charset charset) {
        delegate.writeCharSequence(source, charset);
        return this;
    }

    @Override
    public ByteBuffer write(byte[] source, int offset, int length) {
        delegate.writeBytes(source, offset, length);
        return this;
    }

    @Override
    public ByteBuffer write(ByteBuffer... buffers) {
        throw new UnsupportedOperationException(); // todo
    }

    @Override
    public ByteBuffer write(java.nio.ByteBuffer... buffers) {
        throw new UnsupportedOperationException(); // todo
    }

    /**
     * @param byteBufs The {@link ByteBuf}s
     * @return The {@link ByteBuffer}
     */
    public ByteBuffer write(Buffer... byteBufs) {
        throw new UnsupportedOperationException(); // todo
    }

    @Override
    public ByteBuffer slice(int index, int length) {
        throw new UnsupportedOperationException(); // todo
    }

    @Override
    public java.nio.ByteBuffer asNioBuffer() {
        throw new UnsupportedOperationException(); // todo
    }

    @Override
    public java.nio.ByteBuffer asNioBuffer(int index, int length) {
        throw new UnsupportedOperationException(); // todo
    }

    @Override
    public InputStream toInputStream() {
        return new BufferInputStream(delegate.send());
    }

    @Override
    public OutputStream toOutputStream() {
        return new BufferOutputStream(delegate);
    }

    @Override
    public byte[] toByteArray() {
        return BufferUtil.getBytes(delegate);
    }

    @Override
    public String toString(Charset charset) {
        return delegate.toString(charset);
    }

    @Override
    public int indexOf(byte b) {
        return delegate.bytesBefore(b);
    }

    @Override
    public byte getByte(int index) {
        return delegate.getByte(index);
    }
}
