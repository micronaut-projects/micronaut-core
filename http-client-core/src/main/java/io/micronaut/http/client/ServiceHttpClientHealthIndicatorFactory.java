package io.micronaut.http.client;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.StaticServiceInstanceList;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthResult;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Factory
@EachBean(ServiceHttpClientConfiguration.class)
@Requires(beans = HealthEndpoint.class)
public class ServiceHttpClientHealthIndicatorFactory implements HealthIndicator {

    private final ServiceHttpClientConfiguration configuration;
    private final Collection<URI> loadBalancerUrls;
    private final Collection<URI> originalUrls;
    private final HealthResult.Builder serviceHealthBuilder;

    public ServiceHttpClientHealthIndicatorFactory(@Parameter ServiceHttpClientConfiguration configuration, @Parameter StaticServiceInstanceList instanceList) {
        this.configuration = configuration;
        this.loadBalancerUrls = instanceList.getLoadBalancedURIs();
        this.originalUrls = configuration.getUrls();
        this.serviceHealthBuilder = HealthResult.builder(configuration.getServiceId());
    }

    @Override
    public Publisher<HealthResult> getResult() {
        if (!configuration.isHealthIndicator() || !configuration.isHealthCheck()) {
            return Publishers.empty();
        }

        return Publishers.just(determineServiceHealth());
    }

    private HealthResult determineServiceHealth() {
        Map<String, Object> details = new LinkedHashMap<>(2);
        details.put("all_urls", originalUrls);
        details.put("available_urls", loadBalancerUrls);

        if (loadBalancerUrls.isEmpty()) {
            return serviceHealthBuilder.status(HealthStatus.DOWN).details(details).build();
        }

        return serviceHealthBuilder.status(HealthStatus.UP).details(details).build();
    }
}
