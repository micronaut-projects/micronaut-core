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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Seeded fuzzing of the functional routes under the conditions the other fuzz test leaves out:
 * request size limits, HTTP/2 with many streams on one connection, clients that stall, and the
 * concurrency limits of WebSocket routes. {@code FUZZ_SEED} and {@code FUZZ_ITERATIONS} as in
 * {@link HandlerRouteFuzzTest}.
 */
@Timeout(value = 280, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class HandlerRouteFuzzLimitsTest {

    private static final long LIMIT = 64 * 1024;
    private static final long SEED = Long.parseLong(setting("FUZZ_SEED", Long.toString(System.nanoTime())));
    private static final int ITERATIONS = Integer.parseInt(setting("FUZZ_ITERATIONS", "400"));

    private static String setting(String env, String defaultValue) {
        String value = System.getenv(env);
        return value == null ? defaultValue : value;
    }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> runAll(int count, java.util.function.IntFunction<Callable> scenario) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(12);
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    try {
                        scenario.apply(index).call();
                    } catch (Throwable e) {
                        failures.add("iteration " + index + " seed=" + SEED + ": " + e);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(200, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return new ArrayList<>(failures);
    }

    @FunctionalInterface
    interface Callable {
        void call() throws Exception;
    }

    private static void assertNoFailures(List<String> failures) {
        if (!failures.isEmpty()) {
            fail(failures.size() + " iterations failed (FUZZ_SEED=" + SEED + "):\n" + String.join("\n", failures.stream().limit(25).toList()));
        }
    }

    // ----------------------------------------------------------------------------------------
    // Request size limits
    // ----------------------------------------------------------------------------------------

    @Test
    void bodiesAroundTheRequestSizeLimit() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "FuzzLimits", "micronaut.server.port", -1,
            "micronaut.server.max-request-size", LIMIT))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            HttpClient client = HttpClient.newHttpClient();
            List<String> failures = runAll(ITERATIONS, index -> () -> {
                Random random = new Random(SEED * 31 + index);
                int size = switch (random.nextInt(5)) {
                    case 0 -> random.nextInt(100);
                    case 1 -> (int) LIMIT - random.nextInt(3);
                    case 2 -> (int) LIMIT + random.nextInt(3);
                    case 3 -> random.nextInt((int) LIMIT);
                    default -> (int) LIMIT + random.nextInt(300_000);
                };
                byte[] body = new byte[size];
                random.nextBytes(body);
                String[] paths = {"/lim/text", "/lim/bytes", "/lim/elements"};
                String path = paths[random.nextInt(paths.length)];
                boolean chunked = random.nextBoolean();
                boolean asJson = path.equals("/lim/elements");
                byte[] payload = asJson ? ("[" + "1,".repeat(Math.max(0, size / 2)) + "1]").getBytes(StandardCharsets.US_ASCII) : body;
                if (path.equals("/lim/text")) {
                    // valid UTF-8
                    payload = "a".repeat(Math.max(1, size)).getBytes(StandardCharsets.US_ASCII);
                }
                int length = payload.length;
                byte[] sent = payload;
                java.net.http.HttpRequest.BodyPublisher publisher = chunked
                    ? java.net.http.HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(sent))
                    : java.net.http.HttpRequest.BodyPublishers.ofByteArray(sent);
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path))
                    .header("content-type", asJson ? "application/json" : path.equals("/lim/text") ? "text/plain" : "application/octet-stream")
                    .timeout(Duration.ofSeconds(60))
                    .POST(publisher).build();
                try {
                    java.net.http.HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                    if (length <= LIMIT) {
                        assertEquals(200, response.statusCode(), () -> path + " " + length + " " + response.body());
                        assertEquals(asJson ? Integer.toString(Math.max(0, size / 2) + 1) : Integer.toString(length), response.body(), path);
                    } else {
                        assertEquals(413, response.statusCode(), () -> path + " " + length + " " + response.body());
                    }
                } catch (IOException e) {
                    // the server may close the connection while the oversized body is still being written
                    assertTrue(length > LIMIT, () -> "closed for a body within the limit: " + length + " " + e);
                }
            });
            // a limit on one read
            failures.addAll(runAll(Math.max(50, ITERATIONS / 4), index -> () -> {
                Random random = new Random(SEED * 17 + index);
                int size = random.nextInt(3000);
                byte[] body = "b".repeat(size).getBytes(StandardCharsets.US_ASCII);
                java.net.http.HttpResponse<String> response = client.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/lim/small"))
                    .header("content-type", "text/plain").POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body)).build(), BodyHandlers.ofString());
                if (size <= 1000 && size > 0) {
                    assertEquals(200, response.statusCode(), response::body);
                    assertEquals(Integer.toString(size), response.body());
                } else if (size > 1000) {
                    assertTrue(response.statusCode() >= 400 && response.statusCode() < 500, () -> "text(1000) of " + size + ": " + response.statusCode() + " " + response.body());
                }
            }));
            assertNoFailures(failures);
            // alive
            assertEquals(200, client.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/lim/ping")).build(), BodyHandlers.ofString()).statusCode());
        }
    }

    // ----------------------------------------------------------------------------------------
    // HTTP/2: many streams on one connection
    // ----------------------------------------------------------------------------------------

    @Test
    void manyConcurrentStreamsOfOneHttp2Connection() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "FuzzH2", "micronaut.server.port", -1,
            "micronaut.server.http-version", "2.0", "micronaut.server.ssl.enabled", false))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
            String base = "http://localhost:" + server.getPort();
            // the first request upgrades the connection
            assertEquals(200, client.send(java.net.http.HttpRequest.newBuilder(URI.create(base + "/h2/sse/1")).header("x-trace", "warm").build(), BodyHandlers.ofString()).statusCode());
            AtomicInteger http2 = new AtomicInteger();
            int total = Math.max(200, ITERATIONS);
            List<CompletableFuture<String>> all = new ArrayList<>();
            Random random = new Random(SEED);
            java.util.concurrent.Semaphore streams = new java.util.concurrent.Semaphore(30);
            for (int i = 0; i < total; i++) {
                streams.acquire();
                String trace = "h" + i;
                long seed = random.nextLong();
                int kind = random.nextInt(4);
                int size = random.nextInt(random.nextInt(4) == 0 ? 200_000 : 2000);
                java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder().header("x-trace", trace);
                CompletableFuture<String> result = switch (kind) {
                    case 0 -> {
                        byte[] body = "t".repeat(size + 1).getBytes(StandardCharsets.US_ASCII);
                        yield client.sendAsync(builder.uri(URI.create(base + "/h2/text")).header("content-type", "text/plain")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body)).build(), BodyHandlers.ofString()).thenApply(response -> {
                                http2.addAndGet(response.version() == HttpClient.Version.HTTP_2 ? 1 : 0);
                                return check(response, 200, (size + 1) + ":" + trace);
                            });
                    }
                    case 1 -> {
                        byte[] body = new byte[size + 1];
                        new Random(seed).nextBytes(body);
                        yield client.sendAsync(builder.uri(URI.create(base + "/h2/bytes")).header("content-type", "application/octet-stream")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body)).build(), BodyHandlers.ofString()).thenApply(response -> {
                                http2.addAndGet(response.version() == HttpClient.Version.HTTP_2 ? 1 : 0);
                                return check(response, 200, sha(body));
                            });
                    }
                    case 2 -> {
                        int events = random.nextInt(30);
                        StringBuilder expected = new StringBuilder();
                        for (int e = 0; e < events; e++) {
                            expected.append("data: e").append(e).append(':').append(trace).append('\n');
                        }
                        yield client.sendAsync(builder.uri(URI.create(base + "/h2/sse/" + events)).header("accept", "text/event-stream").build(),
                            BodyHandlers.ofString()).thenApply(response -> {
                                http2.addAndGet(response.version() == HttpClient.Version.HTTP_2 ? 1 : 0);
                                String data = response.body().lines().filter(line -> line.startsWith("data: ")).reduce("", (a, b) -> a + b + "\n");
                                return check(response, 200, expected.toString(), data);
                            });
                    }
                    default -> {
                        byte[] expected = new byte[size + 1];
                        new Random(seed).nextBytes(expected);
                        yield client.sendAsync(builder.uri(URI.create(base + "/h2/big/" + (size + 1) + "/" + seed)).build(), BodyHandlers.ofByteArray())
                            .thenApply(response -> {
                                http2.addAndGet(response.version() == HttpClient.Version.HTTP_2 ? 1 : 0);
                                return response.statusCode() == 200 && sha(expected).equals(sha(response.body())) ? null : "big " + response.statusCode();
                            });
                    }
                };
                all.add(result.orTimeout(120, TimeUnit.SECONDS).exceptionally(e -> trace + " failed: " + e).whenComplete((v, e) -> streams.release()));
            }
            List<String> failures = new ArrayList<>();
            for (CompletableFuture<String> future : all) {
                String failure = future.get(150, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            assertTrue(failures.isEmpty(), () -> "seed=" + SEED + ": " + failures.stream().limit(20).toList());
            // the connection was upgraded: the streams ran over HTTP/2
            assertTrue(http2.get() > total / 2, () -> "only " + http2.get() + " of " + total + " over HTTP/2");
        }
    }

    private static String check(java.net.http.HttpResponse<String> response, int status, String expected) {
        return check(response, status, expected, response.body());
    }

    private static String check(java.net.http.HttpResponse<String> response, int status, String expected, String actual) {
        if (response.statusCode() != status) {
            return "status " + response.statusCode() + " " + response.body();
        }
        return expected.equals(actual) ? null : "expected " + expected + " but was " + (actual.length() > 100 ? actual.substring(0, 100) : actual);
    }

    // ----------------------------------------------------------------------------------------
    // Clients that stall
    // ----------------------------------------------------------------------------------------

    @Test
    void stalledAndSlowClients() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "FuzzLimits", "micronaut.server.port", -1,
            "micronaut.server.idle-timeout", "1s"))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            List<String> failures = runAll(Math.max(40, ITERATIONS / 10), index -> () -> {
                Random random = new Random(SEED * 13 + index);
                try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                    socket.setSoTimeout(20_000);
                    OutputStream out = socket.getOutputStream();
                    int mode = random.nextInt(3);
                    if (mode == 0) {
                        // stalls inside the head
                        out.write("POST /lim/bytes HTTP/1.1\r\nHost: localhost\r\nContent-Le".getBytes(StandardCharsets.US_ASCII));
                    } else if (mode == 1) {
                        // stalls inside the body
                        out.write("POST /lim/bytes HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/octet-stream\r\nContent-Length: 1000\r\n\r\nabc".getBytes(StandardCharsets.US_ASCII));
                    } else {
                        // slow but alive: a byte every 200ms is more often than the idle timeout
                        byte[] body = "slow-body".getBytes(StandardCharsets.US_ASCII);
                        out.write(("POST /lim/bytes HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Type: application/octet-stream\r\nContent-Length: "
                            + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        for (byte b : body) {
                            out.write(b);
                            out.flush();
                            Thread.sleep(200);
                        }
                        String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                        assertTrue(response.startsWith("HTTP/1.1 200"), response);
                        assertTrue(response.endsWith("9"), response);
                        return;
                    }
                    out.flush();
                    // the idle connection is closed by the server (with or without a timeout answer)
                    long start = System.nanoTime();
                    InputStream in = socket.getInputStream();
                    byte[] buffer = new byte[4096];
                    String answer = "";
                    int n;
                    while ((n = in.read(buffer)) >= 0) {
                        answer += new String(buffer, 0, n, StandardCharsets.ISO_8859_1);
                    }
                    assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 15, "closed in time");
                    assertTrue(answer.isEmpty() || !answer.startsWith("HTTP/1.1 5") || answer.startsWith("HTTP/1.1 503"), answer);
                }
            });
            assertNoFailures(failures);
            java.net.http.HttpResponse<String> alive = HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/lim/ping")).build(), BodyHandlers.ofString());
            assertEquals(200, alive.statusCode());
        }
    }

    // ----------------------------------------------------------------------------------------
    // WebSocket concurrency limits
    // ----------------------------------------------------------------------------------------

    @Test
    void webSocketHandlersRunWithinTheirConcurrencyLimits() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "FuzzLimits", "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            HttpClient client = HttpClient.newHttpClient();
            List<String> failures = runAll(Math.max(30, ITERATIONS / 10), index -> () -> {
                Random random = new Random(SEED * 7 + index);
                String[] routes = {"serial", "four", "pending"};
                String route = routes[random.nextInt(routes.length)];
                int messages = 1 + random.nextInt(40);
                LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
                WebSocket ws = client.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + server.getPort() + "/lim/ws/" + route),
                    new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            received.add(data.toString());
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            webSocket.request(1);
                        }
                    }).get(20, TimeUnit.SECONDS);
                try {
                    for (int i = 0; i < messages; i++) {
                        ws.sendText("m" + i, true).get(20, TimeUnit.SECONDS);
                    }
                    List<String> replies = new ArrayList<>();
                    for (int i = 0; i < messages; i++) {
                        String reply = received.poll(30, TimeUnit.SECONDS);
                        assertTrue(reply != null, "reply " + i + " of " + messages + " from " + route);
                        replies.add(reply);
                    }
                    if (route.equals("four")) {
                        // up to four at a time: the replies come in any order, none is lost or repeated
                        assertEquals(messages, replies.stream().distinct().count());
                    } else {
                        // one at a time, or a bounded read-ahead: handled strictly in order
                        for (int i = 0; i < messages; i++) {
                            assertEquals("echo m" + i, replies.get(i), route);
                        }
                    }
                } finally {
                    ws.sendClose(1000, "done").orTimeout(10, TimeUnit.SECONDS).exceptionally(e -> null);
                }
            });
            assertNoFailures(failures);
            Probe probe = ctx.getBean(Probe.class);
            assertEquals(1, probe.peakSerial.get(), "a serial handler never ran twice at once");
            assertTrue(probe.peakFour.get() <= 4, () -> "peak " + probe.peakFour.get());
        }
    }

    // ----------------------------------------------------------------------------------------
    // The application
    // ----------------------------------------------------------------------------------------

    record Trace(String id) implements PropagatedContextElement {
    }

    static String trace() {
        return PropagatedContext.getOrEmpty().find(Trace.class).map(Trace::id).orElse("none");
    }

    @Singleton
    @Requires(property = "spec.name", value = "FuzzLimits")
    static class Probe {
        final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> serial = new java.util.concurrent.ConcurrentHashMap<>();
        final AtomicInteger peakSerial = new AtomicInteger();
        final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> four = new java.util.concurrent.ConcurrentHashMap<>();
        final AtomicInteger peakFour = new AtomicInteger();
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

        CompletionStage<Void> track(java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> counters, String session, AtomicInteger peak, int millis) {
            AtomicInteger current = counters.computeIfAbsent(session, k -> new AtomicInteger());
            peak.accumulateAndGet(current.incrementAndGet(), Math::max);
            CompletableFuture<Void> done = new CompletableFuture<>();
            scheduler.schedule(() -> {
                current.decrementAndGet();
                done.complete(null);
            }, millis, TimeUnit.MILLISECONDS);
            return done;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "FuzzLimits")
    static class LimitRoutes implements HttpRoutes {
        private final Probe probe;

        LimitRoutes(Probe probe) {
            this.probe = probe;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/lim/ping", (request, pv) -> HttpResponse.ok("pong"));
            routes.POST("/lim/text").consumesAll().body(String.class).handle((request, pv, body) -> HttpResponse.ok(Integer.toString(body.length())).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.POST("/lim/bytes").consumesAll().body().handleAsync((request, pv, body) ->
                body.bytes(10_000_000).thenApply(bytes -> HttpResponse.ok(Integer.toString(bytes.length)).contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.POST("/lim/small").consumesAll().body().handleAsync((request, pv, body) ->
                body.text(1000).thenApply(text -> HttpResponse.ok(Integer.toString(text.length())).contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.POST("/lim/elements").body().handleAsync((request, pv, body) -> {
                int[] count = new int[1];
                return body.elements(Integer.class).forEach(element -> {
                    count[0]++;
                    return CompletableFuture.completedFuture(null);
                }).thenApply(done -> HttpResponse.ok(Integer.toString(count[0])).contentType(MediaType.TEXT_PLAIN_TYPE));
            });
            routes.GET("/lim/ws/serial").webSocket(ws -> ws.maxConcurrentMessages(1)
                .onMessage(String.class, (session, message) -> probe.track(probe.serial, session.getId(), probe.peakSerial, 1 + message.length() % 3)
                    .thenCompose(done -> session.sendAsync("echo " + message))));
            routes.GET("/lim/ws/four").webSocket(ws -> ws.maxConcurrentMessages(4)
                .onMessage(String.class, (session, message) -> probe.track(probe.four, session.getId(), probe.peakFour, 1 + message.length() % 5)
                    .thenCompose(done -> session.sendAsync("echo " + message))));
            routes.GET("/lim/ws/pending").webSocket(ws -> ws.maxPendingMessages(2)
                .onMessage(String.class, (session, message) -> probe.track(new java.util.concurrent.ConcurrentHashMap<>(), session.getId(), new AtomicInteger(), 2)
                    .thenCompose(done -> session.sendAsync("echo " + message))));
        }
    }

    @ServerFilter("/h2/**")
    @Requires(property = "spec.name", value = "FuzzH2")
    static class H2Filter {
        @RequestFilter
        void filter(HttpRequest<?> request, MutablePropagatedContext context) {
            context.add(new Trace(String.valueOf(request.getHeaders().get("X-Trace"))));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "FuzzH2")
    static class H2Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/h2/text").consumesAll().body(String.class).handle((request, pv, body) -> HttpResponse.ok(body.length() + ":" + trace()).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.POST("/h2/bytes").consumesAll().body().handleAsync((request, pv, body) -> {
                Supplier<String> trace = HandlerRouteFuzzLimitsTest::trace;
                String before = trace.get();
                return body.bytes(10_000_000).thenApply(bytes -> HttpResponse.ok(sha(bytes)).contentType(MediaType.TEXT_PLAIN_TYPE)
                    .header("X-Trace-Before", before));
            });
            routes.GET("/h2/sse/{n}").sse((request, pv, sse) -> {
                int n = pv.getInt("n");
                String trace = trace();
                for (int i = 0; i < n; i++) {
                    sse.send("e" + i + ":" + trace);
                }
                sse.complete();
            });
            routes.GET("/h2/big/{size}/{seed}").produces(MediaType.APPLICATION_OCTET_STREAM_TYPE).handle((request, pv) -> {
                byte[] bytes = new byte[pv.getInt("size")];
                new Random(pv.getLong("seed")).nextBytes(bytes);
                return HttpResponse.ok(bytes);
            });
        }
    }
}
