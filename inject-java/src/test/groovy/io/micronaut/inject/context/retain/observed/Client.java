package io.micronaut.inject.context.retain.observed;

/**
 * A client made from configuration, which copied the values it needs and holds nothing of the context.
 */
public final class Client {
    public final String name;
    public final int value;

    Client(String name, int value) {
        this.name = name;
        this.value = value;
    }
}
