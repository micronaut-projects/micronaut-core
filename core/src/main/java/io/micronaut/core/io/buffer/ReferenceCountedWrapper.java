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
