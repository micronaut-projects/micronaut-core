package io.micronaut.dev.tck.fixtures;

import jakarta.inject.Singleton;

/**
 * A parent-tier bean with nothing of the reloadable tier, the kind a module retains across a reload.
 */
@Singleton
public class RetainedClock {

    private final long created = System.nanoTime();

    public long created() {
        return created;
    }
}
