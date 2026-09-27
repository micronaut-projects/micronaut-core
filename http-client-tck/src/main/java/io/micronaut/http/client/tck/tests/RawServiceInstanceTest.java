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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientRegistry;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.client.loadbalance.ServiceInstanceListLoadBalancerFactory;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * A raw exchange sent to the service instance the caller selected with
 * {@link RawRequestOptions.Builder#serviceInstance(ServiceInstance)}, e.g. by a gateway that
 * selects the instance of each attempt itself: the exchange goes to that instance, and its outcome
 * is reported for it to the load balancer of the client, whose outlier detection ejects it.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RawServiceInstanceTest {
    static final String SPEC_NAME = "RawServiceInstanceTest";

    @Test
    void theExchangeGoesToTheSelectedInstanceAndItsFailuresEjectIt() throws Exception {
        try (ServerUnderTest server = server()) {
            URI dead = closedPort();
            URI alive = server.getURL().get().toURI();
            try (ApplicationContext clients = ApplicationContext.run(Map.of(
                "spec.name", SPEC_NAME + "-client",
                "micronaut.http.services.pool.urls", List.of(dead.toString(), alive.toString()),
                "micronaut.http.services.pool.outlier-detection.enabled", true,
                "micronaut.http.services.pool.outlier-detection.consecutive-failures", 2,
                "micronaut.http.services.pool.outlier-detection.base-ejection-time", "1m"
            )); RawHttpClient client = rawClient(clients, "pool")) {
                // the load balancer of the service, which the client of the service reports to
                LoadBalancer loadBalancer = clients.getBean(LoadBalancerResolver.class).resolve("pool").orElseThrow();
                ServiceInstance deadInstance = instance(loadBalancer, dead);
                ServiceInstance aliveInstance = instance(loadBalancer, alive);

                for (int i = 0; i < 2; i++) {
                    UnprocessedRequestException failure = Assertions.assertThrows(UnprocessedRequestException.class,
                        () -> exchange(client, HttpRequest.GET("/raw-service-instance/ok"), deadInstance));
                    Assertions.assertEquals(UnprocessedRequestException.Reason.CONNECT, failure.getReason());
                    Assertions.assertEquals(dead.getPort(), failure.getServiceInstance().orElseThrow().getPort(), "the failure names the selected instance");
                }
                // the selected instance is sent the request, whatever the load balancer would select
                for (int i = 0; i < 3; i++) {
                    try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET("/raw-service-instance/ok"), aliveInstance)) {
                        Assertions.assertEquals(200, response.code());
                        Assertions.assertEquals("ok", new String(response.byteBody().buffer().get(30, TimeUnit.SECONDS).toByteArray(), StandardCharsets.UTF_8));
                    }
                }
                // the failures of the selected instance ejected it: the load balancer selects the other one only
                for (int i = 0; i < 6; i++) {
                    ServiceInstance selected = Mono.from(loadBalancer.select()).block();
                    Assertions.assertEquals(alive.getPort(), selected.getPort(), "the ejected instance was selected");
                }
            }
        }
    }

    @Test
    void theOutcomeIsReportedForTheSelectedInstance() throws Exception {
        try (ServerUnderTest server = server()) {
            URI alive = server.getURL().get().toURI();
            URI aliveByName = URI.create(alive.getScheme() + "://localhost:" + alive.getPort());
            try (ApplicationContext clients = ApplicationContext.run(Map.of(
                "spec.name", SPEC_NAME + "-recording",
                "micronaut.http.services.recorded.urls", List.of(alive.toString(), aliveByName.toString())
            )); RawHttpClient client = rawClient(clients, "recorded")) {
                RecordingLoadBalancerFactory recording = clients.getBean(RecordingLoadBalancerFactory.class);
                LoadBalancer loadBalancer = clients.getBean(LoadBalancerResolver.class).resolve("recorded").orElseThrow();
                ServiceInstance byName = instance(loadBalancer, aliveByName);

                try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET("/raw-service-instance/host"), byName)) {
                    Assertions.assertEquals(200, response.code());
                    String host = new String(response.byteBody().buffer().get(30, TimeUnit.SECONDS).toByteArray(), StandardCharsets.UTF_8);
                    Assertions.assertTrue(host.startsWith("localhost"), "the request was sent to the selected instance: " + host);
                }
                Assertions.assertEquals(List.of(aliveByName + " SUCCESS"), awaitOutcomes(recording, 1));

                // an absolute URI is sent as it is, and its outcome is still the outcome of the instance
                try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(alive.resolve("/raw-service-instance/missing").toString()), byName)) {
                    Assertions.assertEquals(404, response.code());
                    // the buffered body is a new body the caller owns: closing the response does not release it
                    response.byteBody().buffer().get(30, TimeUnit.SECONDS).close();
                }
                Assertions.assertEquals(List.of(aliveByName + " SUCCESS", aliveByName + " SUCCESS"), awaitOutcomes(recording, 2));
            }
        }
    }

    @Test
    void anAbsoluteUriIsSentAsItIsAndItsOutcomeIsTheOutcomeOfTheSelectedInstance() throws Exception {
        try (ServerUnderTest server = server()) {
            URI dead = closedPort();
            URI alive = server.getURL().get().toURI();
            try (ApplicationContext clients = ApplicationContext.run(Map.of(
                "spec.name", SPEC_NAME + "-client",
                "micronaut.http.services.absolute.urls", List.of(dead.toString(), alive.toString()),
                "micronaut.http.services.absolute.outlier-detection.enabled", true,
                "micronaut.http.services.absolute.outlier-detection.consecutive-failures", 2,
                "micronaut.http.services.absolute.outlier-detection.base-ejection-time", "1m"
            )); RawHttpClient client = rawClient(clients, "absolute")) {
                LoadBalancer loadBalancer = clients.getBean(LoadBalancerResolver.class).resolve("absolute").orElseThrow();
                ServiceInstance deadInstance = instance(loadBalancer, dead);
                for (int i = 0; i < 2; i++) {
                    UnprocessedRequestException failure = Assertions.assertThrows(UnprocessedRequestException.class,
                        () -> exchange(client, HttpRequest.GET(dead.resolve("/raw-service-instance/ok").toString()), deadInstance));
                    Assertions.assertEquals(dead.getPort(), failure.getServiceInstance().orElseThrow().getPort());
                }
                for (int i = 0; i < 6; i++) {
                    ServiceInstance selected = Mono.from(loadBalancer.select()).block();
                    Assertions.assertEquals(alive.getPort(), selected.getPort(), "the ejected instance was selected");
                }
            }
        }
    }

    @Test
    void theOptionsKeepTheInstance() {
        ServiceInstance instance = ServiceInstance.of("pool", URI.create("http://127.0.0.1:8080"));
        RawRequestOptions options = RawRequestOptions.proxy().toBuilder().serviceInstance(instance).build();
        Assertions.assertSame(instance, options.getServiceInstance());
        Assertions.assertSame(instance, options.toBuilder().build().getServiceInstance());
        Assertions.assertNull(RawRequestOptions.proxy().getServiceInstance());
        Assertions.assertNotEquals(RawRequestOptions.proxy(), options);
        Assertions.assertEquals(RawRequestOptions.proxy(), options.toBuilder().serviceInstance(null).build());
    }

    /**
     * The instance of the load balancer with the URI: the instances of a service instance list
     * are selected in turn.
     */
    private static ServiceInstance instance(LoadBalancer loadBalancer, URI uri) {
        Set<URI> seen = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            ServiceInstance instance = Mono.from(loadBalancer.select()).block();
            if (instance.getURI().getPort() == uri.getPort() && instance.getURI().getHost().equals(uri.getHost())) {
                return instance;
            }
            seen.add(instance.getURI());
        }
        throw new AssertionError("No instance " + uri + " in " + seen);
    }

    private static List<String> awaitOutcomes(RecordingLoadBalancerFactory recording, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (recording.outcomes.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        Thread.sleep(100);
        return new ArrayList<>(recording.outcomes);
    }

    private static RawHttpClient rawClient(ApplicationContext context, String serviceId) {
        ServiceHttpClientConfiguration configuration = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName(serviceId));
        return context.getBean(RawHttpClientRegistry.class).getRawClient(HttpVersionSelection.forClientConfiguration(configuration), serviceId, null);
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, HttpRequest<?> request, ServiceInstance instance) {
        RawRequestOptions options = RawRequestOptions.proxy().toBuilder().serviceInstance(instance).build();
        HttpResponse<?> response = Mono.from(client.exchange(request, null, null, options)).block();
        return Assertions.assertInstanceOf(ByteBodyHttpResponse.class, response);
    }

    private static URI closedPort() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        return URI.create("http://127.0.0.1:" + port);
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Controller("/raw-service-instance")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class InstanceController {
        @Get(value = "/ok", produces = MediaType.TEXT_PLAIN)
        String ok() {
            return "ok";
        }

        @Get(value = "/host", produces = MediaType.TEXT_PLAIN)
        String host(HttpRequest<?> request) {
            return request.getHeaders().get("Host");
        }
    }

    /**
     * Records the instances and the outcomes the clients report to the load balancer of a service.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME + "-recording")
    @Replaces(ServiceInstanceListLoadBalancerFactory.class)
    static class RecordingLoadBalancerFactory extends ServiceInstanceListLoadBalancerFactory {
        final List<String> outcomes = new CopyOnWriteArrayList<>();

        @Override
        public LoadBalancer create(ServiceInstanceList serviceInstanceList) {
            LoadBalancer delegate = super.create(serviceInstanceList);
            return new LoadBalancer() {
                @Override
                public Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                    return delegate.select(discriminator);
                }

                @Override
                public void report(ServiceInstance serviceInstance, Outcome outcome) {
                    outcomes.add(serviceInstance.getURI() + " " + outcome);
                    delegate.report(serviceInstance, outcome);
                }
            };
        }
    }
}
