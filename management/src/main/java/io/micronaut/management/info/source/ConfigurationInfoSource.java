package io.micronaut.management.info.source;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.MapPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

//TODO: @Refreshable
@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.config.enabled", notEquals = "false")
public class ConfigurationInfoSource implements InfoSource {

    private Environment environment;
    private CompletableFuture<MapPropertySource> configurationInfoFuture;

    public ConfigurationInfoSource(Environment environment) {
        this.environment = environment;
        configurationInfoFuture = CompletableFuture.supplyAsync(this::retrieveConfigurationInfo);
    }

    @Override
    public Publisher<PropertySource> getSource() {

        try {
            return Flowable.just(configurationInfoFuture.get());
        } catch (InterruptedException | ExecutionException ex) {}

        return Flowable.empty();
    }


    private MapPropertySource retrieveConfigurationInfo() {

        return new MapPropertySource("info", environment.getProperty("info", Map.class).orElse(new HashMap()));
    }
}
