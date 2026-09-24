package io.micronaut.http.client.loadbalance;

import io.micronaut.context.ApplicationContext;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientRegistry;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class OutlierDetectionConfigurationTest {

    @Test
    void serviceOutlierDetectionIsBoundAndApplied() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "micronaut.http.services.flaky.urls", List.of("http://127.0.0.1:1", "http://127.0.0.1:2"),
            "micronaut.http.services.flaky.outlier-detection.enabled", true,
            "micronaut.http.services.flaky.outlier-detection.consecutive-failures", 2,
            "micronaut.http.services.flaky.outlier-detection.base-ejection-time", "1m"
        ))) {
            OutlierDetectionConfiguration configuration = ctx.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("flaky")).getOutlierDetection();
            Assertions.assertTrue(configuration.isEnabled());
            Assertions.assertEquals(2, configuration.getConsecutiveFailures());
            Assertions.assertEquals(Duration.ofMinutes(1), configuration.getBaseEjectionTime());

            LoadBalancerResolver resolver = ctx.getBean(LoadBalancerResolver.class);
            LoadBalancer loadBalancer = resolver.resolve("flaky").orElseThrow();
            Assertions.assertSame(loadBalancer, resolver.resolve("flaky").orElseThrow(), "One balancer per service");

            ServiceInstance first = Mono.from(loadBalancer.select(HttpRequest.GET("/"))).block();
            loadBalancer.report(first, LoadBalancer.Outcome.CONNECT_FAILURE);
            loadBalancer.report(first, LoadBalancer.Outcome.CONNECT_FAILURE);
            for (int i = 0; i < 4; i++) {
                ServiceInstance selected = Mono.from(loadBalancer.select(HttpRequest.GET("/"))).block();
                Assertions.assertNotEquals(first.getURI(), selected.getURI(), "The failing instance is ejected");
            }
        }
    }

    @Test
    void rawClientReportsItsFailuresToTheBalancer() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "micronaut.http.services.dead.urls", List.of("http://127.0.0.1:1", "http://127.0.0.1:2"),
            "micronaut.http.services.dead.outlier-detection.enabled", true,
            "micronaut.http.services.dead.outlier-detection.consecutive-failures", 1,
            "micronaut.http.services.dead.outlier-detection.max-ejection-percent", 50
        ))) {
            ServiceHttpClientConfiguration configuration = ctx.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("dead"));
            try (RawHttpClient client = ctx.getBean(RawHttpClientRegistry.class).getRawClient(HttpVersionSelection.forClientConfiguration(configuration), "dead", null)) {
                List<Integer> ports = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    UnprocessedRequestException failure = Assertions.assertThrows(UnprocessedRequestException.class,
                        () -> Mono.from(client.exchange(HttpRequest.GET("/"), null, null)).block());
                    ports.add(failure.getServiceInstance().orElseThrow().getPort());
                }
                // the first instance is ejected by its failure; ejecting the second too would exceed the maximum share
                Assertions.assertEquals(List.of(ports.get(0), ports.get(1), ports.get(1)), ports, "Selected ports: " + ports);
            }
        }
    }

    @Test
    void outlierDetectionIsOffByDefault() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "micronaut.http.services.plain.urls", List.of("http://127.0.0.1:1", "http://127.0.0.1:2")
        ))) {
            Assertions.assertFalse(ctx.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("plain")).getOutlierDetection().isEnabled());
            LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("plain").orElseThrow();
            ServiceInstance first = Mono.from(loadBalancer.select(HttpRequest.GET("/"))).block();
            loadBalancer.report(first, LoadBalancer.Outcome.CONNECT_FAILURE);
            loadBalancer.report(first, LoadBalancer.Outcome.CONNECT_FAILURE);
            // round robin: the failing instance is selected again
            Mono.from(loadBalancer.select(HttpRequest.GET("/"))).block();
            Assertions.assertEquals(first.getURI(), Mono.from(loadBalancer.select(HttpRequest.GET("/"))).block().getURI());
        }
    }
}
