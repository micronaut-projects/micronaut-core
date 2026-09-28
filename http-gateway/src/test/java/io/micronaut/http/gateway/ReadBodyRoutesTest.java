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
import io.micronaut.core.type.Argument;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ReadBody} and body conditions: a gateway route chosen by the body of the request, with
 * the fall-through of Spring Cloud Gateway, where a body that does not match goes to the next
 * route (RT-21).
 */
class ReadBodyRoutesTest extends GatewayTestBase {

    static final String SPEC = "ReadBodyRoutesTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.server.idle-timeout", "5s");
        String r = "micronaut.gateway.routes.";
        // two routes of the same path: the body decides, the one without ReadBody is the fallback
        p.put(r + "yaml-express.uri", "${test.upstream}");
        p.put(r + "yaml-express.order", 1);
        p.put(r + "yaml-express.predicates", List.of("Path=/orders/**", "ReadBody=String, isExpress"));
        p.put(r + "yaml-express.filters", List.of("StripPrefix=1", "AddRequestHeader=X-Route, express"));
        p.put(r + "yaml-standard.uri", "${test.upstream}");
        p.put(r + "yaml-standard.order", 2);
        p.put(r + "yaml-standard.predicates", List.of("Path=/orders/**"));
        p.put(r + "yaml-standard.filters", List.of("StripPrefix=1", "AddRequestHeader=X-Route, standard"));
        // the named form, with a limit
        p.put(r + "yaml-named.uri", "${test.upstream}");
        p.put(r + "yaml-named.predicates[0].name", "Path");
        p.put(r + "yaml-named.predicates[0].args.patterns", "/named/**");
        p.put(r + "yaml-named.predicates[1].name", "ReadBody");
        p.put(r + "yaml-named.predicates[1].args.type", "String");
        p.put(r + "yaml-named.predicates[1].args.predicate", "isExpress");
        p.put(r + "yaml-named.predicates[1].args.max-size", "16");
        p.put(r + "yaml-named.filters", List.of("StripPrefix=1"));
        return p;
    }

    @Test
    void aBodyThatDoesNotMatchFallsThroughToTheNextRouteOfTheConfiguration() throws Exception {
        RawHttp.Response express = send("POST", "/orders/inspect/x", "express order", "Content-Type: text/plain");
        assertEquals(200, express.status(), express.toString());
        assertEquals("express", express.inspected("h:x-route"));
        // the body read to choose the route is relayed
        assertEquals("express order", express.inspected("body"));
        RawHttp.Response standard = send("POST", "/orders/inspect/x", "standard order", "Content-Type: text/plain");
        assertEquals(200, standard.status(), standard.toString());
        assertEquals("standard", standard.inspected("h:x-route"));
        assertEquals("standard order", standard.inspected("body"));
        // a chunked body too
        RawHttp.Response chunked = chunked("/orders/inspect/x", "an ", "express ", "order");
        assertEquals("express", chunked.inspected("h:x-route"), chunked.toString());
        assertEquals("an express order", chunked.inspected("body"));
    }

    @Test
    void theNamedFormTakesALimitAndABodyOverItDoesNotMatch() throws Exception {
        RawHttp.Response matched = send("POST", "/named/inspect/x", "express", "Content-Type: text/plain");
        assertEquals(200, matched.status(), matched.toString());
        assertEquals("express", matched.inspected("body"));
        // no other route: 404
        assertEquals(404, send("POST", "/named/inspect/x", "standard", "Content-Type: text/plain").status());
        // over the limit of 16 bytes: not a match
        assertEquals(404, send("POST", "/named/inspect/x", "express, but too long", "Content-Type: text/plain").status());
    }

    @Test
    void aBodyThatDoesNotMatchFallsThroughToTheNextRouteOfTheDsl() throws Exception {
        RawHttp.Response express = send("POST", "/dsl-orders/inspect/x", "{\"type\":\"express\"}", "Content-Type: application/json");
        assertEquals(200, express.status(), express.toString());
        assertEquals("express", express.inspected("h:x-route"));
        assertEquals("{\"type\":\"express\"}", express.inspected("body"));
        RawHttp.Response standard = send("POST", "/dsl-orders/inspect/x", "{\"type\":\"standard\"}", "Content-Type: application/json");
        assertEquals(200, standard.status(), standard.toString());
        assertEquals("standard", standard.inspected("h:x-route"));
        // a body that does not decode meets no condition
        RawHttp.Response notJson = send("POST", "/dsl-orders/inspect/x", "not json", "Content-Type: application/json");
        assertEquals("standard", notJson.inspected("h:x-route"), notJson.toString());
    }

    @Test
    void twoBodyRoutesOfTheSameOrderThatABodyBothMatchesAreAmbiguous() throws Exception {
        RawHttp.Response apple = send("POST", "/tie/inspect/x", "apple", "Content-Type: text/plain");
        assertEquals("apples", apple.inspected("h:x-route"), apple.toString());
        RawHttp.Response banana = send("POST", "/tie/inspect/x", "banana", "Content-Type: text/plain");
        assertEquals("bananas", banana.inspected("h:x-route"), banana.toString());
        // both match, with the same order: a duplicate route, as without body conditions
        assertEquals(400, send("POST", "/tie/inspect/x", "apple banana", "Content-Type: text/plain").status());
    }

    @Test
    void aRouteWithoutBodyConditionsStillStreams() throws Exception {
        CountDownLatch firstChunk = new CountDownLatch(1);
        scripted.on("/chunked", (request, in, out, socket) -> {
            // the first chunk, before the client sent the rest
            String size = ScriptedUpstream.line(in);
            byte[] chunk = in.readNBytes(Integer.parseInt(size.strip(), 16));
            ScriptedUpstream.line(in);
            if (new String(chunk, StandardCharsets.US_ASCII).equals("first")) {
                firstChunk.countDown();
            }
            byte[] rest = request.readBody(in);
            String text = "rest=" + new String(rest, StandardCharsets.US_ASCII);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + text.length() + "\r\n\r\n" + text);
            return true;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /stream/chunked HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\nContent-Type: text/plain\r\n\r\n5\r\nfirst\r\n");
            assertTrue(firstChunk.await(10, TimeUnit.SECONDS), "the upstream received the first chunk while the client still sends");
            http.send("6\r\nsecond\r\n0\r\n\r\n");
            RawHttp.Response response = http.read(false);
            assertEquals("rest=second", response.text());
        }
    }

    @Test
    void aLimitAboveTheBufferOfTheServerFailsTheStartup() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("micronaut.server.max-request-buffer-size", "1KB");
        properties.put("micronaut.gateway.routes.too-large.uri", "${test.upstream}");
        properties.put("micronaut.gateway.routes.too-large.predicates", List.of("Path=/too-large/**", "ReadBody=String, isExpress, 4KB"));
        RuntimeException e = assertThrows(RuntimeException.class, () -> {
            EmbeddedServer server = startGateway(SPEC + "-limit", upstream, properties);
            server.getApplicationContext().close();
        });
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        assertTrue(messages.toString().contains("max-request-buffer-size"), messages.toString());
        assertTrue(messages.toString().contains("/too-large"), messages.toString());
    }

    private RawHttp.Response chunked(String path, String... parts) throws Exception {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST " + path + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n");
            for (String part : parts) {
                http.send(Integer.toHexString(part.length()) + "\r\n" + part + "\r\n");
            }
            http.send("0\r\n\r\n");
            return http.read(false);
        }
    }

    @Singleton
    @Named("isExpress")
    @Requires(property = "spec.name", pattern = SPEC + ".*")
    static class IsExpress implements Predicate<String> {
        @Override
        public boolean test(String body) {
            return body.contains("express");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted) {
            this.upstream = upstream;
            this.scripted = scripted;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("dsl-express").path("/dsl-orders/**").order(1)
                .where(RequestCondition.body(Argument.mapOf(String.class, Object.class), order -> "express".equals(order.get("type"))))
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addRequestHeader("X-Route", "express")).uri(upstream);
            gateway.route("dsl-standard").path("/dsl-orders/**").order(2)
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addRequestHeader("X-Route", "standard")).uri(upstream);
            gateway.route("tie-apples").path("/tie/**")
                .where(RequestCondition.bodyText(text -> text.contains("apple")))
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addRequestHeader("X-Route", "apples")).uri(upstream);
            gateway.route("tie-bananas").path("/tie/**")
                .where(RequestCondition.bodyText(text -> text.contains("banana")))
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addRequestHeader("X-Route", "bananas")).uri(upstream);
            gateway.route("stream").path("/stream/**")
                .filters(GatewayFilters.stripPrefix(1)).uri(scripted);
        }
    }
}
