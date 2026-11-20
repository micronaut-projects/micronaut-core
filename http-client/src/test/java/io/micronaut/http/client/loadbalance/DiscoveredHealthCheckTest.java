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
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.AsyncDiscoveryClient;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * The active health check of a service whose instances come from a discovery client.
 */
class DiscoveredHealthCheckTest {

    private static final String SPEC_NAME = "DiscoveredHealthCheckTest";

    @Test
    void probesDiscoveredInstances() throws Exception {
        try (EmbeddedServer one = server();
             EmbeddedServer two = server();
             EmbeddedServer three = server();
             ApplicationContext ctx = ApplicationContext.run(Map.of(
                 "spec.name", SPEC_NAME,
                 "micronaut.http.services.upstream.health-check", true,
                 "micronaut.http.services.upstream.health-check-interval", "50ms",
                 "micronaut.http.services.upstream.health-check-uri", "/health"
             ))) {
            TestDiscoveryClient discovery = ctx.getBean(TestDiscoveryClient.class);
            discovery.instances.add(ServiceInstance.of("upstream", one.getURI()));
            discovery.instances.add(ServiceInstance.of("upstream", two.getURI()));

            LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("upstream").orElseThrow();
            ServiceHttpClientConfiguration configuration = ctx.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("upstream"));
            HttpClient client = (HttpClient) ctx.getBean(HttpClientRegistry.class)
                .getClient(HttpVersionSelection.forClientConfiguration(configuration), "upstream", null);

            Assertions.assertEquals(Set.of(one.getPort(), two.getPort()), ports(client, 10));

            // instance two turns unhealthy and is skipped
            health(two).set(false);
            await(() -> isUnhealthy(loadBalancer, two));
            Assertions.assertEquals(Set.of(one.getPort()), ports(client, 10));

            // it recovers and returns
            health(two).set(true);
            await(() -> !isUnhealthy(loadBalancer, two));
            Assertions.assertEquals(Set.of(one.getPort(), two.getPort()), ports(client, 10));

            // the instance list changes: three joins unhealthy and is never selected, one leaves
            health(three).set(false);
            discovery.instances.add(ServiceInstance.of("upstream", three.getURI()));
            await(() -> isUnhealthy(loadBalancer, three));
            discovery.instances.removeIf(i -> i.getURI().getPort() == one.getPort());
            Assertions.assertEquals(Set.of(two.getPort()), ports(client, 10));
            await(() -> loadBalancer.getOutlierEjectionStates().stream().noneMatch(s -> s.uri().getPort() == one.getPort()));

            // every instance unhealthy: all of them are used
            health(two).set(false);
            await(() -> isUnhealthy(loadBalancer, two));
            Assertions.assertEquals(Set.of(two.getPort(), three.getPort()), ports(client, 20));
        }
    }

    @Test
    void probesConfiguredUrlsWithUserInfo() throws Exception {
        try (EmbeddedServer server = server()) {
            String url = "http://user:secret@localhost:" + server.getPort();
            try (ApplicationContext ctx = ApplicationContext.run(Map.of(
                "micronaut.http.services.secured.url", url,
                "micronaut.http.services.secured.health-check", true,
                "micronaut.http.services.secured.health-check-interval", "50ms",
                "micronaut.http.services.secured.health-check-uri", "/secure-health"
            ))) {
                LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("secured").orElseThrow();
                HealthController controller = server.getApplicationContext().getBean(HealthController.class);
                await(() -> controller.secureProbes.get() > 1);
                Assertions.assertFalse(isUnhealthy(loadBalancer, server), loadBalancer.getOutlierEjectionStates().toString());

                // the state of the probe is keyed by the URI of the instance, without the user info
                controller.healthy.set(false);
                await(() -> isUnhealthy(loadBalancer, server));
            }
        }
    }

    @Test
    void usesNoInstanceWhenAllUnhealthyIfConfigured() throws Exception {
        try (EmbeddedServer one = server();
             ApplicationContext ctx = ApplicationContext.run(Map.of(
                 "spec.name", SPEC_NAME,
                 "micronaut.http.services.upstream.health-check", true,
                 "micronaut.http.services.upstream.health-check-interval", "50ms",
                 "micronaut.http.services.upstream.health-check-all-unhealthy", "none"
             ))) {
            ctx.getBean(TestDiscoveryClient.class).instances.add(ServiceInstance.of("upstream", one.getURI()));
            LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("upstream").orElseThrow();
            health(one).set(false);
            await(() -> isUnhealthy(loadBalancer, one));
            Assertions.assertThrows(NoAvailableServiceException.class, () -> Mono.from(loadBalancer.select()).block());
            health(one).set(true);
            await(() -> !isUnhealthy(loadBalancer, one));
            Assertions.assertEquals(one.getPort(), Mono.from(loadBalancer.select()).block().getURI().getPort());
        }
    }

    @Test
    void probesDiscoveredInstancesWithTheirCredentials() throws Exception {
        try (EmbeddedServer one = server();
             ApplicationContext ctx = ApplicationContext.run(Map.of(
                 "spec.name", SPEC_NAME,
                 "micronaut.http.services.upstream.health-check", true,
                 "micronaut.http.services.upstream.health-check-interval", "50ms",
                 "micronaut.http.services.upstream.health-check-uri", "/authenticated-health"
             ))) {
            URI withCredentials = URI.create("http://user:secret@localhost:" + one.getPort());
            ctx.getBean(TestDiscoveryClient.class).instances.add(ServiceInstance.builder("upstream", withCredentials).build());
            LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("upstream").orElseThrow();
            HealthController controller = server(one);
            await(() -> controller.authenticatedProbes.get() > 1);
            Assertions.assertFalse(isUnhealthy(loadBalancer, one), loadBalancer.getOutlierEjectionStates().toString());
        }
    }

    @Test
    void probesThroughAnAsyncOnlyDiscoveryClient() throws Exception {
        try (EmbeddedServer one = server();
             ApplicationContext ctx = ApplicationContext.run(Map.of(
                 "spec.name", SPEC_NAME + "Async",
                 "micronaut.http.services.upstream.health-check", true,
                 "micronaut.http.services.upstream.health-check-interval", "50ms"
             ))) {
            ctx.getBean(AsyncOnlyDiscoveryClient.class).instances.add(ServiceInstance.of("upstream", one.getURI()));
            LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("upstream").orElseThrow();
            health(one).set(false);
            await(() -> isUnhealthy(loadBalancer, one));
        }
    }

    private static HealthController server(EmbeddedServer server) {
        return server.getApplicationContext().getBean(HealthController.class);
    }

    private static EmbeddedServer server() {
        return ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME + "Server"));
    }

    private static AtomicBoolean health(EmbeddedServer server) {
        return server.getApplicationContext().getBean(HealthController.class).healthy;
    }

    private static boolean isUnhealthy(LoadBalancer loadBalancer, EmbeddedServer server) {
        return loadBalancer.getOutlierEjectionStates().stream()
            .anyMatch(s -> s.uri().getPort() == server.getPort() && s.unhealthy());
    }

    private static Set<Integer> ports(HttpClient client, int times) {
        Set<Integer> ports = new HashSet<>();
        for (int i = 0; i < times; i++) {
            ports.add(Integer.parseInt(client.toBlocking().retrieve(HttpRequest.GET("/port"), String.class)));
        }
        return ports;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                Assertions.fail("Condition not met in time");
            }
            Thread.sleep(20);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class TestDiscoveryClient implements DiscoveryClient {
        final List<ServiceInstance> instances = new CopyOnWriteArrayList<>();

        @Override
        public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            return Mono.fromCallable(() -> "upstream".equals(serviceId) ? List.copyOf(instances) : List.of());
        }

        @Override
        public Publisher<List<String>> getServiceIds() {
            return Mono.just(List.of("upstream"));
        }

        @Override
        public String getDescription() {
            return "test";
        }

        @Override
        public void close() {
        }
    }

    /**
     * Serves only the async lookup, as {@code DiscoveryClientAsyncTest.AsyncClient} does.
     */
    @Requires(property = "spec.name", value = SPEC_NAME + "Async")
    @Singleton
    static class AsyncOnlyDiscoveryClient implements DiscoveryClient, AsyncDiscoveryClient {
        final List<ServiceInstance> instances = new CopyOnWriteArrayList<>();

        @Override
        public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
            return CompletableFuture.completedFuture("upstream".equals(serviceId) ? List.copyOf(instances) : List.of());
        }

        @Override
        public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            throw new UnsupportedOperationException("async only");
        }

        @Override
        public Publisher<List<String>> getServiceIds() {
            return Mono.just(List.of("upstream"));
        }

        @Override
        public CompletionStage<List<String>> getServiceIdsAsync() {
            return CompletableFuture.completedFuture(List.of("upstream"));
        }

        @Override
        public String getDescription() {
            return "async-only";
        }

        @Override
        public void close() {
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME + "Server")
    @Controller
    static class HealthController {
        final AtomicBoolean healthy = new AtomicBoolean(true);
        final AtomicInteger secureProbes = new AtomicInteger();
        final AtomicInteger authenticatedProbes = new AtomicInteger();

        @Get("/authenticated-health")
        HttpResponse<String> authenticatedHealth(HttpRequest<?> request) {
            String authorization = request.getHeaders().getAuthorization().orElse(null);
            String expected = "Basic " + Base64.getEncoder().encodeToString("user:secret".getBytes(StandardCharsets.UTF_8));
            if (!expected.equals(authorization)) {
                return HttpResponse.<String>status(HttpStatus.UNAUTHORIZED).body("no credentials");
            }
            authenticatedProbes.incrementAndGet();
            return HttpResponse.ok("UP");
        }

        @Get("/secure-health")
        HttpResponse<String> secureHealth() {
            secureProbes.incrementAndGet();
            return health();
        }

        @Get("/health")
        HttpResponse<String> health() {
            return healthy.get() ? HttpResponse.ok("UP") : HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).body("DOWN");
        }

        @Get("/port")
        String port(HttpRequest<?> request) {
            return String.valueOf(request.getServerAddress().getPort());
        }
    }
}
