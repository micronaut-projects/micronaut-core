/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.management.health.indicator.client;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.StaticServiceInstanceList;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>A {@link io.micronaut.management.health.indicator.HealthIndicator} used to display available load balancer URLs.
 * Returns {@link HealthStatus#DOWN} if there are no available URLs in the load balancer.</p>
 *
 * @author Alexander Simpson
 * @since 3.9
 */
@EachBean(ServiceHttpClientConfiguration.class)
@Requires(beans = HealthEndpoint.class)
@Requires(classes = ServiceHttpClientConfiguration.class)
@Requires(property = HealthEndpoint.PREFIX + ".service-http-client.enabled", defaultValue = StringUtils.FALSE, notEquals = StringUtils.FALSE)
public class ServiceHttpClientHealthIndicator implements HealthIndicator {

    private final ServiceHttpClientConfiguration configuration;
    private final Collection<URI> loadBalancerUrls;
    private final Collection<URI> originalUrls;
    private final HealthResult.Builder serviceHealthBuilder;

    /**
     * @param configuration Configuration for the individual service http client.
     * @param instanceList Instance List for the individual service http client. Used to obtain available load balancer URLs.
     */
    public ServiceHttpClientHealthIndicator(@Parameter ServiceHttpClientConfiguration configuration, @Parameter StaticServiceInstanceList instanceList) {
        this.configuration = configuration;
        this.loadBalancerUrls = instanceList.getLoadBalancedURIs();
        this.originalUrls = configuration.getUrls();
        this.serviceHealthBuilder = HealthResult.builder(configuration.getServiceId());
    }

    @Override
    public Publisher<HealthResult> getResult() {
        if (!configuration.isHealthCheck()) {
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
