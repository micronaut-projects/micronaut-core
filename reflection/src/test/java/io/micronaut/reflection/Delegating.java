package io.micronaut.reflection;

/**
 * A record declaring a constructor wider than its canonical one, so that the two can be told apart.
 */
public record Delegating(String label) {

    public Delegating(String label, int ignored) {
        this(label + ignored);
    }
}
