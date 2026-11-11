package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("a")
@Requires(property = "spec.name", value = "BeanWatchTest")
public class ARule implements Rule {
    @Override
    public String name() {
        return "a";
    }
}
