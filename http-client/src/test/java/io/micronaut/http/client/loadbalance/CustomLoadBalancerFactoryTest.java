/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.loadbalance;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * A replaced load balancer factory that overrides only the single-argument {@code create}, as
 * one written before outlier detection existed, still controls the selection: with outlier
 * detection off and on.
 */
class CustomLoadBalancerFactoryTest {
    private static final String SPEC_NAME = "CustomLoadBalancerFactoryTest";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replacedServiceInstanceListFactoryControlsTheSelection(boolean outlierDetection) {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.http.services.configured.urls", List.of("http://127.0.0.1:1", "http://127.0.0.1:2"),
            "micronaut.http.services.configured.outlier-detection.enabled", outlierDetection,
            "micronaut.http.services.configured.outlier-detection.consecutive-failures", 1
        ))) {
            LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("configured").orElseThrow();
            Assertions.assertInstanceOf(MarkerLoadBalancer.class, loadBalancer);
            Assertions.assertEquals("configured", ((MarkerLoadBalancer) loadBalancer).serviceId);
            Assertions.assertEquals(MarkerLoadBalancer.MARKER, Mono.from(loadBalancer.select(HttpRequest.GET("/"))).block().getURI());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replacedDiscoveryClientFactoryControlsTheSelection(boolean outlierDetection) {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.http.services.discovered.outlier-detection.enabled", outlierDetection,
            "micronaut.http.services.discovered.outlier-detection.consecutive-failures", 1
        ))) {
            LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("discovered").orElseThrow();
            Assertions.assertInstanceOf(MarkerLoadBalancer.class, loadBalancer);
            Assertions.assertEquals("discovered", ((MarkerLoadBalancer) loadBalancer).serviceId);
            Assertions.assertEquals(MarkerLoadBalancer.MARKER, Mono.from(loadBalancer.select(HttpRequest.GET("/"))).block().getURI());
        }
    }

    static final class MarkerLoadBalancer implements LoadBalancer {
        static final URI MARKER = URI.create("http://marker:1234");
        final String serviceId;

        MarkerLoadBalancer(String serviceId) {
            this.serviceId = serviceId;
        }

        @Override
        public Publisher<ServiceInstance> select(@Nullable Object discriminator) {
            return Publishers.just(ServiceInstance.of(serviceId, MARKER));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Replaces(ServiceInstanceListLoadBalancerFactory.class)
    static class CustomServiceInstanceListLoadBalancerFactory extends ServiceInstanceListLoadBalancerFactory {
        @Override
        public LoadBalancer create(ServiceInstanceList serviceInstanceList) {
            return new MarkerLoadBalancer(serviceInstanceList.getID());
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Replaces(DiscoveryClientLoadBalancerFactory.class)
    static class CustomDiscoveryClientLoadBalancerFactory extends DiscoveryClientLoadBalancerFactory {
        CustomDiscoveryClientLoadBalancerFactory(DiscoveryClient discoveryClient) {
            super(discoveryClient);
        }

        @Override
        public LoadBalancer create(String serviceID) {
            return new MarkerLoadBalancer(serviceID);
        }
    }
}
