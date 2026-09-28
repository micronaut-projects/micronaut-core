package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("b")
@Requires(property = "spec.name", value = "BeanWatchTest")
public class BRule implements Rule {
    @Override
    public String name() {
        return "b";
    }
}
