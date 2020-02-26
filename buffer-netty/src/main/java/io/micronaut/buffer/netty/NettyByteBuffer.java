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
package io.micronaut.buffer.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.util.ArrayUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * A {@link ByteBuffer} implementation for Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class NettyByteBuffer implements ByteBuffer<ByteBuf>, ReferenceCounted {

    private ByteBuf delegate;

    /**
     * @param delegate The {@link ByteBuf}
     */
    NettyByteBuffer(ByteBuf delegate) {
        this.delegate = delegate;
    }

    @Override
    public ByteBuffer retain() {
        delegate.retain();
        return this;
    }

    @Override
    public ByteBuf asNativeBuffer() {
        return delegate;
    }

    @Override
    public boolean release() {
        return delegate.release();
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
        return delegate.maxCapacity();
    }

    @Override
    public ByteBuffer capacity(int capacity) {
        delegate.capacity(capacity);
        return this;
    }

    @Override
    public int readerIndex() {
        return delegate.readerIndex();
    }

    @Override
    public ByteBuffer readerIndex(int readPosition) {
        delegate.readerIndex(readPosition);
        return this;
    }

    @Override
    public int writerIndex() {
        return delegate.writerIndex();
    }

    @Override
    public ByteBuffer writerIndex(int position) {
        delegate.writerIndex(position);
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
        delegate.readBytes(destination);
        return this;
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
        if (ArrayUtils.isNotEmpty(buffers)) {
            ByteBuf[] byteBufs = Arrays.stream(buffers)
                .map(buffer -> {
                    if (buffer instanceof NettyByteBuffer) {
                        return ((NettyByteBuffer) buffer).asNativeBuffer();
                    } else {
                        return Unpooled.wrappedBuffer(buffer.asNioBuffer());
                    }
                }).toArray(ByteBuf[]::new);
            return write(byteBufs);
        }
        return this;
    }

    @Override
    public ByteBuffer write(java.nio.ByteBuffer... buffers) {
        if (ArrayUtils.isNotEmpty(buffers)) {
            ByteBuf[] byteBufs = Arrays.stream(buffers)
                .map(Unpooled::wrappedBuffer).toArray(ByteBuf[]::new);
            return write(byteBufs);
        }
        return this;
    }

    /**
     * @param byteBufs The {@link ByteBuf}s
     * @return The {@link ByteBuffer}
     */
    public ByteBuffer write(ByteBuf... byteBufs) {
        if (this.delegate instanceof CompositeByteBuf) {
            CompositeByteBuf compositeByteBuf = (CompositeByteBuf) this.delegate;
            compositeByteBuf.addComponents(true, byteBufs);
        } else {
            ByteBuf current = this.delegate;
            CompositeByteBuf composite = current.alloc().compositeBuffer(byteBufs.length + 1);
            this.delegate = composite;
            composite.addComponent(true, current);
            composite.addComponents(true, byteBufs);
        }
        return this;
    }

    @Override
    public ByteBuffer slice(int index, int length) {
        return new NettyByteBuffer(delegate.slice(index, length));
    }

    @Override
    public java.nio.ByteBuffer asNioBuffer() {
        return delegate.nioBuffer();
    }

    @Override
    public java.nio.ByteBuffer asNioBuffer(int index, int length) {
        return delegate.nioBuffer(index, length);
    }

    @Override
    public InputStream toInputStream() {
        return new ByteBufInputStream(delegate);
    }

    @Override
    public OutputStream toOutputStream() {
        return new ByteBufOutputStream(delegate);
    }

    @Override
    public byte[] toByteArray() {
        return ByteBufUtil.getBytes(delegate);
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
