package io.micronaut.dev;

/**
 * What a module's client authenticates with, which the application implements.
 */
public interface ClientCredentials {
    String token();
}
