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
package io.micronaut.http.gateway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;

/**
 * The upstream server of the gateway tests: a Micronaut server with endpoints that tell what
 * they received and answer in the ways the tests need.
 */
final class Upstream {

    static final String SPEC = "GatewayUpstream";
    static final byte[] GZIPPED = gzip("compressed body ".repeat(200));

    private Upstream() {
    }

    static EmbeddedServer start() {
        return start(Map.of());
    }

    static EmbeddedServer start(Map<String, Object> properties) {
        Map<String, Object> all = new HashMap<>(properties);
        all.put("spec.name", SPEC);
        all.put("micronaut.server.port", -1);
        all.putIfAbsent("micronaut.server.max-request-size", 256 * 1024 * 1024);
        return ApplicationContext.run(EmbeddedServer.class, all);
    }

    static byte[] content(long offset, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) ((offset + i) * 31 % 251);
        }
        return bytes;
    }

    static String digest(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return bytes.length + ":" + crc.getValue();
    }

    private static byte[] gzip(String text) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Counts the requests of the endpoints that tests count.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Counters {
        final Map<String, AtomicInteger> counts = new java.util.concurrent.ConcurrentHashMap<>();

        int increment(String name) {
            return counts.computeIfAbsent(name, n -> new AtomicInteger()).incrementAndGet();
        }

        int get(String name) {
            AtomicInteger count = counts.get(name);
            return count == null ? 0 : count.get();
        }
    }

    /**
     * Every method and path under {@code /inspect} answers what it received: the method, the
     * raw path and query, each header, and the body.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class InspectRoutes implements HttpRoutes {
        private final Counters counters;

        InspectRoutes(Counters counters) {
            this.counters = counters;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            TestProxyRoutes.proxy(routes, "/inspect", (request, pathVariables) -> {
                counters.increment("inspect");
                return ((ServerHttpRequest<?>) request).byteBody().buffer().thenApply(body -> {
                    try (CloseableAvailableByteBody b = body) {
                        byte[] bytes = b.toByteArray();
                        return HttpResponse.ok(describe(request, bytes)).contentType(MediaType.TEXT_PLAIN_TYPE);
                    }
                });
            });
            // counts, then answers 503 to the first requests of a key and 200 to the next ones
            TestProxyRoutes.proxy(routes, "/flaky", (request, pathVariables) -> {
                String key = request.getParameters().get("key");
                int failures = Integer.parseInt(request.getParameters().get("failures", String.class).orElse("1"));
                int n = counters.increment("flaky-" + key);
                return ((ServerHttpRequest<?>) request).byteBody().buffer().thenApply(body -> {
                    try (CloseableAvailableByteBody b = body) {
                        if (n <= failures) {
                            return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("attempt " + n).contentType(MediaType.TEXT_PLAIN_TYPE);
                        }
                        byte[] bytes = b.toByteArray();
                        return HttpResponse.ok("attempt " + n + " " + digest(bytes) + (request.getParameters().contains("describe") ? "\n" + describe(request, bytes) : ""))
                            .contentType(MediaType.TEXT_PLAIN_TYPE);
                    }
                });
            });
        }

        static String describe(HttpRequest<?> request, byte[] body) {
            StringBuilder text = new StringBuilder()
                .append("method=").append(request.getMethodName()).append('\n')
                .append("path=").append(request.getPath()).append('\n')
                .append("query=").append(request.getUri().getRawQuery()).append('\n')
                .append("uri=").append(request.getUri().getRawPath()).append(request.getUri().getRawQuery() == null ? "" : "?" + request.getUri().getRawQuery()).append('\n');
            request.getHeaders().forEach((name, values) -> {
                for (String value : values) {
                    text.append("h:").append(name.toLowerCase(java.util.Locale.ROOT)).append('=').append(value).append('\n');
                }
            });
            text.append("body-length=").append(body.length).append('\n');
            text.append("body-digest=").append(digest(body)).append('\n');
            if (body.length < 4096) {
                text.append("body=").append(new String(body, StandardCharsets.UTF_8).replace("\n", "\\n")).append('\n');
            }
            return text.toString();
        }
    }

    @Controller("/up")
    @Requires(property = "spec.name", value = SPEC)
    static class Endpoints {
        private final BeanProvider<EmbeddedServer> server;
        private final Counters counters;

        Endpoints(BeanProvider<EmbeddedServer> server, Counters counters) {
            this.server = server;
            this.counters = counters;
        }

        @Get(value = "/status/{code}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> status(@PathVariable int code) {
            counters.increment("status-" + code);
            return HttpResponse.<String>status(code, "Status " + code).body("status " + code).header("X-Upstream", "yes");
        }

        @Get(value = "/instance", produces = MediaType.TEXT_PLAIN)
        String instance() {
            counters.increment("instance");
            return String.valueOf(server.get().getPort());
        }

        @Get("/redirect")
        HttpResponse<?> redirect() {
            return HttpResponse.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, server.get().getURI().resolve("/up/target").toString());
        }

        @Get("/location")
        HttpResponse<?> location(@Header(HttpHeaders.HOST) String host) {
            return HttpResponse.status(HttpStatus.CREATED).header(HttpHeaders.LOCATION, "http://" + host + "/up/items/1?x=1");
        }

        @Get(value = "/cookies", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> cookies() {
            return HttpResponse.ok("cookies")
                .header(HttpHeaders.SET_COOKIE, "a=1; Path=/; Expires=Wed, 21 Oct 2037 07:28:00 GMT")
                .header(HttpHeaders.SET_COOKIE, "b=2; Path=/; HttpOnly");
        }

        @Get(value = "/headers", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> headers() {
            return HttpResponse.ok("headers")
                .header("X-Dup", "one")
                .header("X-Dup", "two")
                .header("X-Dup", "one")
                .header("Access-Control-Allow-Origin", "*")
                .header("X-Empty", "")
                .header("X-Spaces", "a  b   c")
                .header(HttpHeaders.PROXY_AUTHENTICATE, "Basic")
                .header("Keep-Alive", "timeout=5");
        }

        @Get("/gzip")
        HttpResponse<byte[]> gzip() {
            return HttpResponse.ok(GZIPPED)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header(HttpHeaders.CONTENT_ENCODING, "gzip");
        }

        @Get(value = "/text", produces = MediaType.TEXT_PLAIN)
        String text(@QueryValue(defaultValue = "2000") int size) {
            return "t".repeat(size);
        }

        @Get(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> sse(@QueryValue(defaultValue = "3") int count, @QueryValue(defaultValue = "50") long delay) {
            return Flux.range(1, count)
                .delayElements(Duration.ofMillis(delay))
                .map(i -> Event.of("event " + i).id(String.valueOf(i)));
        }

        @Get(value = "/stream", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> stream(@QueryValue long size, @QueryValue(defaultValue = "0") long delay) {
            int chunk = 8192;
            Flux<byte[]> flux = Flux.generate(() -> 0L, (offset, sink) -> {
                if (offset >= size) {
                    sink.complete();
                    return offset;
                }
                int n = (int) Math.min(chunk, size - offset);
                sink.next(content(offset, n));
                return offset + n;
            });
            return delay > 0 ? flux.delayElements(Duration.ofMillis(delay)) : flux;
        }

        @Post(value = "/upload", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        Mono<String> upload(HttpRequest<?> request, @Body Publisher<byte[]> body) {
            counters.increment("upload");
            return Flux.from(body).collect(ByteArrayOutputStream::new, (out, bytes) -> out.write(bytes, 0, bytes.length))
                .map(out -> request.getMethodName() + " " + digest(out.toByteArray()));
        }

        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<String> slow(@QueryValue long ms) {
            counters.increment("slow");
            return Mono.delay(Duration.ofMillis(ms)).map(x -> "slow").toFuture();
        }

        @Get("/nocontent")
        HttpResponse<?> noContent() {
            return HttpResponse.noContent().header("X-Upstream", "yes");
        }

        @Get("/notmodified")
        HttpResponse<?> notModified() {
            return HttpResponse.notModified().header(HttpHeaders.ETAG, "\"v1\"").header(HttpHeaders.CONTENT_LENGTH, "123");
        }

        @Get(value = "/json", produces = MediaType.APPLICATION_JSON)
        String json() {
            return "{ \"a\" : 1,\n  \"b\" : [ 1, 2 ] }";
        }

        @Get(value = "/content-type")
        HttpResponse<String> contentType(@QueryValue @Nullable String type) {
            HttpResponse<String> response = HttpResponse.ok("typed");
            if (type != null) {
                response = HttpResponse.ok("typed").header(HttpHeaders.CONTENT_TYPE, type);
            }
            return response;
        }
    }
}
