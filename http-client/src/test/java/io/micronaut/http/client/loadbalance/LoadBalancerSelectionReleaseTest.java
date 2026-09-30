package io.micronaut.http.client.loadbalance;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every selection of an instance by the load balancer is reported exactly once, whether its
 * exchange completes, fails or is cancelled, so that the exchanges in flight of the p2c
 * strategy go back to zero.
 */
class LoadBalancerSelectionReleaseTest {

    private static final String SPEC = "LoadBalancerSelectionReleaseTest";

    @Test
    void aCancelledExchangeReleasesItsInstance() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
             ApplicationContext ctx = ApplicationContext.run(Map.of(
                 "spec.name", SPEC,
                 "micronaut.http.services.p2c.urls", List.of("http://localhost:" + server.getPort(), "http://127.0.0.1:" + server.getPort()),
                 "micronaut.http.services.p2c.load-balancer-strategy", "counting-p2c"
             ))) {
            HttpClient client = ctx.getBean(HttpClientRegistry.class).getClient(HttpVersion.HTTP_1_1, "p2c", null);
            CountingP2c strategy = strategy(ctx);

            Assertions.assertEquals("fast", client.toBlocking().retrieve("/lb-release/fast"));
            // the caller gives up before the response
            Mono<String> slow = Mono.from(client.retrieve(HttpRequest.GET("/lb-release/slow"), String.class)).timeout(Duration.ofMillis(300));
            Assertions.assertThrows(Exception.class, slow::block);
            awaitAllReleased(strategy);
            Assertions.assertEquals(1, strategy.outcomes.get(LoadBalancer.Outcome.CANCELLED).get(), strategy.outcomes.toString());
            Assertions.assertEquals(0, strategy.p2c.inFlight(ServiceInstance.of("p2c", URI.create("http://localhost:" + server.getPort()))));
            Assertions.assertEquals(0, strategy.p2c.inFlight(ServiceInstance.of("p2c", URI.create("http://127.0.0.1:" + server.getPort()))));
            Assertions.assertEquals("fast", client.toBlocking().retrieve("/lb-release/fast"));
            awaitAllReleased(strategy);
        }
    }

    @Test
    void aCancelledHttp2ExchangeReleasesItsInstance() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC, "micronaut.server.http-version", "2.0"));
             ApplicationContext ctx = ApplicationContext.run(Map.of(
                 "spec.name", SPEC,
                 "micronaut.http.services.p2c.urls", List.of("http://localhost:" + server.getPort(), "http://127.0.0.1:" + server.getPort()),
                 "micronaut.http.services.p2c.load-balancer-strategy", "counting-p2c"
             ))) {
            HttpClient client = ctx.getBean(HttpClientRegistry.class).getClient(HttpVersion.HTTP_2_0, "p2c", null);
            CountingP2c strategy = strategy(ctx);
            // one exchange per instance, so that both connections are HTTP/2 already
            Assertions.assertEquals("fast", client.toBlocking().retrieve("/lb-release/fast"));
            Assertions.assertEquals("fast", client.toBlocking().retrieve("/lb-release/fast"));
            Mono<String> slow = Mono.from(client.retrieve(HttpRequest.GET("/lb-release/slow"), String.class)).timeout(Duration.ofMillis(300));
            Assertions.assertThrows(Exception.class, slow::block);
            awaitAllReleased(strategy);
            Assertions.assertEquals(1, strategy.outcomes.get(LoadBalancer.Outcome.CANCELLED).get(), strategy.outcomes.toString());
        }
    }

    @Test
    void anExchangeOfAClosedClientReleasesItsInstance() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
             ApplicationContext ctx = ApplicationContext.run(Map.of(
                 "spec.name", SPEC,
                 "micronaut.http.services.p2c.urls", List.of("http://localhost:" + server.getPort(), "http://127.0.0.1:" + server.getPort()),
                 "micronaut.http.services.p2c.load-balancer-strategy", "counting-p2c"
             ))) {
            HttpClient client = ctx.getBean(HttpClientRegistry.class).getClient(HttpVersion.HTTP_1_1, "p2c", null);
            CountingP2c strategy = strategy(ctx);
            client.close();
            Assertions.assertThrows(Exception.class, () -> client.toBlocking().retrieve("/lb-release/fast"));
            awaitAllReleased(strategy);
        }
    }

    @Test
    void theSlowInstanceIsNotAvoidedAfterACancelledExchange() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
             ApplicationContext ctx = ApplicationContext.run(Map.of(
                 "spec.name", SPEC,
                 "micronaut.http.services.p2c.urls", List.of("http://localhost:" + server.getPort(), "http://127.0.0.1:" + server.getPort()),
                 "micronaut.http.services.p2c.load-balancer-strategy", "counting-p2c"
             ))) {
            HttpClient client = ctx.getBean(HttpClientRegistry.class).getClient(HttpVersion.HTTP_1_1, "p2c", null);
            CountingP2c strategy = strategy(ctx);
            Mono<String> slow = Mono.from(client.retrieve(HttpRequest.GET("/lb-release/slow"), String.class)).timeout(Duration.ofMillis(300));
            Assertions.assertThrows(Exception.class, slow::block);
            awaitAllReleased(strategy);
            Map<String, AtomicInteger> hosts = new java.util.concurrent.ConcurrentHashMap<>();
            strategy.selectedHosts = hosts;
            for (int i = 0; i < 50; i++) {
                client.toBlocking().retrieve("/lb-release/fast");
            }
            Assertions.assertEquals(2, hosts.size(), "Both instances are selected: " + hosts);
        }
    }

    private static CountingP2c strategy(ApplicationContext ctx) {
        LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("p2c").orElseThrow();
        return (CountingP2c) ((AbstractRoundRobinLoadBalancer) loadBalancer).getStrategy();
    }

    private static void awaitAllReleased(CountingP2c strategy) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (strategy.selected.get() != strategy.reported.get() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        Assertions.assertEquals(strategy.selected.get(), strategy.reported.get(), "Every selection is reported once: " + strategy.outcomes);
    }

    @Prototype
    @Named("counting-p2c")
    @Requires(property = "spec.name", value = SPEC)
    static final class CountingP2c implements LoadBalancerStrategy {
        final LoadBalancerStrategies.PowerOfTwoChoices p2c = new LoadBalancerStrategies.PowerOfTwoChoices();
        final AtomicInteger selected = new AtomicInteger();
        final AtomicInteger reported = new AtomicInteger();
        final Map<LoadBalancer.Outcome, AtomicInteger> outcomes = new EnumMap<>(LoadBalancer.Outcome.class);
        volatile @Nullable Map<String, AtomicInteger> selectedHosts;

        CountingP2c() {
            for (LoadBalancer.Outcome outcome : LoadBalancer.Outcome.values()) {
                outcomes.put(outcome, new AtomicInteger());
            }
        }

        @Override
        public ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator) {
            ServiceInstance instance = p2c.select(available, discriminator);
            selected.incrementAndGet();
            Map<String, AtomicInteger> hosts = selectedHosts;
            if (hosts != null) {
                hosts.computeIfAbsent(instance.getURI().getHost(), h -> new AtomicInteger()).incrementAndGet();
            }
            return instance;
        }

        @Override
        public void report(ServiceInstance instance, LoadBalancer.Outcome outcome) {
            p2c.report(instance, outcome);
            outcomes.get(outcome).incrementAndGet();
            reported.incrementAndGet();
        }
    }

    @Controller("/lb-release")
    @Requires(property = "spec.name", value = SPEC)
    static final class SlowController {
        @Get("/fast")
        String fast() {
            return "fast";
        }

        @Get("/slow")
        Mono<String> slow() {
            return Mono.just("slow").delayElement(Duration.ofSeconds(2));
        }
    }
}
