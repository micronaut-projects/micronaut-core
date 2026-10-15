package io.micronaut.inject.provider.injectionpoint;

import io.micronaut.core.type.Argument;

/**
 * What a bean was injected into: the name of the field or argument, and the type argument it asked for.
 *
 * @param <T> The type argument of the injection point
 */
public final class InjectedAt<T> {

    private final String name;
    private final Argument<?> type;

    InjectedAt(String name, Argument<?> type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return name;
    }

    public Argument<?> type() {
        return type;
    }
}
