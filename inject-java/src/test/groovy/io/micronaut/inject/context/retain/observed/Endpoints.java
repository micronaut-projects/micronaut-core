package io.micronaut.inject.context.retain.observed;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;

/**
 * Makes an endpoint per server entry, retained, and exposed only as {@link Endpoint}: its definition is not found by
 * its own class.
 */
@Factory
@Requires(property = "spec.name", value = "ObservedConfigurationSpec")
public class Endpoints {

    @EachBean(ServerSettings.class)
    @Bean(typed = Endpoint.class)
    @Retain
    ServerEndpoint endpoint(ServerSettings settings) {
        return new ServerEndpoint(settings.name);
    }

    /**
     * An endpoint of a server.
     *
     * @param server The server's name
     */
    public record ServerEndpoint(String server) implements Endpoint {
    }
}
