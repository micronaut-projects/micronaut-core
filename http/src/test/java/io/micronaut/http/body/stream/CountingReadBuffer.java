/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A buffer that counts how often it is closed, standing in for a pooled buffer whose release
 * the tests check.
 */
final class CountingReadBuffer extends ReadBuffer {
    final AtomicInteger closes = new AtomicInteger();
    private final ReadBuffer delegate;

    CountingReadBuffer(String content) {
        this.delegate = ReadBufferFactory.getJdkFactory().copyOf(content, StandardCharsets.UTF_8);
    }

    @Override
    public int readable() {
        return delegate.readable();
    }

    @Override
    public ReadBuffer duplicate() {
        return delegate.duplicate();
    }

    @Override
    public ReadBuffer split(int splitPosition) {
        return delegate.split(splitPosition);
    }

    @Override
    public ReadBuffer move() {
        return delegate.move();
    }

    @Override
    public void toArray(byte[] destination, int offset) {
        delegate.toArray(destination, offset);
    }

    @Override
    public void close() {
        closes.incrementAndGet();
        delegate.close();
    }

    @Override
    protected boolean isConsumed() {
        return false;
    }

    @Override
    protected byte[] peekArray(int n) {
        return delegate.duplicate().toArray();
    }
}
