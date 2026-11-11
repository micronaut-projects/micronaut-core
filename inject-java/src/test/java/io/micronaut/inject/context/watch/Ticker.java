package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
public class Ticker {

    @Tick("fast")
    public void tick() {
        // only the @Tick annotation matters to the spec
    }

    @Tick("slow")
    public void tock(int times) {
        // only the @Tick annotation matters to the spec
    }

    public void plain() {
        // a method without @Tick, which the watch must not see
    }
}
