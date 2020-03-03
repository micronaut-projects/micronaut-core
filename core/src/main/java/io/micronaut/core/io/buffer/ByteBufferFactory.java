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

/**
 * An allocator for {@link ByteBuffer} instances.
 *
 * @param <T> The type
 * @param <B> The body
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ByteBufferFactory<T, B> {

    /**
     * @return The native allocator
     */
    T getNativeAllocator();

    /**
     * Allocate a {@link ByteBuffer}. If it is a direct or heap buffer depends on the actual implementation.
     *
     * @return The buffer
     */
    ByteBuffer<B> buffer();

    /**
     * Allocate a {@link ByteBuffer} with the given initial capacity. If it is a direct or heap buffer depends on the
     * actual implementation.
     *
     * @param initialCapacity The initial capacity
     * @return the buffer
     */
    ByteBuffer<B> buffer(int initialCapacity);

    /**
     * Allocate a {@link ByteBuffer} with the given initial capacity and the given maximal capacity. If it is a direct
     * or heap buffer depends on the actual implementation.
     *
     * @param initialCapacity The initial capacity
     * @param maxCapacity     The maximum capacity
     * @return The buffer
     */
    ByteBuffer<B> buffer(int initialCapacity, int maxCapacity);

    /**
     * Creates a new big-endian buffer whose content is a copy of the specified {@code array}'s sub-region. The new
     * buffer's {@code readerIndex} and {@code writerIndex} are {@code 0} and the specified {@code length} respectively.
     *
     * @param bytes The bytes
     * @return The buffer
     */
    ByteBuffer<B> copiedBuffer(byte[] bytes);

    /**
     * Creates a new big-endian buffer whose content is a copy of the specified NIO buffer. The new buffer's
     * {@code readerIndex} and {@code writerIndex} are {@code 0} and the specified {@code length} respectively.
     *
     * @param nioBuffer The nioBuffer
     * @return The buffer
     */
    ByteBuffer<B> copiedBuffer(java.nio.ByteBuffer nioBuffer);

    /**
     * Wrap an existing buffer.
     *
     * @param existing The buffer to wrap
     * @return The wrapped {@link ByteBuffer}
     */
    ByteBuffer<B> wrap(B existing);

    /**
     * Wrap an existing buffer.
     *
     * @param existing The bytes to wrap
     * @return The wrapped {@link ByteBuffer}
     */
    ByteBuffer<B> wrap(byte[] existing);
}
