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
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientRegistry;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A load balanced raw client reports its exchanges to the load balancer: with outlier detection,
 * an instance that keeps failing stops being selected.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class LoadBalancerFeedbackTest {
    static final String SPEC_NAME = "LoadBalancerFeedbackTest";
    private static final int REQUESTS = 12;

    @Test
    void failingInstanceIsEjected() throws Exception {
        try (ServerUnderTest server = server()) {
            URI dead = closedPort();
            URI alive = server.getURL().get().toURI();
            try (ApplicationContext clients = ApplicationContext.run(Map.of(
                "spec.name", SPEC_NAME + "-client",
                "micronaut.http.services.flaky.urls", List.of(dead.toString(), alive.toString()),
                "micronaut.http.services.flaky.outlier-detection.enabled", true,
                "micronaut.http.services.flaky.outlier-detection.consecutive-failures", 2,
                "micronaut.http.services.flaky.outlier-detection.base-ejection-time", "1m"
            )); RawHttpClient client = rawClient(clients, "flaky")) {
                List<UnprocessedRequestException> failures = new ArrayList<>();
                StringBuilder sequence = new StringBuilder();
                for (int i = 0; i < REQUESTS; i++) {
                    try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET("/load-balancer-feedback/ok"))) {
                        Assertions.assertEquals(200, response.code());
                        sequence.append("alive ");
                    } catch (UnprocessedRequestException e) {
                        Assertions.assertEquals(UnprocessedRequestException.Reason.CONNECT, e.getReason());
                        Assertions.assertEquals(dead.getPort(), e.getServiceInstance().orElseThrow().getPort());
                        sequence.append("dead ");
                        Assertions.assertTrue(i < REQUESTS / 2, "The dead instance was still selected for request " + i + ": " + sequence);
                        failures.add(e);
                    }
                }
                Assertions.assertFalse(failures.isEmpty(), "The dead instance was never selected");
                Assertions.assertTrue(failures.size() <= 2, "The dead instance was selected " + failures.size() + " times, it should have been ejected after 2 failures");
            }
        }
    }

    @Test
    void truncatedRawResponseIsAFailure() throws Exception {
        try (TruncatingServer truncating = new TruncatingServer()) {
            try (ApplicationContext clients = recordingClients(truncating.uri());
                 RawHttpClient client = rawClient(clients, "truncating")) {
                RecordingLoadBalancerFactory recording = clients.getBean(RecordingLoadBalancerFactory.class);
                try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET("/truncated"))) {
                    Assertions.assertEquals(200, response.code());
                    Assertions.assertTrue(recording.outcomes.isEmpty(), "Nothing is reported before the body ends: " + recording.outcomes);
                    ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                        () -> response.byteBody().buffer().get(30, TimeUnit.SECONDS));
                    Assertions.assertInstanceOf(HttpClientException.class, failure.getCause());
                }
                Assertions.assertEquals(List.of(LoadBalancer.Outcome.RESET), recording.outcomes);
            }
        }
    }

    @Test
    void truncatedBufferedResponseIsAFailure() throws Exception {
        try (TruncatingServer truncating = new TruncatingServer()) {
            try (ApplicationContext clients = recordingClients(truncating.uri());
                 HttpClient client = bufferingClient(clients, "truncating")) {
                RecordingLoadBalancerFactory recording = clients.getBean(RecordingLoadBalancerFactory.class);
                Assertions.assertThrows(HttpClientException.class,
                    () -> Mono.from(client.exchange(HttpRequest.GET("/truncated"), String.class)).block());
                Assertions.assertEquals(List.of(LoadBalancer.Outcome.RESET), recording.outcomes);
            }
        }
    }

    @Test
    void completeResponseIsASuccess() throws Exception {
        try (ServerUnderTest server = server()) {
            URI alive = server.getURL().get().toURI();
            try (ApplicationContext clients = recordingClients(alive);
                 RawHttpClient client = rawClient(clients, "truncating")) {
                RecordingLoadBalancerFactory recording = clients.getBean(RecordingLoadBalancerFactory.class);
                try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET("/load-balancer-feedback/ok"))) {
                    Assertions.assertEquals(200, response.code());
                    Assertions.assertEquals("ok", new String(response.byteBody().buffer().get(30, TimeUnit.SECONDS).toByteArray(), StandardCharsets.UTF_8));
                }
                Assertions.assertEquals(List.of(LoadBalancer.Outcome.SUCCESS), recording.outcomes);
            }
        }
    }

    private static ApplicationContext recordingClients(URI instance) {
        return ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME + "-recording",
            "micronaut.http.services.truncating.urls", List.of(instance.toString()),
            "micronaut.http.services.truncating.read-timeout", "30s"
        ));
    }

    private static HttpClient bufferingClient(ApplicationContext context, String serviceId) {
        ServiceHttpClientConfiguration configuration = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName(serviceId));
        return context.getBean(HttpClientRegistry.class).getClient(HttpVersionSelection.forClientConfiguration(configuration), serviceId, null);
    }

    private static RawHttpClient rawClient(ApplicationContext context, String serviceId) {
        ServiceHttpClientConfiguration configuration = context.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName(serviceId));
        return context.getBean(RawHttpClientRegistry.class).getRawClient(HttpVersionSelection.forClientConfiguration(configuration), serviceId, null);
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, HttpRequest<?> request) {
        HttpResponse<?> response = Mono.from(client.exchange(request, null, null, RawRequestOptions.proxy())).block();
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

    @Controller("/load-balancer-feedback")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OkController {
        @Get(value = "/ok", produces = MediaType.TEXT_PLAIN)
        String ok() {
            return "ok";
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
                    delegate.report(serviceInstance, outcome);
                }
            };
        }
    }

    /**
     * A server that answers every request with a {@code 200} whose body is cut off: the
     * connection is closed after fewer bytes than the {@code Content-Length} announces.
     */
    static final class TruncatingServer implements AutoCloseable {
        private final ServerSocket socket;
        private final Thread thread;

        TruncatingServer() throws IOException {
            socket = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
            thread = new Thread(this::serve, "truncating-server");
            thread.setDaemon(true);
            thread.start();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + socket.getLocalPort());
        }

        private void serve() {
            while (!socket.isClosed()) {
                try (Socket connection = socket.accept()) {
                    connection.setSoTimeout(30_000);
                    readRequestHead(connection.getInputStream());
                    OutputStream out = connection.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 100\r\n\r\npartial").getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (IOException ignored) {
                    // the server is closed, or the client went away
                }
            }
        }

        private static void readRequestHead(InputStream in) throws IOException {
            int matched = 0;
            int b;
            while (matched < 4 && (b = in.read()) != -1) {
                boolean expectCr = matched % 2 == 0;
                if (b == (expectCr ? '\r' : '\n')) {
                    matched++;
                } else {
                    matched = b == '\r' ? 1 : 0;
                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
            thread.interrupt();
        }
    }
}
