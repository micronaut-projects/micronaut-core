package io.micronaut.inject.context.retain.factory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The pool itself: holds nothing of the context that made it.
 */
public final class SimplePool implements DataPool {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public static final AtomicInteger CLOSED = new AtomicInteger();
    private final String name;
    private volatile boolean closed;

    SimplePool(String name) {
        this.name = name;
        CREATED.incrementAndGet();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            CLOSED.incrementAndGet();
        }
        closed = true;
    }
}
