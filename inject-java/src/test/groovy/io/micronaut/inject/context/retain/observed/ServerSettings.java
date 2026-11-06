package io.micronaut.inject.context.retain.observed;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;

/**
 * The configuration of each server: an entry per server.
 */
@EachProperty("observed.servers")
@Requires(property = "spec.name", value = "ObservedConfigurationSpec")
public class ServerSettings {
    public final String name;
    private int port;

    public ServerSettings(@Parameter String name) {
        this.name = name;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
