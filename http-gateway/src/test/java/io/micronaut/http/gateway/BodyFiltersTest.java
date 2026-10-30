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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import io.micronaut.json.JsonMapper;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body filters: ModifyRequestBody, ModifyResponseBody, RemoveJsonAttributesResponseBody and
 * ReadBody (RB-16, BT-01, BT-02, RT-21, RS-18).
 */
class BodyFiltersTest extends GatewayTestBase {

    static final String SPEC = "BodyFiltersTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.server.idle-timeout", "5s");
        String r = "micronaut.gateway.routes.";
        p.put(r + "yaml-json.uri", "${test.upstream}");
        p.put(r + "yaml-json.predicates", List.of("Path=/yaml-json/**"));
        p.put(r + "yaml-json.filters", List.of("StripPrefix=1", "RemoveJsonAttributesResponseBody=a, true"));
        p.put(r + "yaml-upper.uri", "${test.upstream}");
        p.put(r + "yaml-upper.predicates", List.of("Path=/yaml-upper/**", "ReadBody=byte[], hasHello, 1KB"));
        p.put(r + "yaml-upper.filters", List.of("StripPrefix=1", "ModifyRequestBody=upper, 1KB"));
        return p;
    }

    private RawHttp.Response chunked(String path, String... parts) throws Exception {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST " + path + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n");
            for (String part : parts) {
                http.send(Integer.toHexString(part.length()) + "\r\n" + part + "\r\n");
                Thread.sleep(50);
            }
            http.send("0\r\n\r\n");
            return http.read(false);
        }
    }

    private static String gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void rb16TheRequestBodyIsRewrittenWithItsNewLength() throws Exception {
        RawHttp.Response response = chunked("/modify-request/inspect/x", "hello ", "world");
        assertEquals(200, response.status(), response.toString());
        assertEquals("HELLO WORLD", response.inspected("body"));
        assertEquals("11", response.inspected("h:content-length"));
        assertEquals("text/x-upper", response.inspected("h:content-type"));
        assertNull(response.inspected("h:transfer-encoding"));
        // an empty body is rewritten too
        RawHttp.Response empty = send("POST", "/modify-request/inspect/x", "", "Content-Type: text/plain");
        assertEquals(200, empty.status(), empty.toString());
        assertEquals("text/x-upper", empty.inspected("h:content-type"));
        // a body over the limit
        assertEquals(413, chunked("/modify-request/inspect/x", "x".repeat(40), "x".repeat(40)).status());
    }

    @Test
    void rb17TheSizeLimitOfTheRouteAppliesToAChunkedBodyThatIsRewritten() throws Exception {
        assertEquals(413, chunked("/sized-rewrite/inspect/x", "x".repeat(8), "x".repeat(8)).status());
        RawHttp.Response small = chunked("/sized-rewrite/inspect/x", "abc", "de");
        assertEquals(200, small.status(), small.toString());
        assertEquals("ABCDE", small.inspected("body"));
    }

    @Test
    void bt01TheResponseBodyIsRewrittenDecodedAndEncodedAgain() throws Exception {
        RawHttp.Response response = get("/modify-response/up/gzip", "Accept-Encoding: gzip");
        assertEquals(200, response.status(), response.toString());
        assertEquals("gzip", response.header("Content-Encoding"));
        assertEquals(String.valueOf(response.body().length), response.header("Content-Length"));
        assertEquals("COMPRESSED BODY ".repeat(200), gunzip(response.body()));
        assertEquals("text/x-upper", response.header("Content-Type"));
        // plain
        RawHttp.Response plain = get("/modify-response/up/text?size=5");
        assertEquals("TTTTT", plain.text());
        assertEquals("5", plain.header("Content-Length"));
        // too large
        assertEquals(502, get("/modify-response/up/text?size=5000").status());
        // HEAD is not rewritten
        RawHttp.Response head = RawHttp.exchange(gateway.getPort(), "HEAD /modify-response/up/text?size=5 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII), true);
        assertEquals(200, head.status(), head.toString());
    }

    @Test
    void bt02JsonAttributesAreRemovedAtTheTopOrRecursively() throws IOException {
        scripted.on("/nested", (request, in, out, socket) -> {
            String json = "{\"a\":1,\"b\":{\"a\":2,\"c\":3},\"d\":[{\"a\":4,\"e\":5}]}";
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + json.length() + "\r\n\r\n" + json);
            return true;
        });
        String top = get("/json-top/nested").text().replace(" ", "");
        assertEquals("{\"b\":{\"a\":2,\"c\":3},\"d\":[{\"a\":4,\"e\":5}]}", top);
        String recursive = get("/yaml-json-raw/nested").text().replace(" ", "");
        assertEquals("{\"b\":{\"c\":3},\"d\":[{\"e\":5}]}", recursive);
        // not JSON
        scripted.on("/notjson", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 4\r\n\r\nnope");
            return true;
        });
        assertEquals(502, get("/json-top/notjson").status());
        // from the configuration, recursively
        assertEquals("{\"b\":[1,2]}", get("/yaml-json/up/json").text().replace(" ", "").replace("\n", ""));
    }

    @Test
    void rt21ABodyPredicateReadsTheBodyThatIsStillRelayed() throws Exception {
        RawHttp.Response matched = chunked("/yaml-upper/inspect/x", "say ", "hello");
        assertEquals(200, matched.status(), matched.toString());
        assertEquals("SAY HELLO", matched.inspected("body"));
        assertEquals(404, chunked("/yaml-upper/inspect/x", "say ", "bye").status());
        RawHttp.Response inCode = chunked("/read-body/inspect/x", "abc", "def");
        assertEquals("abcdef", inCode.inspected("body"));
        assertEquals(404, chunked("/read-body/inspect/x", "xyz").status());
    }

    @Test
    void rs18TheTrailersOfARewrittenResponseAreDropped() throws Exception {
        scripted.on("/trailers", (request, in, out, socket) -> {
            request.readBody(in);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nTrailer: grpc-status\r\n\r\n3\r\nabc\r\n0\r\ngrpc-status: 0\r\n\r\n");
            return true;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("GET /trailers-rewritten/trailers HTTP/1.1\r\nHost: localhost\r\nTE: trailers\r\nConnection: close\r\n\r\n");
            RawHttp.Response response = http.read(false);
            assertEquals("ABC", response.text(), response.toString());
            assertNull(response.header("trailer:grpc-status"), response.toString());
        }
    }

    @Singleton
    @Named("upper")
    @Requires(property = "spec.name", value = SPEC)
    static class Upper implements BodyRewriter {
        @Override
        public CompletableFuture<byte[]> rewrite(ProxyExchange exchange, io.micronaut.http.MutableHttpHeaders headers, byte[] body) {
            headers.set(HttpHeaders.CONTENT_TYPE, "text/x-upper");
            return CompletableFuture.completedFuture(new String(body, StandardCharsets.UTF_8).toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        }
    }

    @Singleton
    @Named("hasHello")
    @Requires(property = "spec.name", value = SPEC)
    static class HasHello implements Predicate<byte[]> {
        @Override
        public boolean test(byte[] body) {
            return new String(body, StandardCharsets.UTF_8).contains("hello");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;
        private final Upper upper;
        private final JsonMapper mapper;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted, Upper upper, JsonMapper mapper) {
            this.upstream = upstream;
            this.scripted = scripted;
            this.upper = upper;
            this.mapper = mapper;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("modify-request").path("/modify-request/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.modifyRequestBody(64, upper)).uri(upstream);
            gateway.route("sized-rewrite").path("/sized-rewrite/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.requestSize(10), GatewayFilters.modifyRequestBody(64, upper)).uri(upstream);
            gateway.route("modify-response").path("/modify-response/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.modifyResponseBody(4096, upper)).uri(upstream);
            gateway.route("json-top").path("/json-top/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.removeJsonAttributesResponseBody(mapper, 4096, false, "a")).uri(scripted);
            gateway.route("yaml-json-raw").path("/yaml-json-raw/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.removeJsonAttributesResponseBody(mapper, 4096, true, "a")).uri(scripted);
            gateway.route("read-body").path("/read-body/**")
                .where(RequestCondition.bodyBytes(body -> body.length > 3).maxBytes(64))
                .filters(GatewayFilters.stripPrefix(1)).uri(upstream);
            gateway.route("trailers-rewritten").path("/trailers-rewritten/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.modifyResponseBody(4096, upper)).uri(scripted);
        }
    }
}
