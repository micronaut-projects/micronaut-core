package io.micronaut.management.endpoint.info;

import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.management.endpoint.Read;
import io.micronaut.management.info.aggregator.InfoAggregator;
import io.micronaut.management.info.source.InfoSource;
import org.reactivestreams.Publisher;

/**
 * <p>Exposes an {@link Endpoint} to provide information about the application.</p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
@Endpoint("info")
public class InfoEndpoint {


    public static final String NAME = "info";
    public static final String PREFIX = EndpointConfiguration.PREFIX + "." + NAME;

    private InfoAggregator infoAggregator;
    private InfoSource[] infoSources;

    public InfoEndpoint(InfoAggregator infoAggregator, InfoSource[] infoSources) {
        this.infoAggregator = infoAggregator;
        this.infoSources = infoSources;
    }

    @Read
    Publisher getInfo() {
        return infoAggregator.aggregate(infoSources);
    }
}
