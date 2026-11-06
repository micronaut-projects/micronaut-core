package io.micronaut.inject.context.retain.observed;

/**
 * The type an endpoint is exposed as, rather than its own class.
 */
public interface Endpoint {

    /**
     * @return The server's name
     */
    String server();
}
