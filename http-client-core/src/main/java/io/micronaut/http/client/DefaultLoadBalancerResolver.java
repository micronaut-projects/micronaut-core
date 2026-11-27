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
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.discovery.StaticServiceInstanceList;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.loadbalance.HealthCheckProber;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.annotation.PreDestroy;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Collection;
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

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLoadBalancerResolver.class);

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
        if (configuration != null && configuration.isHealthCheck()) {
            if (loadBalancer instanceof AbstractRoundRobinLoadBalancer roundRobin && roundRobin.getInstancesForHealthCheck().isPresent()) {
                startHealthCheck(serviceID, configuration, roundRobin);
            } else {
                LOG.warn("The health check of service '{}' is not started: its load balancer {} cannot tell its instances. "
                    + "The health check needs a load balancer that extends AbstractRoundRobinLoadBalancer, as the default ones do.",
                    serviceID, loadBalancer.getClass().getName());
            }
        }
        return loadBalancer;
    }

    /**
     * Probe the instances of the service, static or discovered, and leave the unhealthy ones out
     * of the selection of its load balancer. The probes go through the client of the service, as
     * they always did, with its filters.
     */
    private void startHealthCheck(String serviceID, ServiceHttpClientConfiguration configuration, AbstractRoundRobinLoadBalancer loadBalancer) {
        // the instances come from the load balancer, so that a replaced factory is followed
        Supplier<Publisher<List<ServiceInstance>>> instances = () -> Mono.justOrEmpty(loadBalancer.getInstancesForHealthCheck())
            .flatMap(lookup -> Mono.fromCompletionStage(lookup.get()));
        ServiceInstanceList serviceInstanceList = serviceInstanceLists.get(serviceID);
        Collection<URI> configuredUrls = serviceInstanceList instanceof StaticServiceInstanceList staticList ? staticList.getLoadBalancedURIs() : null;
        String healthCheckUri = configuration.getHealthCheckUri();
        Function<ServiceInstance, Publisher<Boolean>> probe = instance -> probe(serviceID, configuration, configuredUrls, healthCheckUri, instance);
        HealthCheckProber.Settings settings = HealthCheckProber.Settings.of(configuration.getHealthCheckInterval());
        TaskScheduler scheduler = beanContext.findBean(TaskScheduler.class).orElse(null);
        OutlierDetector detector = loadBalancer.enableHealthCheck();
        detector.setNoneWhenAllUnhealthy(configuration.getHealthCheckAllUnhealthy() == ServiceHttpClientConfiguration.HealthCheckAllUnhealthy.NONE);
        HealthCheckProber prober = new HealthCheckProber(serviceID, settings, instances, probe, detector, scheduler);
        probers.add(prober);
        prober.start();
    }

    /**
     * Probe one instance through the client of the service, as the health check always did,
     * with its filters.
     *
     * @return Whether the instance answered with a status below 300
     */
    private Publisher<Boolean> probe(String serviceID, ServiceHttpClientConfiguration configuration,
                                     @Nullable Collection<URI> configuredUrls, String healthCheckUri, ServiceInstance instance) {
        HttpClient httpClient = beanContext.getBean(HttpClientRegistry.class).getClient(
            HttpVersionSelection.forClientConfiguration(configuration),
            configuration.getServiceId(),
            configuration.getPath().orElse(null));
        MutableHttpRequest<Object> request;
        if (configuredUrls != null) {
            // a configured URL is probed at the URL as configured, as the health check always
            // did, without its credentials becoming a header
            request = HttpRequest.GET(configuredUrl(serviceID, configuredUrls, instance).resolve(healthCheckUri));
        } else {
            // a discovered instance is probed with its credentials, as its requests are
            request = HttpRequest.GET(instance.getURI().resolve(healthCheckUri));
            instance.getMetadata().get(HttpHeaders.AUTHORIZATION_INFO, String.class)
                .filter(info -> !info.isEmpty())
                .ifPresent(info -> request.getHeaders().auth(info));
        }
        return Mono.from(httpClient.exchange(request))
            .map(response -> response.code() < 300)
            .onErrorResume(HttpClientResponseException.class, e -> Mono.just(e.code() < 300));
    }

    /**
     * The configured URL of an instance of a static list: the instance URI is rebuilt without
     * the user info, from decoded components, so it is only the key of the instance.
     */
    static URI configuredUrl(String serviceID, Collection<URI> configuredUrls, ServiceInstance instance) {
        URI key = instance.getURI();
        for (URI url : configuredUrls) {
            if (url.equals(key) || ServiceInstance.builder(serviceID, url).build().getURI().equals(key)) {
                return url;
            }
        }
        return key;
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
