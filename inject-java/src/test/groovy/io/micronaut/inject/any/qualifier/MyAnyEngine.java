package io.micronaut.inject.any.qualifier;

import org.jspecify.annotations.Nullable;

public class MyAnyEngine {

    private final @Nullable String name;

    public MyAnyEngine(@Nullable String name) {
        this.name = name;
    }

    public @Nullable String getName() {
        return name;
    }
}
