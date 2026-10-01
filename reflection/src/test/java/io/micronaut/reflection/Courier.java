package io.micronaut.reflection;

/**
 * A type of which several beans are registered, each named.
 */
public class Courier {

    private final String code;

    public Courier(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
