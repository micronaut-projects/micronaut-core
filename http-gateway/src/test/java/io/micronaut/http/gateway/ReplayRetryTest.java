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
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Retries that send a body that streams in again (RY-07, RY-08, RY-09), and a body no attempt
 * read sent to another instance.
 */
class ReplayRetryTest extends GatewayTestBase {

    static final String SPEC = "ReplayRetryTest";
    static final int DEAD_PORT = SocketUtils.findAvailableTcpPort();

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        // the first instance of the service refuses connections
        return Map.of(
            "micronaut.server.idle-timeout", "5s",
            "micronaut.http.services.handback.urls", List.of("http://127.0.0.1:" + DEAD_PORT, scripted.uri().toString()));
    }

    /**
     * Sends a chunked body in two parts with a pause, so that it still streams in when the
     * route calls the upstream.
     */
    private RawHttp.Response post(String path, String first, String second) throws Exception {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST " + path + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
                + Integer.toHexString(first.length()) + "\r\n" + first + "\r\n");
            Thread.sleep(300);
            http.send(Integer.toHexString(second.length()) + "\r\n" + second + "\r\n0\r\n\r\n");
            return http.read(false);
        }
    }

    private List<String> failFirst(String path) {
        List<String> bodies = new CopyOnWriteArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        scripted.on(path, (request, in, out, socket) -> {
            String body = new String(request.readBody(in), StandardCharsets.UTF_8);
            bodies.add(body);
            if (calls.incrementAndGet() == 1) {
                ScriptedUpstream.write(out, "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n");
            } else {
                String text = "attempt " + calls.get() + ": " + body;
                ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + text.length() + "\r\n\r\n" + text);
            }
            return true;
        });
        return bodies;
    }

    @Test
    void ry07ABodyThatStreamsInIsSentAgainByteForByte() throws Exception {
        List<String> bodies = failFirst("/ry07");
        RawHttp.Response response = post("/replay/ry07", "hello ", "world");
        assertEquals(200, response.status(), response.toString());
        assertEquals("attempt 2: hello world", response.text());
        assertEquals(List.of("hello world", "hello world"), bodies);
        assertEquals("true", response.header("X-Kept"));
    }

    @Test
    void rb15TheLastChunkThatArrivesAfterTheFirstAttemptFailedIsReplayed() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        scripted.on("/rb15", (request, in, out, socket) -> {
            if (calls.incrementAndGet() == 1) {
                // answers before the body ended: the rest of the body is still to come
                ScriptedUpstream.write(out, "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
                return false;
            }
            String body = new String(request.readBody(in), StandardCharsets.UTF_8);
            bodies.add(body);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + body.length() + "\r\n\r\n" + body);
            return true;
        });
        RawHttp.Response response = post("/replay/rb15", "early ", "late");
        assertEquals(200, response.status(), response.toString());
        assertEquals("early late", response.text());
        assertEquals(List.of("early late"), bodies);
    }

    @Test
    void ry08ABodyLargerThanTheReplayLimitIsNotSentAgain() throws Exception {
        List<String> bodies = failFirst("/ry08");
        RawHttp.Response response = post("/replay-small/ry08", "more than ", "eight bytes");
        // the upstream answer is relayed
        assertEquals(503, response.status(), response.toString());
        assertEquals(List.of("more than eight bytes"), bodies);
    }

    @Test
    void ry09TheBodyOfAMethodThatIsNotRetriedIsNotKept() throws Exception {
        List<String> bodies = failFirst("/ry09");
        RawHttp.Response response = post("/replay-get-only/ry09", "not ", "kept");
        assertEquals(503, response.status(), response.toString());
        assertEquals("false", response.header("X-Kept"));
        assertEquals(List.of("not kept"), bodies);
    }

    @Test
    void aBodyNoAttemptReadGoesToTheNextInstance() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        scripted.on("/handback", (request, in, out, socket) -> {
            String body = new String(request.readBody(in), StandardCharsets.UTF_8);
            bodies.add(body);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + body.length() + "\r\n\r\n" + body);
            return true;
        });
        // the first instance refuses the connection: the body was never read and is sent to the other one
        RawHttp.Response response = post("/handback/handback", "never ", "read");
        assertEquals(200, response.status(), response.toString());
        assertEquals("never read", response.text());
        assertEquals(List.of("never read"), bodies);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String scripted;

        Routes(@Value("${test.scripted}") String scripted) {
            this.scripted = scripted;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            GatewayFilter kept = new GatewayFilter() {
                @Override
                public void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) {
                    response.header("X-Kept", String.valueOf(GatewayRequestContext.find(exchange.request()).map(GatewayRequestContext::bodyKeptForReplay).orElse(false)));
                }
            };
            GatewayRetry retryPost = GatewayRetry.of(2).withMethods("POST").withStatuses(503).withBackoff(Duration.ofMillis(1), 1);
            gateway.route("replay").path("/replay/**").filters(GatewayFilters.stripPrefix(1), kept).retry(retryPost).uri(scripted);
            gateway.route("replay-small").path("/replay-small/**").filters(GatewayFilters.stripPrefix(1), kept)
                .retry(retryPost.withMaxReplayBytes(8)).uri(scripted);
            gateway.route("replay-get-only").path("/replay-get-only/**").filters(GatewayFilters.stripPrefix(1), kept)
                .retry(GatewayRetry.of(2).withStatuses(503).withBackoff(Duration.ofMillis(1), 1)).uri(scripted);
            gateway.route("handback").path("/handback/**").filters(GatewayFilters.stripPrefix(1))
                .retry(GatewayRetry.of(2).withMaxReplayBytes(0).withBackoff(Duration.ofMillis(1), 1)).uri("lb://handback");
        }
    }
}
