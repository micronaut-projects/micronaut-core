package io.micronaut.dev;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Makes a module's pools from its configuration, retained until a change under {@code my.settings}.
 */
@Factory
@Requires(property = "spec.name", value = "RetainedConfigurationTest")
public class SettingsPools {

    /**
     * A pool that copied its size from the configuration bean, under the prefix its retention names. It also reads the
     * size directly, so that an edit of it restarts, as a module's configuration watch would ask.
     */
    @Singleton
    @Retain(invalidatedBy = "my.settings")
    Covered covered(PoolSettings settings, @Value("${my.settings.size:0}") int size) {
        return new Covered(settings.getSize());
    }

    /**
     * A pool that received configuration under another prefix.
     */
    @Singleton
    @Retain(invalidatedBy = "my.settings")
    Uncovered uncovered(OtherPoolSettings settings) {
        return new Uncovered(settings.getSize());
    }

    /**
     * A pool of configuration its retention covers.
     */
    public static final class Covered {
        public static final AtomicInteger CREATED = new AtomicInteger();
        public final int size;

        Covered(int size) {
            this.size = size;
            CREATED.incrementAndGet();
        }
    }

    /**
     * A pool of configuration its retention does not cover.
     */
    public static final class Uncovered {
        public static final AtomicInteger CREATED = new AtomicInteger();
        public final int size;

        Uncovered(int size) {
            this.size = size;
            CREATED.incrementAndGet();
        }
    }
}
