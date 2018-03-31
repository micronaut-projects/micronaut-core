package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.MapPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import javax.sound.sampled.Line;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;


/**
 * <p>An {@link InfoSource} that retrieves values under the <i>info</i> key from configuration sources. </p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
//TODO: @Refreshable
@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.config.enabled", notEquals = "false")
public class ConfigurationInfoSource implements InfoSource {

    private final Environment environment;
    private final Supplier<MapPropertySource> supplier;

    public ConfigurationInfoSource(Environment environment) {
        this.environment = environment;
        this.supplier = SupplierUtil.memoized(this::retrieveConfigurationInfo);
    }

    @Override
    public Publisher<PropertySource> getSource() {
        return Flowable.just(supplier.get());
    }


    private MapPropertySource retrieveConfigurationInfo() {
        return new MapPropertySource("info", environment.getProperty("info", Map.class).orElse(new HashMap()));
    }
}
