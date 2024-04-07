package io.micronaut.inject.beans;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "StaticInitSpec")
@Singleton
public class DoNotInitializeBean {

    static {
        if (true) {
            throw new IllegalStateException("The bean shouldn't be initialized!");
        }
    }

}
