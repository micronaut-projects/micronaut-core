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
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Responses: statuses, streaming, framing, malformed and truncated upstream responses,
 * redirects, compression and cancellation.
 */
class ResponseTest extends GatewayTestBase {

    static final String SPEC = "ResponseTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> upstreamProperties() {
        // the upstream never compresses: a compressed body comes from the gateway only
        return Map.of("micronaut.server.netty.compression-threshold", -1);
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of("micronaut.server.idle-timeout", "8s");
    }

    @Test
    void rs01TheStatusIsRelayedVerbatimAndAnOutOfRangeStatusIsA502() throws Exception {
        for (int code : new int[]{200, 201, 404, 418, 432, 500, 503, 599}) {
            RawHttp.Response response = get("/up/status/" + code);
            assertEquals(code, response.status(), response.toString());
            assertEquals("yes", response.header("X-Upstream"));
        }
        scripted.on("/status-1000", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 1000 Weird\r\nContent-Length: 0\r\n\r\n");
            return false;
        });
        scripted.on("/no-status", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1\r\nContent-Length: 0\r\n\r\n");
            return false;
        });
        assertEquals(502, get("/raw/status-1000").status());
        assertEquals(502, get("/raw/no-status").status());
    }

    @Test
    void rs02AndRs03AStreamedResponseAndServerSentEventsAreRelayedAsTheyArrive() throws Exception {
        CountDownLatch next = new CountDownLatch(1);
        scripted.on("/sse", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n");
            String first = "data: first\n\n";
            ScriptedUpstream.write(out, Integer.toHexString(first.length()) + "\r\n" + first + "\r\n");
            next.await(20, TimeUnit.SECONDS);
            String second = "data: second\n\n";
            ScriptedUpstream.write(out, Integer.toHexString(second.length()) + "\r\n" + second + "\r\n0\r\n\r\n");
            return true;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("GET /raw/sse HTTP/1.1\r\nHost: localhost\r\nAccept: text/event-stream\r\nAccept-Encoding: gzip\r\n\r\n");
            StringBuilder received = new StringBuilder();
            long deadline = System.currentTimeMillis() + 10_000;
            while (!received.toString().contains("data: first") && System.currentTimeMillis() < deadline) {
                int c = http.in.read();
                if (c < 0) {
                    break;
                }
                received.append((char) c);
            }
            assertTrue(received.toString().contains("data: first"), "the first event arrived before the second was sent: " + received);
            assertFalse(received.toString().toLowerCase().contains("content-encoding"), received.toString());
            next.countDown();
        }
        RawHttp.Response events = get("/up/sse?count=3&delay=10", "Accept: text/event-stream");
        assertTrue(events.text().contains("data: event 3"), events.toString());
        assertEquals("text/event-stream", events.header("Content-Type"));
    }

    @Test
    void rs04AChunkedResponseToAnHttp10ClientIsNotChunked() throws Exception {
        RawHttp.Response response = RawHttp.exchange(gateway.getPort(), "GET /up/stream?size=100000 HTTP/1.0\r\nHost: localhost\r\n\r\n");
        assertEquals(200, response.status(), response.toString());
        assertNull(response.header("Transfer-Encoding"), response.toString());
        assertEquals(100_000, response.body().length);
        assertArrayEquals(Upstream.content(0, 8192), Arrays.copyOf(response.body(), 8192));
    }

    @Test
    void rs05AResponseFramedByTheCloseOfTheConnectionIsRelayedComplete() throws Exception {
        String body = "x".repeat(20_000);
        scripted.on("/close-framed", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\n" + body);
            return false;
        });
        RawHttp.Response response = get("/raw/close-framed");
        assertEquals(200, response.status());
        assertEquals(body, response.text());
        assertTrue(response.complete());
    }

    @Test
    void rs06HeadKeepsTheContentLengthAndSendsNoBody() throws Exception {
        scripted.on("/head", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 1234\r\nContent-Type: text/plain\r\n\r\n");
            return true;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("HEAD /raw/head HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RawHttp.Response head = http.read(true);
            assertEquals(200, head.status());
            assertEquals("1234", head.header("Content-Length"));
            assertNull(head.header("Transfer-Encoding"));
            // the next response on the connection is framed right: the HEAD sent no body
            http.send("GET /inspect/next HTTP/1.1\r\nHost: localhost\r\n\r\n");
            assertEquals("/inspect/next", http.read(false).inspected("path"));
        }
    }

    @Test
    void rs07NoContentAndNotModifiedCarryNoBodyAndKeepTheirContentLength() throws Exception {
        scripted.on("/204-with-body", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 204 No Content\r\nX-Upstream: yes\r\n\r\noops");
            return false;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("GET /raw/204-with-body HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RawHttp.Response noContent = http.read(false);
            assertEquals(204, noContent.status());
            assertNull(noContent.header("Transfer-Encoding"));
            http.send("GET /up/notmodified HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RawHttp.Response notModified = http.read(false);
            assertEquals(304, notModified.status(), notModified.toString());
            assertEquals("\"v1\"", notModified.header("ETag"));
            assertNull(notModified.header("Transfer-Encoding"));
            http.send("GET /inspect/after HTTP/1.1\r\nHost: localhost\r\n\r\n");
            assertEquals("/inspect/after", http.read(false).inspected("path"));
        }
    }

    @Test
    void rs08ABodyLongerThanItsLengthIsCutAndAShorterOneEndsTheConnection() throws Exception {
        scripted.on("/too-long", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhelloEXTRA-BYTES");
            return false;
        });
        scripted.on("/too-short", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nonly-some");
            return false;
        });
        RawHttp.Response tooLong = get("/raw/too-long");
        assertEquals("hello", tooLong.text());
        RawHttp.Response tooShort = get("/raw/too-short");
        assertEquals(200, tooShort.status());
        assertFalse(tooShort.complete(), "a too short response looked complete: " + tooShort);
    }

    @Test
    void rs09AndEr05AnUnfinishedChunkedResponseEndsTheConnectionWithoutTheLastChunk() throws Exception {
        scripted.on("/unfinished", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n");
            Thread.sleep(200);
            socket.setSoLinger(true, 0);
            return false;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("GET /raw/unfinished HTTP/1.1\r\nHost: localhost\r\n\r\n");
            byte[] raw = http.readToEnd();
            String text = new String(raw, StandardCharsets.ISO_8859_1);
            assertTrue(text.startsWith("HTTP/1.1 200"), text);
            assertTrue(text.contains("hello"), text);
            assertFalse(text.endsWith("0\r\n\r\n"), "a truncated response ended with the last chunk: " + text);
        }
    }

    @Test
    void rs10AnInvalidFramingOfTheUpstreamResponseIsA502() throws Exception {
        scripted.on("/double-length", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nContent-Length: 6\r\n\r\nhello!");
            return false;
        });
        scripted.on("/unknown-te", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nTransfer-Encoding: magic\r\n\r\nhello");
            return false;
        });
        scripted.on("/bad-length", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: five\r\n\r\nhello");
            return false;
        });
        assertEquals(502, get("/raw/double-length").status());
        assertEquals(502, get("/raw/bad-length").status());
        RawHttp.Response unknownTe = get("/raw/unknown-te");
        assertTrue(unknownTe.status() == 502 || unknownTe.status() == 200 && "hello".equals(unknownTe.text()), unknownTe.toString());
    }

    @Test
    void rs11AnInvalidHttpVersionOfTheUpstreamIsA502() throws Exception {
        scripted.on("/bad-version", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTX/9.9 200 OK\r\nContent-Length: 2\r\n\r\nok");
            return false;
        });
        assertEquals(502, get("/raw/bad-version").status());
    }

    @Test
    void rs12AnInvalidHeaderNameOfTheUpstreamIsA502() throws Exception {
        scripted.on("/bad-header", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nBad Header: x\r\nContent-Length: 2\r\n\r\nok");
            return false;
        });
        RawHttp.Response response = get("/raw/bad-header");
        assertTrue(response.status() == 502 || response.header("Bad Header") == null, response.toString());
    }

    @Test
    void rs13RedirectsOfTheUpstreamAreRelayedNotFollowed() throws Exception {
        for (int code : new int[]{301, 302, 303, 307, 308}) {
            scripted.on("/redirect-" + code, (request, in, out, socket) -> {
                ScriptedUpstream.write(out, "HTTP/1.1 " + code + " Redirect\r\nLocation: http://elsewhere.example/target\r\nContent-Length: 0\r\n\r\n");
                return true;
            });
            RawHttp.Response response = get("/raw/redirect-" + code);
            assertEquals(code, response.status());
            assertEquals("http://elsewhere.example/target", response.header("Location"));
        }
    }

    @Test
    void rs16CompressionIsRelayedAsItIs() throws Exception {
        RawHttp.Response gzip = get("/up/gzip", "Accept-Encoding: gzip");
        assertEquals("gzip", gzip.header("Content-Encoding"));
        assertEquals(String.valueOf(Upstream.GZIPPED.length), gzip.header("Content-Length"));
        assertArrayEquals(Upstream.GZIPPED, gzip.body());
        assertEquals("gzip, deflate", get("/inspect/x", "Accept-Encoding: gzip, deflate").inspected("h:accept-encoding"));
        // the upstream chose not to compress: the gateway does not either
        RawHttp.Response plain = get("/up/text?size=5000", "Accept-Encoding: gzip");
        assertNull(plain.header("Content-Encoding"), plain.toString());
        assertEquals("5000", plain.header("Content-Length"));
    }

    @Test
    void rs21LargeResponseHeadersAreRelayedUpToTheLimitOfTheClient() throws Exception {
        scripted.on("/big-header", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nX-Big: " + "b".repeat(6000) + "\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        scripted.on("/huge-header", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nX-Huge: " + "h".repeat(200_000) + "\r\nContent-Length: 2\r\n\r\nok");
            return false;
        });
        assertEquals(6000, get("/raw/big-header").header("X-Big").length());
        assertEquals(502, get("/raw/huge-header").status());
    }

    @Test
    void rs25AndRs27AClientThatLeavesMidResponseStopsTheUpstream() throws Exception {
        CountDownLatch stopped = new CountDownLatch(1);
        scripted.on("/endless", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n");
            try {
                for (int i = 0; i < 10_000; i++) {
                    ScriptedUpstream.write(out, "400\r\n" + "e".repeat(1024) + "\r\n");
                    Thread.sleep(5);
                }
            } catch (IOException e) {
                stopped.countDown();
            }
            return false;
        });
        RawHttp http = new RawHttp(gateway.getPort());
        http.send("GET /raw/endless HTTP/1.1\r\nHost: localhost\r\n\r\n");
        http.in.readNBytes(4096);
        http.socket.setSoLinger(true, 0);
        http.close();
        assertTrue(stopped.await(30, TimeUnit.SECONDS), "the upstream kept streaming after the client left");
    }

    @Test
    void rs26ContentTypesPassThroughAsTheyAre() throws Exception {
        assertEquals("text/plain; charset=utf-8; format=flowed", get("/up/content-type?type=text%2Fplain%3B%20charset%3Dutf-8%3B%20format%3Dflowed").header("Content-Type"));
        assertEquals("foo", get("/up/content-type?type=foo").header("Content-Type"));
        scripted.on("/no-type", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        RawHttp.Response none = get("/raw/no-type");
        assertNull(none.header("Content-Type"), none.toString());
        assertEquals("ok", none.text());
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
            gateway.route("inspect").path("/inspect/**").uri(upstream);
            gateway.route("up").path("/up/**").uri(upstream);
            gateway.route("raw").path("/raw/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted);
        }
    }

}
