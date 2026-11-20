package io.micronaut.inject.any.qualifier;

import io.micronaut.context.Qualifier;
import org.jspecify.annotations.Nullable;

public class MyAnyPrototype {

    private final @Nullable Qualifier<?> qualifier;

    public MyAnyPrototype(@Nullable Qualifier<?> qualifier) {
        this.qualifier = qualifier;
    }

    public @Nullable Qualifier<?> getQualifier() {
        return qualifier;
    }
}
