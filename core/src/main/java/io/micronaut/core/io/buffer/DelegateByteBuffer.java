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

import io.micronaut.core.annotation.Internal;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Delegate class for {@link ByteBuffer}.
 *
 * @param <T> The native buffer type
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public class DelegateByteBuffer<T> implements ByteBuffer<T> {
    private final ByteBuffer<T> delegate;

    public DelegateByteBuffer(ByteBuffer<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T asNativeBuffer() {
        return delegate.asNativeBuffer();
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
        return delegate.read();
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        return delegate.readCharSequence(length, charset);
    }

    @Override
    public ByteBuffer read(byte[] destination) {
        delegate.read(destination);
        return this;
    }

    @Override
    public ByteBuffer read(byte[] destination, int offset, int length) {
        delegate.read(destination, offset, length);
        return this;
    }

    @Override
    public ByteBuffer write(byte b) {
        delegate.write(b);
        return this;
    }

    @Override
    public ByteBuffer write(byte[] source) {
        delegate.write(source);
        return this;
    }

    @Override
    public ByteBuffer write(CharSequence source, Charset charset) {
        delegate.write(source, charset);
        return this;
    }

    @Override
    public ByteBuffer write(byte[] source, int offset, int length) {
        delegate.write(source, offset, length);
        return this;
    }

    @Override
    public ByteBuffer write(ByteBuffer... buffers) {
        delegate.write(buffers);
        return this;
    }

    @Override
    public ByteBuffer write(java.nio.ByteBuffer... buffers) {
        delegate.write(buffers);
        return this;
    }

    @Override
    public ByteBuffer slice(int index, int length) {
        delegate.slice(index, length);
        return this;
    }

    @Override
    public java.nio.ByteBuffer asNioBuffer() {
        return delegate.asNioBuffer();
    }

    @Override
    public java.nio.ByteBuffer asNioBuffer(int index, int length) {
        return delegate.asNioBuffer(index, length);
    }

    @Override
    public InputStream toInputStream() {
        return delegate.toInputStream();
    }

    @Override
    public OutputStream toOutputStream() {
        return delegate.toOutputStream();
    }

    @Override
    public byte[] toByteArray() {
        return delegate.toByteArray();
    }

    @Override
    public String toString(Charset charset) {
        return delegate.toString(charset);
    }

    @Override
    public int indexOf(byte b) {
        return delegate.indexOf(b);
    }

    @Override
    public byte getByte(int index) {
        return delegate.getByte(index);
    }
}
