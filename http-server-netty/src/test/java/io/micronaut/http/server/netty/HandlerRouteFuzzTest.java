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
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.sse.Event;
import io.micronaut.json.JsonMapper;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.websocket.WebSocketSession;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A seeded, randomized test of the functional routes, over the wire and concurrently: request and
 * response bodies (text, bytes, JSON, urlencoded and multipart forms, in many fragments, with
 * either framing, read sync or async), server-sent events, WebSocket routes, error routes, route
 * and server filters (sync and async, on other threads) and the propagated context.
 *
 * <p>Every request carries its own trace id, which a server filter puts in the propagated context
 * and the MDC and the route filters extend. The oracles are the replies, computed from what was
 * sent: the payload round-trips, the handlers, the filters and the continuations see the context of
 * their own request and never the one of a concurrent request, and a malformed request is answered
 * with a client error, never a server error or a hang, while the server keeps serving.</p>
 *
 * <p>Reproduce a failure with {@code FUZZ_SEED=<seed>} (printed on failure), more runs with
 * {@code FUZZ_ITERATIONS=<n>} (or the system properties {@code fuzz.seed} and {@code fuzz.iterations}).</p>
 */
@Timeout(value = 280, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class HandlerRouteFuzzTest {

    private static final String SPEC = "HandlerRouteFuzzTest";
    private static final int ITERATIONS = Integer.parseInt(setting("fuzz.iterations", "FUZZ_ITERATIONS", "400"));
    private static final long SEED = Long.parseLong(setting("fuzz.seed", "FUZZ_SEED", Long.toString(System.nanoTime())));
    private static final String ALPHABET = "abcXYZ019 _-.~!*'();:@&=+$,/?#[]%éü€中😀\"\\\n\t<>{}|^`";

    private static String setting(String property, String env, String defaultValue) {
        String value = System.getProperty(property, System.getenv(env));
        return value == null ? defaultValue : value;
    }

    private static ApplicationContext context;
    private static EmbeddedServer server;
    private static JsonMapper json;
    private static HttpClient wsClient;
    private static final AtomicInteger TRACES = new AtomicInteger();

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of("spec.name", SPEC, "micronaut.server.port", -1));
        server = context.getBean(EmbeddedServer.class).start();
        json = context.getBean(JsonMapper.class);
        wsClient = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        wsClient.shutdownNow();
        context.close();
    }

    @Test
    void randomRequestsOverTheWire() throws Exception {
        System.out.println("HandlerRouteFuzzTest seed=" + SEED + " iterations=" + ITERATIONS);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < ITERATIONS; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    Random random = new Random(SEED * 31 + index);
                    String trace = "t" + TRACES.incrementAndGet() + "x" + Integer.toHexString(random.nextInt());
                    Case c = null;
                    try {
                        c = generate(random, trace);
                        run(random, c);
                    } catch (Throwable e) {
                        failures.add("iteration " + index + " [" + (c == null ? "?" : c.name) + "] seed=" + SEED + ": " + e);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(240, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        if (!failures.isEmpty()) {
            fail(failures.size() + " of " + ITERATIONS + " iterations failed (FUZZ_SEED=" + SEED + "):\n"
                + String.join("\n", failures.stream().limit(25).toList()));
        }
        // the server survived the aborted and malformed requests
        Resp health = send(new Req("GET", "/fz/path/1/2", Map.of("x-trace", "health"), new byte[0], false, 0, 0, false));
        assertEquals(200, health.status, health.text());
        assertEquals(0, Events.OPEN_SSE.get(), "every event stream was closed");
    }

    @Test
    void webSocketRoutesKeepTheContextOfTheirOwnUpgrade() throws Exception {
        Random random = new Random(SEED);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<String>> futures = new ArrayList<>();
        int connections = Math.max(8, ITERATIONS / 10);
        for (int i = 0; i < connections; i++) {
            long seed = random.nextLong();
            futures.add(pool.submit(() -> webSocketScenario(new Random(seed))));
        }
        List<String> failures = new ArrayList<>();
        for (Future<String> future : futures) {
            try {
                String failure = future.get(120, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            } catch (Exception e) {
                failures.add(String.valueOf(e));
            }
        }
        pool.shutdownNow();
        assertTrue(failures.isEmpty(), "seed=" + SEED + ": " + failures);
    }

    // ----------------------------------------------------------------------------------------
    // WebSocket
    // ----------------------------------------------------------------------------------------

    private static String webSocketScenario(Random random) throws Exception {
        String trace = "w" + TRACES.incrementAndGet();
        String room = "room" + random.nextInt(1000);
        boolean binaryMode = random.nextBoolean();
        LinkedBlockingQueue<Object> received = new LinkedBlockingQueue<>();
        WebSocket ws = wsClient.newWebSocketBuilder()
            .header("X-Trace", trace)
            .buildAsync(URI.create("ws://localhost:" + server.getPort() + (binaryMode ? "/fz/wsb/" : "/fz/ws/") + room + "?t=" + trace), new WebSocket.Listener() {
                private final StringBuilder text = new StringBuilder();
                private final ByteArrayOutputStream binary = new ByteArrayOutputStream();

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    text.append(data);
                    if (last) {
                        received.add(text.toString());
                        text.setLength(0);
                    }
                    webSocket.request(1);
                    return null;
                }

                @Override
                public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                    byte[] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    binary.write(bytes, 0, bytes.length);
                    if (last) {
                        received.add(binary.toByteArray());
                        binary.reset();
                    }
                    webSocket.request(1);
                    return null;
                }
            }).get(20, TimeUnit.SECONDS);
        try {
            Object open = received.poll(20, TimeUnit.SECONDS);
            String expectedOpen = "open " + room + " trace=" + trace;
            if (!(open instanceof String s) || !s.startsWith(expectedOpen)) {
                return "open message " + open + " expected " + expectedOpen;
            }
            int messages = 1 + random.nextInt(12);
            for (int i = 0; i < messages; i++) {
                if (binaryMode) {
                    byte[] bytes = new byte[random.nextInt(2000)];
                    random.nextBytes(bytes);
                    send(ws, bytes, random);
                    byte[] reversed = new byte[bytes.length];
                    for (int j = 0; j < bytes.length; j++) {
                        reversed[j] = bytes[bytes.length - 1 - j];
                    }
                    Object reply = received.poll(20, TimeUnit.SECONDS);
                    if (!(reply instanceof byte[] b) || !java.util.Arrays.equals(reversed, b)) {
                        return "binary reply of " + trace + " was " + describeObject(reply);
                    }
                } else {
                    String text = randomText(random, random.nextInt(3000));
                    send(ws, text, random);
                    Object reply = received.poll(20, TimeUnit.SECONDS);
                    String expected = "echo " + room + " trace=" + trace + " " + text;
                    if (!expected.equals(reply)) {
                        return "text reply of " + trace + " was " + describeObject(reply) + " expected " + abbreviate(expected);
                    }
                }
            }
            return null;
        } finally {
            ws.sendClose(1000, "done").orTimeout(10, TimeUnit.SECONDS).exceptionally(e -> null);
        }
    }

    private static void send(WebSocket ws, String text, Random random) throws Exception {
        if (random.nextBoolean() && text.length() > 1) {
            // a message in fragments is one message; do not split a surrogate pair
            int at = 1 + random.nextInt(text.length() - 1);
            if (Character.isHighSurrogate(text.charAt(at - 1))) {
                at++;
            }
            if (at < text.length()) {
                ws.sendText(text.substring(0, at), false).get(20, TimeUnit.SECONDS);
                ws.sendText(text.substring(at), true).get(20, TimeUnit.SECONDS);
                return;
            }
        }
        ws.sendText(text, true).get(20, TimeUnit.SECONDS);
    }

    private static void send(WebSocket ws, byte[] bytes, Random random) throws Exception {
        if (random.nextBoolean() && bytes.length > 1) {
            int at = 1 + random.nextInt(bytes.length - 1);
            ws.sendBinary(ByteBuffer.wrap(bytes, 0, at), false).get(20, TimeUnit.SECONDS);
            ws.sendBinary(ByteBuffer.wrap(bytes, at, bytes.length - at), true).get(20, TimeUnit.SECONDS);
            return;
        }
        ws.sendBinary(ByteBuffer.wrap(bytes), true).get(20, TimeUnit.SECONDS);
    }

    private static String describeObject(Object o) {
        return o instanceof byte[] b ? "bytes[" + b.length + "]" : abbreviate(String.valueOf(o));
    }

    private static String abbreviate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "...(" + s.length() + ")" : s;
    }

    // ----------------------------------------------------------------------------------------
    // The generator of the cases
    // ----------------------------------------------------------------------------------------

    private record Req(String method, String path, Map<String, String> headers, byte[] body, boolean chunked,
                       int fragment, int pauseMillis, boolean abort) {
    }

    private record Resp(int status, Map<String, String> headers, byte[] body, boolean closedWithoutResponse) {
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private record Case(String name, Req request, Consumer<Resp> check) {
    }

    private static Case generate(Random r, String trace) {
        return switch (r.nextInt(17)) {
            case 0 -> textCase(r, trace, "/fz/text", "sync text");
            case 1 -> textCase(r, trace, "/fz/blocking/text", "sync text on the blocking executor");
            case 2 -> asyncTextCase(r, trace);
            case 3 -> bytesCase(r, trace);
            case 4 -> jsonCase(r, trace);
            case 5 -> urlencodedFormCase(r, trace, r.nextBoolean() ? "/fz/form" : "/fz/aform");
            case 6 -> multipartCase(r, trace);
            case 7 -> sseCase(r, trace);
            case 8 -> sseBodyCase(r, trace);
            case 9 -> pathCase(r, trace);
            case 10 -> errorCase(r, trace);
            case 11 -> blockedByFilterCase(r, trace);
            case 12 -> abortedCase(r, trace);
            case 13 -> malformedCase(r, trace);
            case 14 -> asyncFilterCase(r, trace);
            case 15 -> lateReplyCase(r, trace);
            default -> textCase(r, trace, "/fz/group/text", "group text with group filters");
        };
    }

    private static Map<String, String> headers(String trace, String... more) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-trace", trace);
        for (int i = 0; i < more.length; i += 2) {
            headers.put(more[i], more[i + 1]);
        }
        return headers;
    }

    private static Req post(Random r, String path, Map<String, String> headers, byte[] body) {
        boolean chunked = r.nextInt(3) == 0;
        int fragment = switch (r.nextInt(4)) {
            case 0 -> 1 + r.nextInt(8);
            case 1 -> 1 + r.nextInt(300);
            case 2 -> 1 + r.nextInt(5000);
            default -> Integer.MAX_VALUE;
        };
        // a long pause between the fragments of a large body is slow: pause rarely
        int pause = fragment < 100 && body.length / fragment > 40 ? 0 : r.nextInt(4) == 0 ? r.nextInt(3) : 0;
        return new Req("POST", path, headers, body, chunked, fragment, pause, false);
    }

    private static void common(Resp resp, String trace, int status) {
        assertEquals(status, resp.status, () -> "status, body: " + abbreviate(resp.text()));
        // the filters and handlers saw the context of this request
        assertContext(resp.headers.get("x-handler-ctx"), trace, "handler");
    }

    private static void assertContext(String ctx, String trace, String where) {
        assertTrue(ctx != null && ctx.contains("trace=" + trace + ",") && ctx.contains("route=r-" + trace + ",")
            && ctx.contains("mdc=" + trace + ","), () -> where + " context of " + trace + " was " + ctx);
    }

    private static String sha(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomText(Random r, int length) {
        StringBuilder sb = new StringBuilder();
        int[] points = ALPHABET.codePoints().toArray();
        while (sb.length() < length) {
            sb.appendCodePoint(points[r.nextInt(points.length)]);
        }
        return sb.toString();
    }

    private static int randomLength(Random r) {
        return switch (r.nextInt(6)) {
            case 0 -> 0;
            case 1 -> r.nextInt(16);
            case 2 -> r.nextInt(1_000);
            case 3 -> r.nextInt(70_000);
            case 4 -> 1024 * 64 + r.nextInt(3) - 1;
            default -> r.nextInt(300_000);
        };
    }

    private static Case textCase(Random r, String trace, String path, String name) {
        String text = randomText(r, Math.max(1, randomLength(r)));
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        boolean group = path.startsWith("/fz/group");
        return new Case(name, post(r, path, headers(trace, "content-type", "text/plain; charset=UTF-8"), body), resp -> {
            common(resp, trace, 200);
            assertEquals(body.length + ":" + sha(text.getBytes(StandardCharsets.UTF_8)), resp.text());
            assertContext(resp.headers.get("x-after-ctx"), trace, "response filter");
            if (group) {
                assertEquals("g2,g1", resp.headers.get("x-order"));
            }
        });
    }

    private static Case asyncTextCase(Random r, String trace) {
        String text = randomText(r, randomLength(r));
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        return new Case("async text", post(r, "/fz/atext", headers(trace, "content-type", "text/plain"), body), resp -> {
            assertEquals(200, resp.status, resp.text());
            assertEquals(body.length + ":" + sha(body), resp.text());
            // the handler runs with the context, the continuation of the body never with another one
            assertContext(resp.headers.get("x-handler-ctx"), trace, "handler");
            assertNoForeignTrace(resp.headers.get("x-continuation-ctx"), trace);
            assertContext(resp.headers.get("x-after-ctx"), trace, "async response filter");
        });
    }

    private static void assertNoForeignTrace(String ctx, String trace) {
        assertTrue(ctx != null && (ctx.contains("trace=none,") || ctx.contains("trace=" + trace + ",")),
            () -> "continuation of " + trace + " saw " + ctx);
    }

    private static Case bytesCase(Random r, String trace) {
        byte[] body = new byte[randomLength(r)];
        r.nextBytes(body);
        return new Case("bytes", post(r, "/fz/bytes", headers(trace, "content-type", "application/octet-stream"), body), resp -> {
            assertEquals(200, resp.status, resp.text());
            assertEquals(body.length + ":" + sha(body), resp.text());
            assertNoForeignTrace(resp.headers.get("x-continuation-ctx"), trace);
        });
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else if (r(c)) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static boolean r(char c) {
        return c > 0x7e;
    }

    private static Case jsonCase(Random r, String trace) {
        String name = randomText(r, r.nextInt(200));
        int count = r.nextInt(200);
        List<Integer> numbers = new ArrayList<>();
        long sum = 0;
        for (int i = 0; i < count; i++) {
            int n = r.nextInt(2000) - 1000;
            numbers.add(n);
            sum += n;
        }
        long expectedSum = sum;
        String body = "{\"name\":" + jsonString(name) + ",\"numbers\":" + numbers + ",\"unknown\":{\"a\":[1,2,{\"b\":null}]}}";
        return new Case("json", post(r, "/fz/json", headers(trace, "content-type", "application/json"), body.getBytes(StandardCharsets.UTF_8)), resp -> {
            common(resp, trace, 200);
            try {
                Summary summary = json.readValue(resp.body, Summary.class);
                // the default serialization omits an empty string
                assertEquals(name.isEmpty() ? null : name, summary.name);
                assertEquals(expectedSum, summary.sum);
                assertEquals(count, summary.count);
            } catch (IOException e) {
                throw new AssertionError(resp.text(), e);
            }
        });
    }

    private static Case urlencodedFormCase(Random r, String trace, String path) {
        Map<String, List<String>> fields = new TreeMap<>();
        int n = r.nextInt(12);
        for (int i = 0; i < n; i++) {
            String name = "f" + r.nextInt(6) + (r.nextInt(5) == 0 ? randomText(r, 3) : "");
            List<String> values = fields.computeIfAbsent(name, k -> new ArrayList<>());
            values.add(randomText(r, r.nextInt(r.nextInt(8) == 0 ? 50_000 : 40)));
        }
        StringBuilder body = new StringBuilder();
        List<Map.Entry<String, String>> pairs = new ArrayList<>();
        fields.forEach((k, vs) -> vs.forEach(v -> pairs.add(Map.entry(k, v))));
        java.util.Collections.shuffle(pairs, r);
        // shuffled pairs keep the relative order of the values of one name only if we rebuild the expectation
        Map<String, List<String>> expected = new TreeMap<>();
        for (Map.Entry<String, String> pair : pairs) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(pair.getKey(), StandardCharsets.UTF_8)).append('=')
                .append(URLEncoder.encode(pair.getValue(), StandardCharsets.UTF_8));
            expected.computeIfAbsent(pair.getKey(), k -> new ArrayList<>()).add(pair.getValue());
        }
        return new Case("urlencoded form " + path, post(r, path, headers(trace, "content-type", "application/x-www-form-urlencoded"),
            body.toString().getBytes(StandardCharsets.UTF_8)), resp -> {
            assertEquals(200, resp.status, () -> abbreviate(resp.text()));
            assertEquals(render(expected), resp.text());
            assertContext(resp.headers.get("x-handler-ctx"), trace, "handler");
        });
    }

    private static String render(Map<String, List<String>> fields) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(fields).forEach((k, v) -> sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=')
            .append(v.stream().map(x -> sha(x.getBytes(StandardCharsets.UTF_8)).substring(0, 12) + "/" + x.length()).toList()).append('\n'));
        return sb.toString();
    }

    private static Case multipartCase(Random r, String trace) {
        String boundary = "----fuzz" + Integer.toHexString(r.nextInt()) + "B";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, List<String>> fields = new TreeMap<>();
        Map<String, String> files = new TreeMap<>();
        int parts = r.nextInt(8);
        for (int i = 0; i < parts; i++) {
            try {
                if (r.nextBoolean()) {
                    String name = "field" + r.nextInt(4);
                    String value = randomText(r, r.nextInt(2000)).replace("\r", "");
                    out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(value.getBytes(StandardCharsets.UTF_8));
                    out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    fields.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
                } else {
                    String name = "file" + i;
                    byte[] content = new byte[r.nextInt(r.nextInt(6) == 0 ? 200_000 : 3000)];
                    r.nextBytes(content);
                    out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + name + ".bin\"\r\n"
                        + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(content);
                    out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    files.put(name, content.length + ":" + sha(content));
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return new Case("multipart", post(r, "/fz/multipart", headers(trace, "content-type", "multipart/form-data; boundary=" + boundary), out.toByteArray()), resp -> {
            assertEquals(200, resp.status, () -> abbreviate(resp.text()));
            assertEquals(render(fields) + files, resp.text());
            assertNoForeignTrace(resp.headers.get("x-continuation-ctx"), trace);
        });
    }

    private static Case sseCase(Random r, String trace) {
        int count = r.nextInt(40);
        int delay = r.nextInt(4) == 0 ? 1 + r.nextInt(4) : 0;
        String lastId = r.nextBoolean() ? Integer.toString(r.nextInt(100)) : null;
        Map<String, String> headers = headers(trace, "accept", "text/event-stream");
        if (lastId != null) {
            headers.put("last-event-id", lastId);
        }
        Req request = new Req("GET", "/fz/sse/" + count + "?delay=" + delay + "&size=" + r.nextInt(3000), headers, new byte[0], false, Integer.MAX_VALUE, 0, false);
        int size = Integer.parseInt(request.path.substring(request.path.lastIndexOf('=') + 1));
        return new Case("sse", request, resp -> {
            assertEquals(200, resp.status, () -> abbreviate(resp.text()));
            assertTrue(Objects.toString(resp.headers.get("content-type")).startsWith("text/event-stream"), resp.headers::toString);
            String text = resp.text().replace("\r\n", "\n");
            List<String> data = new ArrayList<>();
            for (String line : text.split("\n")) {
                if (line.startsWith("data: ")) {
                    data.add(line.substring(6));
                }
            }
            assertEquals(count, data.size(), () -> "events: " + abbreviate(text));
            for (int i = 0; i < count; i++) {
                assertEquals("e" + i + ":" + trace + ":" + (lastId == null ? "-" : lastId) + ":" + "p".repeat(size), data.get(i));
            }
            assertTrue(text.indexOf("id: ") < 0 || text.indexOf("id: 0") >= 0 || count == 0, text);
        });
    }

    private static Case sseBodyCase(Random r, String trace) {
        String text = randomText(r, r.nextInt(5000)).replace("\n", " ").replace("\r", " ");
        int parts = 1 + r.nextInt(10);
        return new Case("sse with body", post(r, "/fz/sse-body?parts=" + parts, headers(trace, "content-type", "text/plain", "accept", "text/event-stream"),
            text.getBytes(StandardCharsets.UTF_8)), resp -> {
            assertEquals(200, resp.status, () -> abbreviate(resp.text()));
            StringBuilder joined = new StringBuilder();
            int events = 0;
            for (String line : resp.text().replace("\r\n", "\n").split("\n")) {
                if (line.startsWith("data: ")) {
                    String payload = line.substring(6);
                    int colon = payload.indexOf(':');
                    assertEquals(trace, payload.substring(0, colon), "the event handler's context");
                    joined.append(payload.substring(colon + 1));
                    events++;
                }
            }
            assertEquals(parts, events);
            assertEquals(text, joined.toString());
        });
    }

    private static Case pathCase(Random r, String trace) {
        String a = randomText(r, 1 + r.nextInt(20)).replace("%", "").replace("/", "").replace("?", "").replace("#", "").replace("\\", "") + "z";
        boolean number = r.nextInt(5) != 0;
        String b = number ? Integer.toString(r.nextInt(100000)) : "x" + r.nextInt(5);
        String encoded = URLEncoder.encode(a, StandardCharsets.UTF_8).replace("+", "%20");
        return new Case("path variables", new Req("GET", "/fz/path/" + encoded + "/" + b, headers(trace), new byte[0], false, Integer.MAX_VALUE, 0, false), resp -> {
            if (number) {
                assertEquals(200, resp.status, resp::text);
                assertEquals(a + "|" + b, resp.text());
            } else {
                // the constraint of the second variable does not match
                assertEquals(404, resp.status, resp::text);
            }
        });
    }

    private static Case errorCase(Random r, String trace) {
        String[] kinds = {"throw", "stage", "status", "nested"};
        String kind = kinds[r.nextInt(kinds.length)];
        return new Case("error " + kind, new Req("GET", "/fz/err/" + kind, headers(trace), new byte[0], false, Integer.MAX_VALUE, 0, false), resp -> {
            assertEquals(switch (kind) {
                case "status" -> 409;
                default -> 418;
            }, resp.status, resp::text);
            // the error route runs with the context of the request, and so does its response filter
            if (!kind.equals("status")) {
                assertContext(resp.headers.get("x-error-ctx"), trace, "error route");
                assertTrue(resp.text().startsWith(kind), resp::text);
            }
        });
    }

    private static Case blockedByFilterCase(Random r, String trace) {
        String body = randomText(r, r.nextInt(30_000));
        return new Case("request filter answers", post(r, "/fz/group/text", headers(trace, "x-block", "yes", "content-type", "text/plain"),
            body.getBytes(StandardCharsets.UTF_8)), resp -> {
            assertEquals(403, resp.status, resp::text);
            // the response filters still ran, with the context
            String after = resp.headers.get("x-after-ctx");
            assertTrue(after != null && after.contains("trace=" + trace + ",") && after.contains("mdc=" + trace + ","), () -> "blocked " + after);
            assertEquals("g1", resp.headers.get("x-order"));
        });
    }

    private static Case asyncFilterCase(Random r, String trace) {
        byte[] body = new byte[r.nextInt(5000)];
        r.nextBytes(body);
        return new Case("async filters", post(r, "/fz/afilters", headers(trace, "content-type", "application/octet-stream"), body), resp -> {
            assertEquals(200, resp.status, resp::text);
            // the async filters added their element after a thread hop: the handler and the response filters see it
            assertEquals("a1,a2,handler,r1,r2", resp.headers.get("x-order"));
            assertContext(resp.headers.get("x-handler-ctx"), trace, "handler");
            assertContext(resp.headers.get("x-after-ctx"), trace, "async response filter");
            assertTrue(resp.headers.get("x-handler-ctx").contains("late=l-" + trace), resp.headers::toString);
            assertEquals(body.length + ":" + sha(body), resp.text());
        });
    }

    private static Case lateReplyCase(Random r, String trace) {
        // the handler replies from another thread long after it returned
        int ms = r.nextInt(8);
        return new Case("late reply", new Req("GET", "/fz/late?ms=" + ms, headers(trace), new byte[0], false, Integer.MAX_VALUE, 0, false), resp -> {
            assertEquals(200, resp.status, resp::text);
            assertEquals("late " + ms, resp.text());
            assertContext(resp.headers.get("x-after-ctx"), trace, "response filter");
        });
    }

    private static Case abortedCase(Random r, String trace) {
        byte[] body = new byte[1000 + r.nextInt(100_000)];
        r.nextBytes(body);
        String[] paths = {"/fz/text", "/fz/atext", "/fz/bytes", "/fz/json", "/fz/form", "/fz/aform", "/fz/multipart", "/fz/sse-body", "/fz/afilters"};
        String path = paths[r.nextInt(paths.length)];
        String type = path.contains("form") ? "application/x-www-form-urlencoded" : path.equals("/fz/multipart") ? "multipart/form-data; boundary=zz" : "application/octet-stream";
        return new Case("client disconnects mid-body " + path, new Req("POST", path, headers(trace, "content-type", type), body, r.nextBoolean(),
            1 + r.nextInt(2000), 0, true), resp -> {
            // nothing to check: the client left; the server must go on, see the final health check
        });
    }

    private static Case malformedCase(Random r, String trace) {
        String[] paths = {"/fz/json", "/fz/form", "/fz/aform", "/fz/multipart", "/fz/text", "/fz/bytes", "/fz/sse-body"};
        String[] types = {"application/json", "application/x-www-form-urlencoded", "multipart/form-data; boundary=zz", "multipart/form-data", "application/xml", "", "application/json;;;"};
        String path = paths[r.nextInt(paths.length)];
        byte[] body = new byte[r.nextInt(5000)];
        if (r.nextBoolean()) {
            r.nextBytes(body);
        } else {
            byte[] fragment = "{\"name\":[[\"x\"".getBytes(StandardCharsets.UTF_8);
            body = java.util.Arrays.copyOf(fragment, Math.min(fragment.length, body.length));
        }
        Map<String, String> headers = headers(trace);
        String type = types[r.nextInt(types.length)];
        if (!type.isEmpty()) {
            headers.put("content-type", type);
        }
        byte[] finalBody = body;
        return new Case("malformed " + path + " as " + type, post(r, path, headers, finalBody), resp -> {
            if (resp.closedWithoutResponse) {
                return;
            }
            // a client error or a response, never a server error
            assertTrue(resp.status < 500 || resp.status == 418, () -> "status " + resp.status + " " + abbreviate(resp.text()));
        });
    }

    // ----------------------------------------------------------------------------------------
    // The client
    // ----------------------------------------------------------------------------------------

    private static void run(Random random, Case c) throws Exception {
        Resp resp = send(c.request);
        if (c.request.abort) {
            return;
        }
        assertTrue(!resp.closedWithoutResponse || c.name.startsWith("malformed"), "the server closed the connection without a response");
        c.check.accept(resp);
    }

    private static Resp send(Req req) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(60_000);
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();
            StringBuilder head = new StringBuilder(req.method).append(' ').append(req.path).append(" HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n");
            req.headers.forEach((k, v) -> head.append(k).append(": ").append(v).append("\r\n"));
            boolean hasBody = req.body.length > 0 || req.method.equals("POST");
            if (hasBody) {
                head.append(req.chunked ? "Transfer-Encoding: chunked\r\n" : "Content-Length: " + req.body.length + "\r\n");
            }
            head.append("\r\n");
            try {
                out.write(head.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                int limit = req.abort ? req.body.length / 2 : req.body.length;
                for (int at = 0; at < limit; at += req.fragment) {
                    int n = Math.min(req.fragment, limit - at);
                    if (req.chunked) {
                        out.write((Integer.toHexString(n) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                        out.write(req.body, at, n);
                        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    } else {
                        out.write(req.body, at, n);
                    }
                    out.flush();
                    if (req.pauseMillis > 0) {
                        Thread.sleep(req.pauseMillis);
                    }
                }
                if (req.abort) {
                    socket.setSoLinger(true, 0);
                    return new Resp(0, Map.of(), new byte[0], true);
                }
                if (req.chunked) {
                    out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                }
            } catch (IOException e) {
                // the server answered and closed before the whole body was sent, e.g. a limit: read the answer
            }
            return parse(socket.getInputStream());
        }
    }

    private static Resp parse(InputStream in) throws IOException {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        try {
            in.transferTo(all);
        } catch (IOException e) {
            if (all.size() == 0) {
                return new Resp(0, Map.of(), new byte[0], true);
            }
        }
        byte[] bytes = all.toByteArray();
        int end = indexOf(bytes, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
        if (end < 0) {
            return new Resp(0, Map.of(), new byte[0], true);
        }
        String[] lines = new String(bytes, 0, end, StandardCharsets.ISO_8859_1).split("\r\n");
        int status = Integer.parseInt(lines[0].split(" ")[1]);
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            headers.merge(lines[i].substring(0, colon).toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim(), (a, b) -> a + "," + b);
        }
        int bodyStart = end + 4;
        byte[] body;
        if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            int at = bodyStart;
            while (at < bytes.length) {
                int eol = indexOf(bytes, "\r\n".getBytes(StandardCharsets.US_ASCII), at);
                if (eol < 0) {
                    break;
                }
                int size = Integer.parseInt(new String(bytes, at, eol - at, StandardCharsets.US_ASCII).split(";")[0].trim(), 16);
                if (size == 0) {
                    break;
                }
                decoded.write(bytes, eol + 2, Math.min(size, bytes.length - eol - 2));
                at = eol + 2 + size + 2;
            }
            body = decoded.toByteArray();
        } else {
            body = java.util.Arrays.copyOfRange(bytes, bodyStart, bytes.length);
        }
        return new Resp(status, headers, body, false);
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    // ----------------------------------------------------------------------------------------
    // The application
    // ----------------------------------------------------------------------------------------

    record Trace(String id) implements PropagatedContextElement {
    }

    record RouteTrace(String id) implements PropagatedContextElement {
    }

    record LateTrace(String id) implements PropagatedContextElement {
    }

    record Summary(String name, long sum, int count) {
    }

    record Item(String name, List<Integer> numbers) {
    }

    static String describe() {
        PropagatedContext context = PropagatedContext.getOrEmpty();
        return "trace=" + context.find(Trace.class).map(Trace::id).orElse("none")
            + ",route=" + context.find(RouteTrace.class).map(t -> t.id()).orElse("none")
            + ",mdc=" + Objects.requireNonNullElse(MDC.get("trace"), "none")
            + ",late=" + context.find(LateTrace.class).map(LateTrace::id).orElse("none")
            + ",request=" + ServerRequestContext.currentRequest().map(HttpRequest::getPath).orElse("none");
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Events {
        static final AtomicInteger OPEN_SSE = new AtomicInteger();
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

        @PreDestroy
        void stop() {
            scheduler.shutdownNow();
        }
    }

    @ServerFilter("/fz/**")
    @Requires(property = "spec.name", value = SPEC)
    static class TraceFilter {
        @RequestFilter
        void filter(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            String trace = request.getHeaders().get("X-Trace");
            if (trace != null) {
                propagatedContext.add(new Trace(trace));
                propagatedContext.add(new MdcPropagationContext(Map.of("trace", trace)));
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements HttpRoutes {
        private final Events events;

        Routes(Events events) {
            this.events = events;
        }

        private static void routeTrace(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            propagatedContext.add(new RouteTrace("r-" + request.getHeaders().get("X-Trace")));
        }

        private static void afterCtx(HttpRequest<?> request, io.micronaut.http.MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) {
            response.header("X-After-Ctx", describe());
        }

        private static HttpResponse<String> reply(String body) {
            return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE).header("X-Handler-Ctx", describe());
        }

        private static String order(HttpRequest<?> request, String step) {
            return step;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            // sync body read decoded
            routes.POST("/fz/text").consumesAll().before(Routes::routeTrace).and().after(Routes::afterCtx).and()
                .body(String.class).handle((request, pv, body) -> reply(body.getBytes(StandardCharsets.UTF_8).length + ":" + sha(body.getBytes(StandardCharsets.UTF_8))));
            routes.POST("/fz/blocking/text").consumesAll().executeOn(TaskExecutors.BLOCKING).before(Routes::routeTrace).and().after(Routes::afterCtx).and()
                .body(String.class).handle((request, pv, body) -> reply(body.getBytes(StandardCharsets.UTF_8).length + ":" + sha(body.getBytes(StandardCharsets.UTF_8))));

            // async body read, an async response filter, the continuation of the read
            routes.POST("/fz/atext").consumesAll().before(Routes::routeTrace).and()
                .afterAsync((request, response, ctx) -> {
                    String described = describe();
                    return CompletableFuture.runAsync(() -> response.header("X-After-Ctx", described), events.scheduler);
                }).and()
                .body().handleAsync((request, pv, body) -> {
                    String handler = describe();
                    return body.bytes(10_000_000).thenApply(bytes -> HttpResponse.ok(bytes.length + ":" + sha(bytes))
                        .contentType(MediaType.TEXT_PLAIN_TYPE)
                        .header("X-Handler-Ctx", handler)
                        .header("X-Continuation-Ctx", describe()));
                });
            routes.POST("/fz/bytes").consumesAll().before(Routes::routeTrace).and()
                .body().handleAsync((request, pv, body) -> body.bytes(10_000_000).thenApply(bytes -> HttpResponse.ok(bytes.length + ":" + sha(bytes))
                    .contentType(MediaType.TEXT_PLAIN_TYPE).header("X-Continuation-Ctx", describe())));

            // decoded JSON; unknown properties are ignored
            routes.POST("/fz/json").before(Routes::routeTrace).and()
                .body(Item.class).handle((request, pv, item) -> HttpResponse.ok(new Summary(item.name(), item.numbers().stream().mapToLong(Integer::longValue).sum(), item.numbers().size()))
                    .header("X-Handler-Ctx", describe()));

            // forms
            routes.POST("/fz/form").consumes(MediaType.APPLICATION_FORM_URLENCODED_TYPE).before(Routes::routeTrace).and()
                .form().handle((request, pv, form) -> reply(renderForm(form)));
            routes.POST("/fz/aform").consumes(MediaType.APPLICATION_FORM_URLENCODED_TYPE).before(Routes::routeTrace).and()
                .body().handleAsync((request, pv, body) -> {
                    String handler = describe();
                    return body.form().thenApply(form -> HttpResponse.ok(renderForm(form)).contentType(MediaType.TEXT_PLAIN_TYPE).header("X-Handler-Ctx", handler));
                });
            routes.POST("/fz/multipart").consumes(MediaType.MULTIPART_FORM_DATA_TYPE).before(Routes::routeTrace).and()
                .form().handleAsync((request, pv, form) -> {
                    Map<String, String> files = new TreeMap<>();
                    List<CompletableFuture<?>> reads = new ArrayList<>();
                    for (int i = 0; i < 10; i++) {
                        for (FileUpload file : form.getFiles("file" + i)) {
                            reads.add(file.bytes(10_000_000).toCompletableFuture().thenAccept(bytes -> {
                                synchronized (files) {
                                    files.put(file.name(), bytes.length + ":" + sha(bytes));
                                }
                            }));
                        }
                    }
                    return CompletableFuture.allOf(reads.toArray(new CompletableFuture[0]))
                        .thenApply(done -> HttpResponse.ok(renderForm(form) + files).contentType(MediaType.TEXT_PLAIN_TYPE)
                            .header("X-Continuation-Ctx", describe()));
                });

            // server-sent events
            routes.GET("/fz/sse/{count}").before(Routes::routeTrace).and().sse((request, pv, sse) -> {
                int count = pv.getInt("count");
                long delay = request.getParameters().get("delay", Integer.class).orElse(0);
                String padding = "p".repeat(request.getParameters().get("size", Integer.class).orElse(0));
                String lastId = sse.lastEventId().orElse("-");
                String trace = PropagatedContext.getOrEmpty().find(Trace.class).map(Trace::id).orElse("none");
                Events.OPEN_SSE.incrementAndGet();
                sse.keepOpen();
                sse.onClose(error -> Events.OPEN_SSE.decrementAndGet());
                emit(sse, 0, count, delay, trace, lastId, padding);
            });
            routes.POST("/fz/sse-body").consumesAll().before(Routes::routeTrace).and().body(String.class).sse((request, pv, body, sse) -> {
                int parts = request.getParameters().get("parts", Integer.class).orElse(1);
                String trace = PropagatedContext.getOrEmpty().find(Trace.class).map(Trace::id).orElse("none");
                int points = body.codePointCount(0, body.length());
                int size = (points + parts - 1) / parts;
                // the number of events is fixed: empty events keep the count; split between code points
                for (int i = 0; i < parts; i++) {
                    int from = body.offsetByCodePoints(0, Math.min(points, i * size));
                    int to = body.offsetByCodePoints(0, Math.min(points, (i + 1) * size));
                    sse.send(Event.of(trace + ":" + body.substring(from, to)));
                }
                sse.complete();
            });

            // path variables with a constraint
            routes.GET("/fz/path/{a}/{b}").constrain("b", value -> value.chars().allMatch(Character::isDigit))
                .handle((request, pv) -> HttpResponse.ok(pv.getString("a") + "|" + pv.getString("b")).contentType(MediaType.TEXT_PLAIN_TYPE));

            // errors
            routes.GET("/fz/err/{kind}").before(Routes::routeTrace).and().handle((request, pv) -> {
                switch (pv.getString("kind")) {
                    case "throw":
                        throw new IllegalStateException("throw");
                    case "nested":
                        throw new IllegalStateException("nested", new IllegalArgumentException("cause"));
                    default:
                        return HttpResponse.status(HttpStatus.CONFLICT);
                }
            });
            routes.GET("/fz/err/stage").before(Routes::routeTrace).and().handleAsync((request, pv) ->
                CompletableFuture.supplyAsync(() -> {
                    throw new IllegalStateException("stage");
                }, events.scheduler));
            routes.error(IllegalStateException.class, (request, error) -> HttpResponse.status(HttpStatus.I_AM_A_TEAPOT)
                .body((error.getCause() == null ? error.getMessage() : "nested") + "")
                .contentType(MediaType.TEXT_PLAIN_TYPE).header("X-Error-Ctx", describe()));

            // the filters
            routes.path("/fz/group", group -> {
                group.beforeReplacing(request -> request.getHeaders().contains("X-Block") ? HttpResponse.status(HttpStatus.FORBIDDEN) : null).and();
                group.before((request, ctx) -> {
                    routeTrace(request, ctx);
                    request.getAttribute("order", String.class);
                }).and();
                group.after((request, response, ctx) -> {
                    response.header("X-After-Ctx", describe());
                    response.getHeaders().add("X-Order", "g1");
                }).and();
                group.POST("/text").consumesAll()
                    .after((request, response, ctx) -> {
                        if (response.getStatus() == HttpStatus.OK) {
                            response.getHeaders().add("X-Order", "g2");
                            String order = String.join(",", response.getHeaders().getAll("X-Order"));
                            response.getHeaders().remove("X-Order");
                            response.header("X-Order", order);
                        } else {
                            String order = String.join(",", response.getHeaders().getAll("X-Order"));
                            response.getHeaders().remove("X-Order");
                            response.header("X-Order", order);
                        }
                    }).and()
                    .body(String.class).handle((request, pv, body) -> reply(body.getBytes(StandardCharsets.UTF_8).length + ":" + sha(body.getBytes(StandardCharsets.UTF_8))));
            });
            routes.POST("/fz/afilters").consumesAll()
                .beforeAsync((request, ctx) -> {
                    request.getAttributes().put("order", "a1");
                    return CompletableFuture.runAsync(() -> { }, events.scheduler)
                        .thenRun(() -> ctx.add(new RouteTrace("r-" + request.getHeaders().get("X-Trace"))));
                }).and()
                .beforeAsync((request, ctx) -> {
                    request.getAttributes().put("order", request.getAttributes().get("order", String.class).orElse("?") + ",a2");
                    return CompletableFuture.supplyAsync(() -> request.getHeaders().get("X-Trace"), events.scheduler)
                        .thenAccept(trace -> ctx.add(new LateTrace("l-" + trace)));
                }).and()
                .afterAsync((request, response, ctx) -> {
                    String order = request.getAttributes().get("order", String.class).orElse("?");
                    String described = describe();
                    return CompletableFuture.runAsync(() -> response.header("X-After-Ctx", described), events.scheduler)
                        .thenRun(() -> response.getHeaders().set("X-Order", order + ",r1"));
                }).and()
                .afterAsync((request, response, ctx) -> CompletableFuture.runAsync(() ->
                    response.getHeaders().set("X-Order", response.getHeaders().get("X-Order") + ",r2"), events.scheduler)).and()
                .body().handleAsync((request, pv, body) -> {
                    request.getAttributes().put("order", request.getAttributes().get("order", String.class).orElse("?") + ",handler");
                    String handler = describe();
                    return body.bytes(1_000_000).thenApply(bytes -> HttpResponse.ok(bytes.length + ":" + sha(bytes))
                        .contentType(MediaType.TEXT_PLAIN_TYPE).header("X-Handler-Ctx", handler));
                });
            routes.GET("/fz/late").before(Routes::routeTrace).and().after(Routes::afterCtx).and().handleAsync((request, pv) -> {
                int ms = request.getParameters().get("ms", Integer.class).orElse(0);
                CompletableFuture<HttpResponse<?>> future = new CompletableFuture<>();
                events.scheduler.schedule(() -> future.complete(HttpResponse.ok("late " + ms).contentType(MediaType.TEXT_PLAIN_TYPE)), ms, TimeUnit.MILLISECONDS);
                return future;
            });

            // WebSocket
            routes.GET("/fz/ws/{room}").webSocket(ws -> ws
                .onOpen((session, request) -> session.sendAsync("open " + room(session) + " trace=" + request.getHeaders().get("X-Trace") + " " + request.getPath()))
                .onMessage(String.class, (session, message) -> session.sendAsync("echo " + room(session) + " trace=" + session.getRequestParameters().getFirst("t").orElse("-") + " " + message)
                    .thenApply(sent -> {
                        return sent;
                    }))
                );
            routes.GET("/fz/wsb/{room}").webSocket(ws -> ws
                .onOpen((session, request) -> session.sendAsync("open " + room(session) + " trace=" + request.getHeaders().get("X-Trace") + " " + request.getPath()))
                .onMessage(byte[].class, (session, bytes) -> {
                    byte[] reversed = new byte[bytes.length];
                    for (int i = 0; i < bytes.length; i++) {
                        reversed[i] = bytes[bytes.length - 1 - i];
                    }
                    return session.sendAsync(reversed);
                }));
        }

        private static String room(WebSocketSession session) {
            return session.getUriVariables().get("room", String.class).orElse("?");
        }

        private void emit(io.micronaut.http.sse.SseEmitter sse, int i, int count, long delay, String trace, String lastId, String padding) {
            if (i >= count) {
                sse.complete();
                return;
            }
            sse.send(Event.of("e" + i + ":" + trace + ":" + lastId + ":" + padding).id(Integer.toString(i))).whenComplete((v, e) -> {
                if (e != null) {
                    return;
                }
                if (delay > 0) {
                    events.scheduler.schedule(() -> emit(sse, i + 1, count, delay, trace, lastId, padding), delay, TimeUnit.MILLISECONDS);
                } else {
                    emit(sse, i + 1, count, delay, trace, lastId, padding);
                }
            });
        }

        private static String renderForm(FormData form) {
            Map<String, List<String>> fields = new TreeMap<>();
            for (String name : form.names()) {
                List<String> values = form.getValues(name);
                if (!values.isEmpty()) {
                    fields.put(name, values);
                }
            }
            return render(fields);
        }
    }
}
