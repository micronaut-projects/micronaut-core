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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.loadbalance.HealthCheckProber;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.annotation.PreDestroy;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import io.micronaut.http.client.loadbalance.AbstractRoundRobinLoadBalancer;
import io.micronaut.http.client.loadbalance.DiscoveryClientLoadBalancerFactory;
import io.micronaut.http.client.loadbalance.LoadBalancerStrategy;
import io.micronaut.http.client.loadbalance.OutlierDetectionConfiguration;
import io.micronaut.http.client.loadbalance.OutlierDetector;
import io.micronaut.http.client.loadbalance.ServiceInstanceListLoadBalancerFactory;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <p>Abstraction over {@link LoadBalancer} lookup. The strategy is as follows:</p>
 *
 * <ul>
 * <li>If a reference starts with '/' then we attempt to look up the {@link EmbeddedServer}</li>
 * <li>If the reference contains a '/' assume it is a URL and try to create a URL reference to it</li>
 * <li>Otherwise delegate to the {@link io.micronaut.discovery.DiscoveryClient} to attempt to resolve the URIs</li>
 * </ul>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@BootstrapContextCompatible
public class DefaultLoadBalancerResolver implements LoadBalancerResolver {

    private final Map<String, ServiceInstanceList> serviceInstanceLists;
    private final Map<String, LoadBalancer> loadBalancers = new ConcurrentHashMap<>();
    private final Queue<HealthCheckProber> probers = new ConcurrentLinkedQueue<>();
    private final BeanContext beanContext;

    /**
     * The default server loadbalance resolver.
     *
     * @param beanContext          The bean context
     * @param serviceInstanceLists Any other providers
     */
    public DefaultLoadBalancerResolver(
        BeanContext beanContext,
        ServiceInstanceList... serviceInstanceLists) {
        this(beanContext, Arrays.asList(serviceInstanceLists));
    }

    /**
     * The default server loadbalance resolver.
     *
     * @param beanContext          The bean context
     * @param serviceInstanceLists Any other providers
     */
    @Inject public DefaultLoadBalancerResolver(
            BeanContext beanContext,
            List<ServiceInstanceList> serviceInstanceLists) {
        this.beanContext = beanContext;
        if (CollectionUtils.isNotEmpty(serviceInstanceLists)) {
            this.serviceInstanceLists = CollectionUtils.newHashMap(serviceInstanceLists.size());
            for (ServiceInstanceList provider : serviceInstanceLists) {
                this.serviceInstanceLists.put(provider.getID(), provider);
            }
        } else {
            this.serviceInstanceLists = Collections.emptyMap();
        }
    }

    @Override
    public Optional<? extends LoadBalancer> resolve(String... serviceReferences) {
        if (ArrayUtils.isEmpty(serviceReferences) || StringUtils.isEmpty(serviceReferences[0])) {
            return Optional.empty();
        }
        String reference = serviceReferences[0];

        if (reference.startsWith("/")) {
            // current server reference
            if (beanContext.containsBean(EmbeddedServer.class)) {
                EmbeddedServer embeddedServer = beanContext.getBean(EmbeddedServer.class);
                URI uri = embeddedServer.getURI();
                return Optional.of(LoadBalancer.fixed(uri));
            } else {
                return Optional.empty();
            }
        } else if (reference.indexOf('/') > -1) {
            try {
                URI uri = new URI(reference);
                return Optional.of(LoadBalancer.fixed(uri));
            } catch (URISyntaxException e) {
                return Optional.empty();
            }
        } else {
            reference = NameUtils.hyphenate(reference);
            return resolveLoadBalancerForServiceID(reference);
        }
    }

    /**
     * @param serviceID The service ID
     * @return An {@link Optional} with the load balancer
     */
    protected Optional<? extends LoadBalancer> resolveLoadBalancerForServiceID(String serviceID) {
        // one balancer per service, shared by its clients: the round robin and the outlier
        // detection see every exchange of the service
        return Optional.ofNullable(loadBalancers.computeIfAbsent(serviceID, this::createLoadBalancerForServiceID));
    }

    @Nullable
    private LoadBalancer createLoadBalancerForServiceID(String serviceID) {
        // the factories may be replaced by ones that override only the single-argument create:
        // that is the one to call unless the service enables outlier detection, and even then
        // the two-argument one only adds the detection to what the single-argument one returns
        ServiceHttpClientConfiguration configuration = beanContext.findBean(ServiceHttpClientConfiguration.class, Qualifiers.byName(serviceID)).orElse(null);
        OutlierDetectionConfiguration outlierDetection = configuration == null || !configuration.getOutlierDetection().isEnabled()
            ? null : configuration.getOutlierDetection();
        LoadBalancer loadBalancer;
        if (serviceInstanceLists.containsKey(serviceID)) {
            ServiceInstanceList serviceInstanceList = serviceInstanceLists.get(serviceID);
            ServiceInstanceListLoadBalancerFactory factory = beanContext.getBean(ServiceInstanceListLoadBalancerFactory.class);
            loadBalancer = outlierDetection == null ? factory.create(serviceInstanceList) : factory.create(serviceInstanceList, outlierDetection);
        } else {
            DiscoveryClientLoadBalancerFactory factory = beanContext.getBean(DiscoveryClientLoadBalancerFactory.class);
            loadBalancer = outlierDetection == null ? factory.create(serviceID) : factory.create(serviceID, outlierDetection);
        }
        String strategyName = configuration == null ? null : configuration.getLoadBalancerStrategy();
        if (strategyName != null && !strategyName.isBlank()) {
            LoadBalancerStrategy strategy = beanContext.findBean(LoadBalancerStrategy.class, Qualifiers.byName(strategyName))
                .orElseGet(() -> builtInStrategy(serviceID, strategyName));
            loadBalancer = AbstractRoundRobinLoadBalancer.withStrategy(loadBalancer, strategy);
        }
        if (configuration != null && configuration.isHealthCheck() && loadBalancer instanceof AbstractRoundRobinLoadBalancer roundRobin) {
            startHealthCheck(serviceID, configuration, roundRobin);
        }
        return loadBalancer;
    }

    /**
     * Probe the instances of the service, static or discovered, and leave the unhealthy ones out
     * of the selection of its load balancer. The probes go through the client of the service, as
     * they always did, with its filters.
     */
    private void startHealthCheck(String serviceID, ServiceHttpClientConfiguration configuration, AbstractRoundRobinLoadBalancer loadBalancer) {
        Supplier<Publisher<List<ServiceInstance>>> instances;
        ServiceInstanceList serviceInstanceList = serviceInstanceLists.get(serviceID);
        if (serviceInstanceList != null) {
            instances = () -> Mono.fromCallable(serviceInstanceList::getInstances);
        } else {
            DiscoveryClient discoveryClient = beanContext.getBean(DiscoveryClientLoadBalancerFactory.class).getDiscoveryClient();
            instances = () -> discoveryClient.getInstances(serviceID);
        }
        String healthCheckUri = configuration.getHealthCheckUri();
        Function<ServiceInstance, Publisher<Boolean>> probe = instance -> {
            HttpClient httpClient = beanContext.getBean(HttpClientRegistry.class).getClient(
                HttpVersionSelection.forClientConfiguration(configuration),
                configuration.getServiceId(),
                configuration.getPath().orElse(null));
            return Mono.from(httpClient.exchange(HttpRequest.GET(probeUri(instance).resolve(healthCheckUri))))
                .map(response -> response.code() < 300)
                .onErrorResume(HttpClientResponseException.class, e -> Mono.just(e.code() < 300));
        };
        HealthCheckProber.Settings settings = HealthCheckProber.Settings.of(configuration.getHealthCheckInterval());
        TaskScheduler scheduler = beanContext.findBean(TaskScheduler.class).orElse(null);
        OutlierDetector detector = loadBalancer.enableHealthCheck();
        detector.setNoneWhenAllUnhealthy(configuration.getHealthCheckAllUnhealthy() == ServiceHttpClientConfiguration.HealthCheckAllUnhealthy.NONE);
        HealthCheckProber prober = new HealthCheckProber(serviceID, settings, instances, probe, detector, scheduler);
        probers.add(prober);
        prober.start();
    }

    /**
     * The URI to probe an instance at: its URI with the user info that a {@link ServiceInstance}
     * keeps in its metadata rather than its URI, so that a configured URL is probed at the same
     * URI as the health check always did. The client sends an absolute URI as it is: the user
     * info does not become an authorization header.
     */
    private static URI probeUri(ServiceInstance instance) {
        URI uri = instance.getURI();
        if (uri.getUserInfo() != null) {
            return uri;
        }
        String userInfo = instance.getMetadata().get(HttpHeaders.AUTHORIZATION_INFO, String.class).orElse(null);
        if (userInfo == null || userInfo.isEmpty()) {
            return uri;
        }
        try {
            return new URI(uri.getScheme(), userInfo, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            return uri;
        }
    }

    /**
     * Stop the health checks of the services.
     *
     * @since 5.3.0
     */
    @PreDestroy
    public void stopHealthChecks() {
        for (HealthCheckProber prober : probers) {
            prober.close();
        }
        probers.clear();
    }

    private static LoadBalancerStrategy builtInStrategy(String serviceID, String strategyName) {
        try {
            return LoadBalancerStrategy.of(strategyName);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Unknown load balancer strategy '" + strategyName + "' for service '" + serviceID
                + "': set micronaut.http.services." + serviceID + ".load-balancer-strategy to one of "
                + String.join(", ", LoadBalancerStrategy.ROUND_ROBIN, LoadBalancerStrategy.RANDOM, LoadBalancerStrategy.POWER_OF_TWO_CHOICES, LoadBalancerStrategy.WEIGHTED, LoadBalancerStrategy.STICKY)
                + ", or to the name of a LoadBalancerStrategy bean", e);
        }
    }
}
