package io.micronaut.inject.context.retain.nested;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

/**
 * Makes a client per server, from the server's configuration and its streams.
 */
@Factory
@Requires(property = "spec.name", value = "NestedEachPropertyRetentionSpec")
public class ServerClientFactory {

    @EachBean(ServerConfig.class)
    @Bean(preDestroy = "close")
    public ServerClient client(ServerConfig config) {
        return new ServerClient(config.getStreams().stream().map(stream -> stream.name).toList());
    }
}
