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

import io.micronaut.core.annotation.NonNull;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ReferenceCountedWrapper<T> implements Closeable {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Slot<T> slot;

    private ReferenceCountedWrapper(Slot<T> slot) {
        this.slot = slot;
    }

    @NonNull
    public static <T> List<ReferenceCountedWrapper<T>> wrap(T object, @NonNull Consumer<T> close, int referenceCount) {
        if (referenceCount <= 0) {
            throw new IllegalArgumentException("Reference count must be positive");
        }

        Slot<T> s = new Slot<>(object, referenceCount, close);
        List<ReferenceCountedWrapper<T>> list = new ArrayList<>(referenceCount);
        for (int i = 0; i < referenceCount; i++) {
            list.add(new ReferenceCountedWrapper<>(s));
        }
        return list;
    }

    public T get() {
        if (closed.getPlain()) {
            throw new IllegalStateException("Already closed");
        }
        return slot.object;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (slot.referenceCount.decrementAndGet() == 0) {
                slot.close.accept(slot.object);
                slot.object = null;
            }
        }
    }

    private static final class Slot<T> {
        private final AtomicInteger referenceCount;
        private T object;
        private final Consumer<T> close;

        Slot(T object, int count, Consumer<T> close) {
            this.object = object;
            this.referenceCount = new AtomicInteger(count);
            this.close = close;
        }
    }
}
