package io.micronaut.dev;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the housekeeper as a connection pool module makes its pool, declared retained across restarts, and closes it
 * once the last generation stops.
 */
@Factory
@Requires(property = "spec.name", value = "ThreadGenerationMemoryTest")
public class HousekeeperFactory {
    private final List<Housekeeper> made = new ArrayList<>();

    @Context
    @Retain(invalidatedBy = "housekeeper")
    public Housekeeper housekeeper() {
        Housekeeper housekeeper = new Housekeeper();
        made.add(housekeeper);
        return housekeeper;
    }

    @PreDestroy
    void close() throws InterruptedException {
        for (Housekeeper housekeeper : made) {
            housekeeper.close();
        }
    }
}
