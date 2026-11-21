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
package io.micronaut.http.client.retry;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.client.ExchangeRetryConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.internal.ExchangeRetry;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exchange retry of the Netty client, see {@link ExchangeRetryConfiguration}.
 */
class ExchangeRetryTest {
    private static final String SPEC_NAME = "ExchangeRetryNettyTest";
    private static final String SERVICE = "retrying";

    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @Test
    void retriesA503ThenSucceeds() {
        try (ApplicationContext clients = clients(List.of(url()), Map.of()); HttpClient client = client(clients)) {
            String id = id();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET("/netty-retry/flaky/" + id + "/2"), String.class)).block());
            assertEquals("ok 3", response.body());
            assertEquals(3, Flaky.calls(id));
        }
    }

    @Test
    void retriesAnAbsoluteRequest() {
        try (ApplicationContext clients = clients(List.of(url()), Map.of()); HttpClient client = client(clients)) {
            String id = id();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET(url() + "/netty-retry/flaky/" + id + "/1"), String.class)).block());
            assertEquals("ok 2", response.body());
        }
    }

    @Test
    void retriesAnUnprocessedPostOnAnotherInstance() throws IOException {
        try (ApplicationContext clients = clients(List.of(closedPort(), url()), Map.of()); HttpClient client = client(clients)) {
            String id = id();
            for (int i = 0; i < 4; i++) {
                HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.POST("/netty-retry/ok/" + id, "body").contentType(MediaType.TEXT_PLAIN_TYPE), String.class)).block());
                assertEquals("body", response.body());
            }
            assertEquals(4, Flaky.calls(id));
        }
    }

    @Test
    void doesNotRetryAStatusOrAMethodThatIsNotRetried() {
        try (ApplicationContext clients = clients(List.of(url()), Map.of()); HttpClient client = client(clients)) {
            String bad = id();
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/netty-retry/status/" + bad + "/400"), String.class)).block());
            assertEquals(400, e.code());
            assertEquals(1, Flaky.calls(bad));
            String post = id();
            e = assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.POST("/netty-retry/status/" + post + "/503", "x").contentType(MediaType.TEXT_PLAIN_TYPE), String.class)).block());
            assertEquals(503, e.code());
            assertEquals(1, Flaky.calls(post));
        }
    }

    @Test
    void doesNotRetryAStreamedBody() {
        try (ApplicationContext clients = clients(List.of(url()), Map.of()); HttpClient client = client(clients)) {
            String id = id();
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.PUT("/netty-retry/status/" + id + "/503", Flux.just("streamed")).contentType(MediaType.TEXT_PLAIN_TYPE), String.class)).block());
            assertEquals(503, e.code());
            assertEquals(1, Flaky.calls(id));
        }
    }

    @Test
    void timesOutAnAttemptAndRetries() {
        try (ApplicationContext clients = clients(List.of(url()), Map.of(
            "micronaut.http.services." + SERVICE + ".retry.attempt-timeout", "300ms"
        )); HttpClient client = client(clients)) {
            String id = id();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET("/netty-retry/slow/" + id), String.class)).block());
            assertEquals("fast 2", response.body());
        }
    }

    @Test
    void timesOutTheSingleAttemptOfAnExchangeThatIsNotRetried() {
        try (ApplicationContext clients = clients(List.of(url()), Map.of(
            "micronaut.http.services." + SERVICE + ".retry.max-attempts", 1,
            "micronaut.http.services." + SERVICE + ".retry.attempt-timeout", "300ms"
        )); HttpClient client = client(clients)) {
            String id = id();
            long start = System.nanoTime();
            assertThrows(ReadTimeoutException.class, () -> Mono.from(client.exchange(HttpRequest.GET("/netty-retry/slow/" + id), String.class)).block());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 10_000);
            assertEquals(1, Flaky.calls(id));
        }
    }

    @Test
    void doesNotRetryAnExchangeCancelledWhileWaiting() throws InterruptedException {
        try (ApplicationContext clients = clients(List.of(url()), Map.of()); HttpClient client = client(clients)) {
            String id = id();
            assertThrows(Exception.class, () -> Mono.from(client.exchange(HttpRequest.GET("/netty-retry/later/" + id + "/1"), String.class))
                .timeout(Duration.ofMillis(300))
                .block());
            Thread.sleep(1500);
            assertEquals(1, Flaky.calls(id));
        }
    }

    @Test
    void isDisabledByDefault() {
        try (ApplicationContext clients = clients(List.of(url()), Map.of(
            "micronaut.http.services." + SERVICE + ".retry.enabled", false
        )); HttpClient client = client(clients)) {
            String id = id();
            assertThrows(HttpClientResponseException.class, () -> Mono.from(client.exchange(HttpRequest.GET("/netty-retry/flaky/" + id + "/1"), String.class)).block());
            assertEquals(1, Flaky.calls(id));
        }
    }

    @Test
    void bindsTheConfigurationOfAService() {
        try (ApplicationContext context = ApplicationContext.run(Map.ofEntries(
            Map.entry("micronaut.retry.policies.quick.delay", "5ms"),
            Map.entry("micronaut.http.services.foo.url", "http://localhost:1"),
            Map.entry("micronaut.http.services.foo.retry.enabled", true),
            Map.entry("micronaut.http.services.foo.retry.policy", "quick"),
            Map.entry("micronaut.http.services.foo.retry.max-attempts", 3),
            Map.entry("micronaut.http.services.foo.retry.statuses", List.of(429)),
            Map.entry("micronaut.http.services.foo.retry.status-classes", List.of("5xx")),
            Map.entry("micronaut.http.services.foo.retry.attempt-timeout", "2s"),
            Map.entry("micronaut.http.services.foo.retry.budget.max-tokens", 7),
            Map.entry("micronaut.http.services.foo.retry.budget.token-ratio", 0.5),
            Map.entry("micronaut.http.services.bar.url", "http://localhost:2")
        ))) {
            ServiceHttpClientConfiguration foo = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("foo"));
            ExchangeRetryConfiguration retry = foo.getRetryConfiguration();
            assertTrue(retry.isEnabled());
            assertEquals("quick", retry.getPolicy());
            assertEquals(3, retry.getMaxAttempts());
            assertEquals(List.of(429), retry.getStatuses());
            assertEquals(List.of("5xx"), retry.getStatusClasses());
            assertEquals(Duration.ofSeconds(2), retry.getAttemptTimeout());
            assertEquals(7, retry.getBudget().getMaxTokens());
            assertEquals(0.5, retry.getBudget().getTokenRatio());
            ExchangeRetry exchangeRetry = foo.exchangeRetry();
            assertNotNull(exchangeRetry);
            assertSame(exchangeRetry, foo.exchangeRetry(), "created once");
            assertEquals(3, exchangeRetry.maxAttempts());
            assertEquals(Duration.ofSeconds(2), exchangeRetry.attemptTimeout());

            ServiceHttpClientConfiguration bar = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("bar"));
            assertFalse(bar.getRetryConfiguration().isEnabled());
            assertNull(bar.exchangeRetry());
        }
    }

    @Test
    void anUnknownPolicyFailsTheCreation() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.http.services.foo.url", "http://localhost:1",
            "micronaut.http.services.foo.retry.enabled", true,
            "micronaut.http.services.foo.retry.policy", "missing"
        ))) {
            ServiceHttpClientConfiguration foo = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("foo"));
            assertThrows(io.micronaut.context.exceptions.ConfigurationException.class, foo::exchangeRetry);
        }
    }
    private static ApplicationContext clients(List<String> urls, Map<String, Object> extra) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("micronaut.retry.policies.fast.delay", "10ms");
        properties.put("micronaut.retry.policies.fast.attempts", 5);
        properties.put("micronaut.http.services." + SERVICE + ".urls", urls);
        properties.put("micronaut.http.services." + SERVICE + ".read-timeout", "30s");
        properties.put("micronaut.http.services." + SERVICE + ".retry.enabled", true);
        properties.put("micronaut.http.services." + SERVICE + ".retry.policy", "fast");
        properties.putAll(extra);
        return ApplicationContext.run(properties);
    }

    private static HttpClient client(ApplicationContext context) {
        ServiceHttpClientConfiguration configuration = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName(SERVICE));
        return context.getBean(HttpClientRegistry.class).getClient(HttpVersionSelection.forClientConfiguration(configuration), SERVICE, null);
    }

    private static String url() {
        return server.getURL().toString();
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static String closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }
    }

    @Controller("/netty-retry")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Flaky {
        static final Map<String, List<String>> CALLS = new ConcurrentHashMap<>();

        static int calls(String id) {
            return CALLS.getOrDefault(id, List.of()).size();
        }

        private static int record(String id) {
            List<String> calls = CALLS.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>());
            calls.add(id);
            return calls.size();
        }

        @Get(value = "/flaky/{id}/{failures}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> flaky(String id, int failures) {
            int call = record(id);
            return call <= failures ? HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).body("down") : HttpResponse.ok("ok " + call);
        }

        @Get(value = "/later/{id}/{seconds}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> later(String id, int seconds) {
            int call = record(id);
            return call == 1 ? HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds)).body("later") : HttpResponse.ok("ok " + call);
        }

        @Get(value = "/status/{id}/{status}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> status(String id, int status) {
            record(id);
            return HttpResponse.<String>status(HttpStatus.valueOf(status)).body("status " + status);
        }

        @Post(value = "/status/{id}/{status}", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> statusPost(String id, int status, @Body String body) {
            return status(id, status);
        }

        @Put(value = "/status/{id}/{status}", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> statusPut(String id, int status, @Body String body) {
            return status(id, status);
        }

        @Post(value = "/ok/{id}", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String ok(String id, @Body String body) {
            record(id);
            return body;
        }

        @Get(value = "/slow/{id}", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow(String id) {
            int call = record(id);
            return call == 1 ? Mono.never() : Mono.just("fast " + call);
        }
    }
}
