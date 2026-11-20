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
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.StaticServiceInstanceList;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.loadbalance.OutlierEjectionState;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final @Nullable LoadBalancerResolver loadBalancerResolver;

    /**
     * @param configuration Configuration for the individual service http client.
     * @param instanceList Instance List for the individual service http client. Used to obtain available load balancer URLs.
     */
    public ServiceHttpClientHealthIndicator(@Parameter ServiceHttpClientConfiguration configuration, @Parameter StaticServiceInstanceList instanceList) {
        this(configuration, instanceList, null);
    }

    /**
     * @param configuration        Configuration for the individual service http client.
     * @param instanceList         Instance List for the individual service http client. Used to obtain available load balancer URLs.
     * @param loadBalancerResolver Resolves the load balancer of the service, whose health check tells the unhealthy URLs
     * @since 5.3.0
     */
    @Inject
    public ServiceHttpClientHealthIndicator(@Parameter ServiceHttpClientConfiguration configuration,
                                            @Parameter StaticServiceInstanceList instanceList,
                                            @Nullable LoadBalancerResolver loadBalancerResolver) {
        this.loadBalancerResolver = loadBalancerResolver;
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
        List<URI> available = new ArrayList<>(loadBalancerUrls);
        if (loadBalancerResolver != null) {
            // the health check of the load balancer leaves the unhealthy instances out; its
            // states are keyed by the URI of the instance, which has no credentials
            loadBalancerResolver.resolve(configuration.getServiceId()).ifPresent(loadBalancer -> {
                Set<URI> unhealthy = new HashSet<>();
                for (OutlierEjectionState state : loadBalancer.getOutlierEjectionStates()) {
                    if (state.unhealthy()) {
                        unhealthy.add(state.uri());
                    }
                }
                if (!unhealthy.isEmpty()) {
                    String serviceId = configuration.getServiceId();
                    available.removeIf(url -> unhealthy.contains(ServiceInstance.builder(serviceId, url).build().getURI()));
                }
            });
        }
        details.put("available_urls", available);

        if (available.isEmpty()) {
            return serviceHealthBuilder.status(HealthStatus.DOWN).details(details).build();
        }

        return serviceHealthBuilder.status(HealthStatus.UP).details(details).build();
    }
}
