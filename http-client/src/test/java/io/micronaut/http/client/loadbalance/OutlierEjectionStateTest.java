package io.micronaut.http.client.loadbalance;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
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
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class OutlierEjectionStateTest {

    @Test
    void failingUpstreamShowsAsEjected() {
        try (EmbeddedServer healthy = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "OutlierEjectionStateTest", "upstream.status", 200));
             EmbeddedServer failing = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "OutlierEjectionStateTest", "upstream.status", 503));
             ApplicationContext ctx = ApplicationContext.run(Map.of(
                 "micronaut.http.services.upstream.urls", List.of(healthy.getURL().toString(), failing.getURL().toString()),
                 "micronaut.http.services.upstream.outlier-detection.enabled", true,
                 "micronaut.http.services.upstream.outlier-detection.consecutive-server-errors", 2,
                 "micronaut.http.services.upstream.outlier-detection.base-ejection-time", "1m"
             ))) {
            LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver.class).resolve("upstream").orElseThrow();
            Assertions.assertEquals(List.of(), loadBalancer.getOutlierEjectionStates(), "Nothing selected yet");

            ServiceHttpClientConfiguration configuration = ctx.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("upstream"));
            HttpClientRegistry<?> registry = ctx.getBean(HttpClientRegistry.class);
            try (HttpClient client = (HttpClient) registry.getClient(HttpVersionSelection.forClientConfiguration(configuration), "upstream", null)) {
                int failures = 0;
                for (int i = 0; i < 6; i++) {
                    try {
                        client.toBlocking().exchange(HttpRequest.GET("/upstream"), String.class);
                    } catch (HttpClientResponseException e) {
                        Assertions.assertEquals(503, e.getStatus().getCode());
                        failures++;
                    }
                }
                Assertions.assertEquals(2, failures, "The failing upstream is ejected after two errors");
            }

            List<OutlierEjectionState> states = loadBalancer.getOutlierEjectionStates();
            Assertions.assertEquals(2, states.size(), states.toString());
            OutlierEjectionState failingState = states.stream().filter(s -> s.uri().getPort() == failing.getPort()).findFirst().orElseThrow();
            OutlierEjectionState healthyState = states.stream().filter(s -> s.uri().getPort() == healthy.getPort()).findFirst().orElseThrow();
            Assertions.assertTrue(failingState.ejected(), failingState.toString());
            Assertions.assertEquals(1, failingState.ejectionCount());
            Assertions.assertNotNull(failingState.ejectedUntil());
            Assertions.assertFalse(healthyState.ejected(), healthyState.toString());
            Assertions.assertEquals(0, healthyState.ejectionCount());
            Assertions.assertNull(healthyState.ejectedUntil());
        }
    }

    @Requires(property = "spec.name", value = "OutlierEjectionStateTest")
    @Controller("/upstream")
    static class UpstreamController {
        private final int status;

        UpstreamController(@Value("${upstream.status}") int status) {
            this.status = status;
        }

        @Get
        HttpResponse<String> get() {
            return HttpResponse.<String>status(HttpStatus.valueOf(status)).body("ok");
        }
    }
}
