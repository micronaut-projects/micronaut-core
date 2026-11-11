package io.micronaut.inject.context.retain.observed;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;

/**
 * Makes a connection per server entry, retained without naming a prefix.
 */
@Factory
@Requires(property = "spec.name", value = "ObservedConfigurationSpec")
public class Connections {

    @EachBean(ServerSettings.class)
    @Retain
    Connection connection(ServerSettings settings) {
        return new Connection(settings.name, settings.getPort());
    }

    /**
     * A connection to a server.
     *
     * @param server The server's name
     * @param port The port it copied
     */
    public record Connection(String server, int port) {
    }
}
