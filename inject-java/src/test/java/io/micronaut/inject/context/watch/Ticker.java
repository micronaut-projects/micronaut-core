package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
public class Ticker {

    @Tick("fast")
    public void tick() {
    }

    @Tick("slow")
    public void tock(int times) {
    }

    public void plain() {
    }
}
