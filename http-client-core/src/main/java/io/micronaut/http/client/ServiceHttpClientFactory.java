/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.client;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.StaticServiceInstanceList;
import io.micronaut.context.event.StartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Creates {@link HttpClient} instances for each defined {@link ServiceHttpClientConfiguration}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Internal
public class ServiceHttpClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceHttpClientFactory.class);

    private final BeanProvider<LoadBalancerResolver> loadBalancerResolver;

    /**
     * Default constructor.
     *
     * @param loadBalancerResolver The load balancer resolver, which starts the health checks
     */
    public ServiceHttpClientFactory(BeanProvider<LoadBalancerResolver> loadBalancerResolver) {
        this.loadBalancerResolver = loadBalancerResolver;
    }

    /**
     * Create a {@link io.micronaut.discovery.ServiceInstanceList} for each configured client.
     *
     * @param configuration The configuration
     * @return The instance list
     */
    @EachBean(ServiceHttpClientConfiguration.class)
    @Requires(condition = ServiceHttpClientCondition.class)
    StaticServiceInstanceList serviceInstanceList(ServiceHttpClientConfiguration configuration) {
        List<URI> originalURLs = configuration.getUrls();
        Collection<URI> loadBalancedURIs = new ConcurrentLinkedQueue<>(originalURLs);
        return new StaticServiceInstanceList(configuration.getServiceId(), loadBalancedURIs, configuration.getPath().orElse(null));
    }

    /**
     * Creates a {@link ApplicationEventListener} that listens to {@link StartupEvent} for each configured HTTP client
     * in order to start its health check if necessary, whether or not the application runs a server.
     * The health check belongs to the load balancer of the service, see {@link LoadBalancerResolver}: it probes
     * the configured URLs, or the instances a discovery client finds for a service without URLs.
     *
     * @param configuration The configuration
     * @return The event listener
     */
    @EachBean(ServiceHttpClientConfiguration.class)
    ApplicationEventListener<StartupEvent> healthCheckStarter(@Parameter ServiceHttpClientConfiguration configuration) {
        if (configuration.isHealthCheck()) {
            return event -> {
                LoadBalancerResolver resolver = loadBalancerResolver.get();
                if (!(resolver instanceof DefaultLoadBalancerResolver)) {
                    LOG.warn("The health check of service '{}' is not started: the load balancer resolver {} is not a DefaultLoadBalancerResolver, which starts it.",
                        configuration.getServiceId(), resolver.getClass().getName());
                }
                resolver.resolve(configuration.getServiceId());
            };
        }
        throw new DisabledBeanException("HTTP Client Health Check not enabled");
    }
}
