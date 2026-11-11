package io.micronaut.inject.context.retain.replaced;

/**
 * What a client authenticates with, which the application implements.
 */
public interface Credentials {
    String token();
}
