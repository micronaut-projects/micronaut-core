package io.micronaut.inject.context.retain.config;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool that copied the values of its configuration, and holds nothing of the context.
 */
public class SizedPool {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public final int size;
    public final int max;

    protected SizedPool(int size, int max) {
        this.size = size;
        this.max = max;
        CREATED.incrementAndGet();
    }

    /**
     * A pool of configuration under the prefix its retention names.
     */
    public static final class Covered extends SizedPool {
        Covered(int size, int max) {
            super(size, max);
        }
    }

    /**
     * A pool of configuration under another prefix.
     */
    public static final class Uncovered extends SizedPool {
        Uncovered(int size) {
            super(size, 0);
        }
    }
}
