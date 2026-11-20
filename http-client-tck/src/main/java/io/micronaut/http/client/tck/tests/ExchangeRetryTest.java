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
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientRegistry;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.loadbalance.ServiceInstanceListLoadBalancerFactory;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * The retry of the exchanges of a service client, decided from the outcome of each attempt, see
 * {@link io.micronaut.http.client.ExchangeRetryConfiguration}.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class ExchangeRetryTest {
    static final String SPEC_NAME = "ExchangeRetryTest";
    private static final String SERVICE = "retrying";

    @Test
    void retriesA503ThenSucceeds() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of());
             HttpClient client = client(clients)) {
            String id = id();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/flaky/" + id + "/1"), String.class)).block(), "response");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("ok 2", response.body());
            Assertions.assertEquals(2, RetryController.calls(id));
        }
    }

    @Test
    void doesNotRetryA400() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of());
             HttpClient client = client(clients)) {
            String id = id();
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/status/" + id + "/400"), String.class)).block());
            Assertions.assertEquals(400, e.code());
            Assertions.assertEquals(1, RetryController.calls(id));
        }
    }

    @Test
    void doesNotRetryAPostOnA503() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of());
             HttpClient client = client(clients)) {
            String id = id();
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.POST("/exchange-retry/status/" + id + "/503", "body").contentType(MediaType.TEXT_PLAIN_TYPE), String.class)).block());
            Assertions.assertEquals(503, e.code());
            Assertions.assertEquals(1, RetryController.calls(id));
        }
    }

    @Test
    void retriesAnUnprocessedPostOnAnotherInstance() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(closedPort(), uri(server)), Map.of());
             HttpClient client = client(clients)) {
            String id = id();
            int requests = 4;
            for (int i = 0; i < requests; i++) {
                HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.POST("/exchange-retry/ok/" + id, "body " + i).contentType(MediaType.TEXT_PLAIN_TYPE), String.class)).block(), "response");
                Assertions.assertEquals(200, response.code());
                Assertions.assertEquals("body " + i, response.body());
            }
            // the round robin sent some of them to the closed port first: none was processed twice
            Assertions.assertEquals(requests, RetryController.calls(id));
        }
    }

    @Test
    void timesOutAnAttempt() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".retry.attempt-timeout", "500ms"
             ));
             HttpClient client = client(clients)) {
            String id = id();
            long start = System.nanoTime();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/slow/" + id), String.class)).block(), "response");
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            Assertions.assertEquals("fast 2", response.body());
            Assertions.assertEquals(2, RetryController.calls(id));
            Assertions.assertTrue(elapsed < 10_000, "The first attempt timed out: " + elapsed + "ms");
        }
    }

    @Test
    void sendsEachAttemptToAnotherInstance() throws Exception {
        try (ServerUnderTest first = server();
             ServerUnderTest second = server();
             ApplicationContext clients = clients(List.of(uri(first), uri(second)), Map.of(
                 "spec.name", SPEC_NAME + "-recording",
                 "micronaut.http.services." + SERVICE + ".retry.max-attempts", 2
             ));
             HttpClient client = client(clients)) {
            RecordingLoadBalancerFactory recording = clients.getBean(RecordingLoadBalancerFactory.class);
            String id = id();
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/port/" + id), String.class)).block());
            Assertions.assertEquals(503, e.code());
            Set<Integer> ports = Set.of(first.getPort().orElseThrow(), second.getPort().orElseThrow());
            Assertions.assertEquals(ports, Set.copyOf(RetryController.PORTS.get(id)), "Each attempt went to another instance");
            Assertions.assertEquals(List.of(LoadBalancer.Outcome.SERVER_ERROR, LoadBalancer.Outcome.SERVER_ERROR), awaitOutcomes(recording, 2));
        }
    }

    @Test
    void capsTheAttempts() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".retry.max-attempts", 3
             ));
             HttpClient client = client(clients)) {
            String id = id();
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/status/" + id + "/503"), String.class)).block());
            Assertions.assertEquals(503, e.code());
            // the policy allows five retries, the configuration three attempts
            Assertions.assertEquals(3, RetryController.calls(id));
        }
    }

    @Test
    void retriesABufferedPutBody() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of());
             HttpClient client = client(clients)) {
            String id = id();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.PUT("/exchange-retry/flaky/" + id + "/2", "payload").contentType(MediaType.TEXT_PLAIN_TYPE), String.class)).block(), "response");
            Assertions.assertEquals("payload", response.body());
            Assertions.assertEquals(List.of("payload", "payload", "payload"), RetryController.BODIES.get(id));
        }
    }

    @Test
    void retriesADeclarativeClient() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of())) {
            String id = id();
            Assertions.assertEquals("ok 3", clients.getBean(RetryingClient.class).flaky(id, 2));
            Assertions.assertEquals(3, RetryController.calls(id));
        }
    }

    @Test
    void isDisabledByDefault() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".retry.enabled", false
             ));
             HttpClient client = client(clients)) {
            String id = id();
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/flaky/" + id + "/1"), String.class)).block());
            Assertions.assertEquals(503, e.code());
            Assertions.assertEquals(1, RetryController.calls(id));
        }
    }

    @Test
    void waitsForTheRetryAfterSecondsOfA503() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of());
             HttpClient client = client(clients)) {
            String id = id();
            long start = System.nanoTime();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/retry-after/" + id + "/503/1"), String.class)).block(), "response");
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            Assertions.assertEquals("ok 2", response.body());
            Assertions.assertEquals(2, RetryController.calls(id));
            // the policy waits 10ms, the header one second
            Assertions.assertTrue(elapsed >= 950, "The retry waited for Retry-After: " + elapsed + "ms");
        }
    }

    @Test
    void waitsForTheRetryAfterDateOfA503() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of());
             HttpClient client = client(clients)) {
            String id = id();
            long start = System.nanoTime();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/retry-after-date/" + id + "/2"), String.class)).block(), "response");
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            Assertions.assertEquals("ok 2", response.body());
            Assertions.assertEquals(2, RetryController.calls(id));
            // the date is two seconds ahead, to the second: at least one second ahead
            Assertions.assertTrue(elapsed >= 800, "The retry waited for the Retry-After date: " + elapsed + "ms");
        }
    }

    @Test
    void retriesA429WithRetryAfterWhenConfigured() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".retry.statuses", List.of(429, 503)
             ));
             HttpClient client = client(clients)) {
            String id = id();
            long start = System.nanoTime();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/retry-after/" + id + "/429/1"), String.class)).block(), "response");
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            Assertions.assertEquals("ok 2", response.body());
            Assertions.assertEquals(2, RetryController.calls(id));
            Assertions.assertTrue(elapsed >= 950, "The retry waited for Retry-After: " + elapsed + "ms");
        }
    }

    @Test
    void doesNotRetryA429ByDefault() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of());
             HttpClient client = client(clients)) {
            String id = id();
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/retry-after/" + id + "/429/1"), String.class)).block());
            Assertions.assertEquals(429, e.code());
            Assertions.assertEquals(1, RetryController.calls(id));
        }
    }

    @Test
    void doesNotRetryARetryAfterLongerThanTheMaximum() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".retry.max-retry-after", "1s"
             ));
             HttpClient client = client(clients)) {
            String id = id();
            long start = System.nanoTime();
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/retry-after/" + id + "/503/5"), String.class)).block());
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            Assertions.assertEquals(503, e.code());
            Assertions.assertEquals("5", e.getResponse().getHeaders().get(HttpHeaders.RETRY_AFTER));
            Assertions.assertEquals(1, RetryController.calls(id));
            Assertions.assertTrue(elapsed < 5000, "The exchange did not wait: " + elapsed + "ms");
        }
    }

    @Test
    void ignoresRetryAfterWhenNotRespected() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".retry.respect-retry-after", false
             ));
             HttpClient client = client(clients)) {
            String id = id();
            long start = System.nanoTime();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/retry-after/" + id + "/503/5"), String.class)).block(), "response");
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            Assertions.assertEquals("ok 2", response.body());
            Assertions.assertTrue(elapsed < 5000, "The retry did not wait for Retry-After: " + elapsed + "ms");
        }
    }

    @Test
    void doesNotRetryStreamingOrRawExchanges() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of());
             HttpClient client = client(clients);
             RawHttpClient raw = rawClient(clients)) {
            String streamed = id();
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Flux.from(((StreamingHttpClient) client).dataStream(HttpRequest.GET("/exchange-retry/flaky/" + streamed + "/1"))).blockLast());
            Assertions.assertEquals(503, e.code());
            Assertions.assertEquals(1, RetryController.calls(streamed));

            String rawId = id();
            HttpResponse<?> response = Objects.requireNonNull(Mono.from(raw.exchange(HttpRequest.GET("/exchange-retry/flaky/" + rawId + "/1"), null, null)).block(), "response");
            try (ByteBodyHttpResponse<?> rawResponse = Assertions.assertInstanceOf(ByteBodyHttpResponse.class, response)) {
                Assertions.assertEquals(503, rawResponse.code());
            }
            Assertions.assertEquals(1, RetryController.calls(rawId));
        }
    }

    @Test
    void decidesTheStatusBeforeReadingTheBody() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".max-content-length", 1024,
                 // a body over the limit would fail the attempt, and a failure is not retried
                 "micronaut.http.services." + SERVICE + ".retry.retry-on-failure", false
             ));
             HttpClient client = client(clients)) {
            String id = id();
            HttpResponse<String> response = Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/big/" + id), String.class)).block(), "response");
            Assertions.assertEquals("ok 2", response.body());
            Assertions.assertEquals(2, RetryController.calls(id));
        }
    }

    @Test
    @ClientDisabledCondition.ClientDisabled(httpClient = ClientDisabledCondition.JDK) // the JDK client sends no multipart body
    void doesNotRetryAMultipartBodyWithAnInputStream() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of());
             HttpClient client = client(clients)) {
            String id = id();
            MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.txt", MediaType.TEXT_PLAIN_TYPE, new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), 7)
                .build();
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.PUT("/exchange-retry/multipart/" + id, body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String.class)).block());
            Assertions.assertEquals(503, e.code());
            Assertions.assertEquals(1, RetryController.calls(id));
        }
    }

    @Test
    @ClientDisabledCondition.ClientDisabled(httpClient = ClientDisabledCondition.JDK) // the JDK client sends no netty buffer
    void doesNotRetryAReferenceCountedBuffer() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of());
             HttpClient client = client(clients)) {
            String id = id();
            ByteBuf body = Unpooled.copiedBuffer("buffer", StandardCharsets.UTF_8);
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.PUT("/exchange-retry/status/" + id + "/503", body).contentType(MediaType.TEXT_PLAIN_TYPE), String.class)).block());
            Assertions.assertEquals(503, e.code());
            Assertions.assertEquals(1, RetryController.calls(id));
        }
    }

    @Test
    void appliesTheAttemptTimeoutWithoutRetries() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".retry.max-attempts", 1,
                 "micronaut.http.services." + SERVICE + ".retry.attempt-timeout", "500ms"
             ));
             HttpClient client = client(clients)) {
            String id = id();
            long start = System.nanoTime();
            Assertions.assertThrows(ReadTimeoutException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/slow/" + id), String.class)).block());
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            Assertions.assertEquals(1, RetryController.calls(id));
            Assertions.assertTrue(elapsed < 10_000, "The attempt timed out: " + elapsed + "ms");
        }
    }

    @Test
    void survivesAnAttemptTimeoutThatElapsesAtOnce() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".retry.max-attempts", 3,
                 "micronaut.http.services." + SERVICE + ".retry.attempt-timeout", "1ms"
             ));
             HttpClient client = client(clients)) {
            for (int i = 0; i < 20; i++) {
                String id = id();
                try {
                    Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/flaky/" + id + "/0"), String.class)).block();
                } catch (ReadTimeoutException e) {
                    // the timeout elapsed before the response
                }
            }
        }
    }

    @Test
    void doesNotWaitBeyondTheMaxDelayOfThePolicy() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.retry.policies.fast.max-delay", "500ms"
             ));
             HttpClient client = client(clients)) {
            String id = id();
            long start = System.nanoTime();
            HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/retry-after/" + id + "/503/2"), String.class)).block());
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            Assertions.assertEquals(503, e.code());
            Assertions.assertEquals(1, RetryController.calls(id));
            Assertions.assertTrue(elapsed < 2000, "The retry was not scheduled after the max delay: " + elapsed + "ms");
        }
    }

    @Test
    void stopsRetryingWhenTheBudgetIsSpent() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".retry.budget.max-tokens", 4,
                 "micronaut.http.services." + SERVICE + ".retry.budget.token-ratio", 1
             ));
             HttpClient client = client(clients)) {
            // four tokens: a retry is made while more than two are left
            String first = id();
            Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/status/" + first + "/503"), String.class)).block());
            Assertions.assertEquals(2, RetryController.calls(first), "One retry, then the budget is at its half");
            String second = id();
            Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/status/" + second + "/503"), String.class)).block());
            Assertions.assertEquals(1, RetryController.calls(second), "No retry once the budget is spent");
            // successes put tokens back
            for (int i = 0; i < 4; i++) {
                Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/flaky/" + id() + "/0"), String.class)).block();
            }
            String third = id();
            Assertions.assertEquals("ok 2", Objects.requireNonNull(Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/flaky/" + third + "/1"), String.class)).block(), "response").body());
        }
    }

    @Test
    void retriesWithoutBudgetWhenDisabled() throws Exception {
        try (ServerUnderTest server = server();
             ApplicationContext clients = clients(List.of(uri(server)), Map.of(
                 "micronaut.http.services." + SERVICE + ".retry.budget.enabled", false,
                 "micronaut.http.services." + SERVICE + ".retry.budget.max-tokens", 1
             ));
             HttpClient client = client(clients)) {
            String id = id();
            Assertions.assertThrows(HttpClientResponseException.class,
                () -> Mono.from(client.exchange(HttpRequest.GET("/exchange-retry/status/" + id + "/503"), String.class)).block());
            // the policy allows five retries
            Assertions.assertEquals(6, RetryController.calls(id));
        }
    }

    private static List<LoadBalancer.Outcome> awaitOutcomes(RecordingLoadBalancerFactory recording, int expected) throws InterruptedException {
        Assertions.assertTrue(recording.reported.tryAcquire(expected, 10, TimeUnit.SECONDS), "Outcomes reported: " + recording.outcomes);
        // a report that should not come
        Assertions.assertFalse(recording.reported.tryAcquire(100, TimeUnit.MILLISECONDS), "Unexpected extra outcome: " + recording.outcomes);
        return List.copyOf(recording.outcomes);
    }

    private static ApplicationContext clients(List<URI> urls, Map<String, Object> extra) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spec.name", SPEC_NAME + "-client");
        properties.put("micronaut.retry.policies.fast.delay", "10ms");
        properties.put("micronaut.retry.policies.fast.attempts", 5);
        properties.put("micronaut.http.services." + SERVICE + ".urls", urls.stream().map(URI::toString).toList());
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

    private static RawHttpClient rawClient(ApplicationContext context) {
        ServiceHttpClientConfiguration configuration = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName(SERVICE));
        return context.getBean(RawHttpClientRegistry.class).getRawClient(HttpVersionSelection.forClientConfiguration(configuration), SERVICE, null);
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static URI uri(ServerUnderTest server) throws Exception {
        return server.getURL().orElseThrow().toURI();
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

    @Client(id = SERVICE)
    @Requires(property = "spec.name", value = SPEC_NAME + "-client")
    interface RetryingClient {
        @Get(value = "/exchange-retry/flaky/{id}/{failures}", consumes = MediaType.TEXT_PLAIN)
        String flaky(String id, int failures);
    }

    @Controller("/exchange-retry")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RetryController {
        static final Map<String, List<String>> CALLS = new ConcurrentHashMap<>();
        static final Map<String, List<String>> BODIES = new ConcurrentHashMap<>();
        static final Map<String, List<Integer>> PORTS = new ConcurrentHashMap<>();

        static int calls(String id) {
            return CALLS.getOrDefault(id, List.of()).size();
        }

        private static int record(String id, HttpRequest<?> request) {
            List<String> calls = CALLS.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>());
            calls.add(request.getMethodName());
            PORTS.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(request.getServerAddress().getPort());
            return calls.size();
        }

        @Get(value = "/flaky/{id}/{failures}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> flaky(String id, int failures, HttpRequest<?> request) {
            int call = record(id, request);
            return call <= failures ? HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).body("down") : HttpResponse.ok("ok " + call);
        }

        @Put(value = "/flaky/{id}/{failures}", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> flakyPut(String id, int failures, @Body String body, HttpRequest<?> request) {
            int call = record(id, request);
            BODIES.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(body);
            return call <= failures ? HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).body("down") : HttpResponse.ok(body);
        }

        @Get(value = "/status/{id}/{status}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> status(String id, int status, HttpRequest<?> request) {
            record(id, request);
            return HttpResponse.<String>status(HttpStatus.valueOf(status)).body("status " + status);
        }

        @Post(value = "/status/{id}/{status}", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> statusPost(String id, int status, @Body String body, HttpRequest<?> request) {
            return status(id, status, request);
        }

        @Put(value = "/status/{id}/{status}", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> statusPut(String id, int status, @Body String body, HttpRequest<?> request) {
            return status(id, status, request);
        }

        @Post(value = "/ok/{id}", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String ok(String id, @Body String body, HttpRequest<?> request) {
            record(id, request);
            return body;
        }

        @Get(value = "/retry-after/{id}/{status}/{seconds}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> retryAfter(String id, int status, int seconds, HttpRequest<?> request) {
            int call = record(id, request);
            if (call == 1) {
                return HttpResponse.<String>status(HttpStatus.valueOf(status)).header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds)).body("later");
            }
            return HttpResponse.ok("ok " + call);
        }

        @Get(value = "/retry-after-date/{id}/{seconds}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> retryAfterDate(String id, int seconds, HttpRequest<?> request) {
            int call = record(id, request);
            if (call == 1) {
                String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(seconds));
                return HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, date).body("later");
            }
            return HttpResponse.ok("ok " + call);
        }

        @Get(value = "/big/{id}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> big(String id, HttpRequest<?> request) {
            int call = record(id, request);
            return call == 1 ? HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).body("x".repeat(64 * 1024)) : HttpResponse.ok("ok " + call);
        }

        @Put(value = "/multipart/{id}", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> multipart(String id, HttpRequest<?> request) {
            record(id, request);
            return HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).body("down");
        }

        @Get(value = "/port/{id}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> port(String id, HttpRequest<?> request) {
            record(id, request);
            return HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).body("down");
        }

        @Get(value = "/slow/{id}", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow(String id, HttpRequest<?> request) {
            int call = record(id, request);
            // the first attempt never gets a response
            return call == 1 ? Mono.never() : Mono.just("fast " + call);
        }
    }

    /**
     * Records the outcomes the clients report to the load balancer of a service.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME + "-recording")
    @Replaces(ServiceInstanceListLoadBalancerFactory.class)
    static class RecordingLoadBalancerFactory extends ServiceInstanceListLoadBalancerFactory {
        final List<LoadBalancer.Outcome> outcomes = new CopyOnWriteArrayList<>();
        final Semaphore reported = new Semaphore(0);

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
                    outcomes.add(outcome);
                    reported.release();
                    delegate.report(serviceInstance, outcome);
                }
            };
        }
    }
}
