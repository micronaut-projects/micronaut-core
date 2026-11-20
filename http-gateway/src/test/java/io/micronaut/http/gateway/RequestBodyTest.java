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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Request bodies: framing, streaming, backpressure, 100-continue, aborts and early responses.
 */
class RequestBodyTest extends GatewayTestBase {

    static final String SPEC = "RequestBodyTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        // a request the test abandons is noticed even when the loopback drops the reset
        return Map.of("micronaut.server.idle-timeout", "8s");
    }

    @Test
    void rb01ABodyWithAContentLengthIsRelayedByteForByte() throws IOException {
        byte[] body = Upstream.content(3, 300_000);
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send(("POST /up/upload HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Type: application/octet-stream\r\nContent-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            http.send(body);
            RawHttp.Response response = http.read(false);
            assertEquals("POST " + Upstream.digest(body), response.text());
        }
    }

    @Test
    void rb02AChunkedBodyIsStreamedNotBuffered() throws Exception {
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
            String text = "te=" + request.header("transfer-encoding") + " rest=" + new String(rest, StandardCharsets.US_ASCII);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + text.length() + "\r\n\r\n" + text);
            return true;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /raw/chunked HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\nContent-Type: text/plain\r\n\r\n5\r\nfirst\r\n");
            assertTrue(firstChunk.await(10, TimeUnit.SECONDS), "the upstream received the first chunk while the client still sends");
            http.send("6\r\nsecond\r\n0\r\n\r\n");
            RawHttp.Response response = http.read(false);
            assertEquals("te=chunked rest=second", response.text());
        }
    }

    @Test
    void rb03AGiantBodyStreamsBothWays() throws IOException {
        int size = 48 * 1024 * 1024;
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send(("POST /up/upload HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/octet-stream\r\nContent-Length: " + size + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            for (int offset = 0; offset < size; offset += 1024 * 1024) {
                byte[] chunk = Upstream.content(offset, 1024 * 1024);
                crc.update(chunk);
                http.send(chunk);
            }
            assertEquals("POST " + size + ":" + crc.getValue(), http.read(false).text());
        }
        RawHttp.Response download = get("/up/stream?size=" + size);
        assertEquals(size, download.body().length);
        java.util.zip.CRC32 expected = new java.util.zip.CRC32();
        for (int offset = 0; offset < size; offset += 8192) {
            expected.update(Upstream.content(offset, Math.min(8192, size - offset)));
        }
        java.util.zip.CRC32 actual = new java.util.zip.CRC32();
        actual.update(download.body());
        assertEquals(expected.getValue(), actual.getValue());
    }

    @Test
    void rb04AndRb05EmptyAndBodylessRequestsKeepTheirFramingAndGetOrDeleteBodiesPass() throws Exception {
        scripted.on("/framing", (request, in, out, socket) -> {
            byte[] body = request.readBody(in);
            String text = "cl=" + request.header("content-length") + " te=" + request.header("transfer-encoding") + " body=" + new String(body, StandardCharsets.US_ASCII);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + text.length() + "\r\n\r\n" + text);
            return true;
        });
        assertEquals("cl=0 te=null body=", send("POST", "/raw/framing", "").text());
        assertEquals("cl=null te=null body=", get("/raw/framing").text());
        // the client frames an empty OPTIONS, like an empty DELETE, with a length of 0: never chunked
        String options = send("OPTIONS", "/raw/framing", null).text();
        assertTrue(options.equals("cl=null te=null body=") || options.equals("cl=0 te=null body="), options);
        assertEquals("cl=3 te=null body=abc", send("GET", "/raw/framing", "abc").text());
        assertEquals("cl=3 te=null body=xyz", send("DELETE", "/raw/framing", "xyz").text());
    }

    @Test
    void rb06FormAndMultipartBodiesPassUnchanged() throws IOException {
        String form = "a=1&b=x+y&c=%26";
        RawHttp.Response formResponse = send("POST", "/inspect/form", form, "Content-Type: application/x-www-form-urlencoded");
        assertEquals(form, formResponse.inspected("body"));
        String multipart = "--XyZ\r\nContent-Disposition: form-data; name=\"f\"; filename=\"a.txt\"\r\nContent-Type: text/plain\r\n\r\nhello\r\n--XyZ--\r\n";
        RawHttp.Response multipartResponse = send("POST", "/inspect/multipart", multipart, "Content-Type: multipart/form-data; boundary=XyZ");
        assertEquals(multipart.replace("\n", "\\n"), multipartResponse.inspected("body"));
        assertEquals("multipart/form-data; boundary=XyZ", multipartResponse.inspected("h:content-type"));
    }

    @Test
    void rb07ExpectContinueIsEndToEndAndAFinalResponseWithoutContinueIsDelivered() throws Exception {
        scripted.on("/continue", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 100 Continue\r\n\r\n");
            byte[] body = request.readBody(in);
            String text = "expect=" + request.header("expect") + " body=" + new String(body, StandardCharsets.US_ASCII);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + text.length() + "\r\n\r\n" + text);
            return true;
        });
        scripted.on("/reject", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 401 Unauthorized\r\nContent-Length: 6\r\nConnection: close\r\n\r\ndenied");
            return false;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /raw/continue HTTP/1.1\r\nHost: localhost\r\nExpect: 100-continue\r\nContent-Length: 5\r\nContent-Type: text/plain\r\n\r\n");
            RawHttp.Response interim = http.read(false);
            assertEquals(100, interim.status(), interim.toString());
            http.send("hello");
            RawHttp.Response response = http.read(false);
            assertEquals(200, response.status());
            assertEquals("expect=100-continue body=hello", response.text());
        }
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /raw/reject HTTP/1.1\r\nHost: localhost\r\nExpect: 100-continue\r\nContent-Length: 5\r\nContent-Type: text/plain\r\n\r\n");
            RawHttp.Response response = http.read(false);
            if (response.status() == 100) {
                // the server may say continue once the relay starts to read the body; the final
                // response is still the one of the upstream
                response = http.read(false);
            }
            assertEquals(401, response.status(), response.toString());
            assertEquals("denied", response.text());
        }
    }

    @Test
    void rb08ASlowUpstreamThrottlesTheUploadAndASlowClientTheDownload() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicLong read = new AtomicLong();
        scripted.on("/slow-reader", (request, in, out, socket) -> {
            release.await(30, TimeUnit.SECONDS);
            byte[] buffer = new byte[65536];
            long length = Long.parseLong(request.header("content-length"));
            while (read.get() < length) {
                int n = in.read(buffer);
                if (n < 0) {
                    break;
                }
                read.addAndGet(n);
            }
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        long size = 256L * 1024 * 1024;
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.socket.setSendBufferSize(64 * 1024);
            http.send("POST /raw/slow-reader HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/octet-stream\r\nContent-Length: " + size + "\r\n\r\n");
            AtomicLong written = new AtomicLong();
            Thread writer = new Thread(() -> {
                byte[] chunk = new byte[64 * 1024];
                try {
                    while (written.get() < size) {
                        http.out.write(chunk);
                        written.addAndGet(chunk.length);
                    }
                } catch (IOException e) {
                    // the test closes the connection
                }
            });
            writer.setDaemon(true);
            writer.start();
            Thread.sleep(2000);
            long stalled = written.get();
            assertTrue(stalled < 64L * 1024 * 1024, "the gateway buffered the upload: " + stalled);
            release.countDown();
            writer.join(60_000);
            assertEquals(size, written.get());
            assertEquals("ok", http.read(false).text());
        }
        // a slow client: the scripted upstream writes as fast as the gateway takes it
        AtomicLong sent = new AtomicLong();
        CountDownLatch done = new CountDownLatch(1);
        scripted.on("/fast-writer", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + size + "\r\n\r\n");
            byte[] chunk = new byte[64 * 1024];
            try {
                while (sent.get() < size) {
                    out.write(chunk);
                    sent.addAndGet(chunk.length);
                }
            } catch (IOException e) {
                // the client went away
            } finally {
                done.countDown();
            }
            return false;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.socket.setReceiveBufferSize(64 * 1024);
            http.send("GET /raw/fast-writer HTTP/1.1\r\nHost: localhost\r\n\r\n");
            Thread.sleep(2000);
            long produced = sent.get();
            assertTrue(produced < 64L * 1024 * 1024, "the gateway buffered the download: " + produced);
        }
        assertTrue(done.await(30, TimeUnit.SECONDS), "the upstream stream ended once the client left");
    }

    @Test
    void rb09AnAbortedUploadClosesTheUpstreamRequest() throws Exception {
        CountDownLatch upstreamEnded = new CountDownLatch(1);
        scripted.on("/abort-upload", (request, in, out, socket) -> {
            long length = Long.parseLong(request.header("content-length"));
            long total = 0;
            byte[] buffer = new byte[8192];
            try {
                int n;
                while (total < length && (n = in.read(buffer)) >= 0) {
                    total += n;
                }
            } catch (IOException e) {
                // reset
            }
            if (total < length) {
                upstreamEnded.countDown();
            }
            return false;
        });
        RawHttp http = new RawHttp(gateway.getPort());
        http.send("POST /raw/abort-upload HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/octet-stream\r\nContent-Length: 1000000\r\n\r\n");
        http.send(new byte[100_000]);
        Thread.sleep(500);
        http.socket.setSoLinger(true, 0);
        http.close();
        assertTrue(upstreamEnded.await(20, TimeUnit.SECONDS), "the upstream request was not closed");
    }

    @Test
    void rb10TheUpstreamClosingDuringTheUploadIsA502() throws Exception {
        scripted.on("/close-during-upload", (request, in, out, socket) -> {
            in.readNBytes(10);
            socket.setSoLinger(true, 0);
            return false;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /raw/close-during-upload HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/octet-stream\r\nContent-Length: 1000000\r\n\r\n");
            try {
                for (int i = 0; i < 100; i++) {
                    http.send(new byte[10_000]);
                    Thread.sleep(5);
                }
            } catch (IOException e) {
                // the gateway may close the connection while the client still sends
            }
            RawHttp.Response response;
            try {
                response = http.read(false);
            } catch (IOException e) {
                // the gateway closed the connection after answering, before the client read it
                return;
            }
            assertEquals(502, response.status(), response.toString());
        }
    }

    @Test
    void rb11AnEarlyResponseDuringTheUploadIsRelayedAndItsConnectionIsNotReused() throws Exception {
        scripted.on("/early", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 413 Payload Too Large\r\nContent-Length: 8\r\n\r\ntoo much");
            // keep the connection open without reading the body
            Thread.sleep(3000);
            return false;
        });
        scripted.on("/after-early", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nafter");
            return true;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /raw/early HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/octet-stream\r\nContent-Length: 10000000\r\n\r\n");
            http.send(new byte[50_000]);
            RawHttp.Response response = http.read(false);
            assertEquals(413, response.status(), response.toString());
            assertEquals("too much", response.text());
        }
        int connections = scripted.connections.get();
        assertEquals("after", get("/raw/after-early").text());
        assertTrue(scripted.connections.get() > connections, "the connection of the early response was reused");
    }

    @Test
    void rb14ABodyShorterThanItsContentLengthNeverReachesTheUpstreamComplete() throws Exception {
        CountDownLatch incomplete = new CountDownLatch(1);
        scripted.on("/short-body", (request, in, out, socket) -> {
            byte[] body = in.readNBytes(Integer.parseInt(request.header("content-length")));
            if (body.length < 100) {
                incomplete.countDown();
                return false;
            }
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 8\r\n\r\ncomplete");
            return true;
        });
        RawHttp http = new RawHttp(gateway.getPort());
        http.send("POST /raw/short-body HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nContent-Length: 100\r\n\r\nonly-some");
        Thread.sleep(300);
        http.socket.setSoLinger(true, 0);
        http.close();
        assertTrue(incomplete.await(20, TimeUnit.SECONDS), "the upstream got a complete request from an incomplete one");
        // a body longer than its Content-Length: the rest is the next request, which is not HTTP
        try (RawHttp longer = new RawHttp(gateway.getPort())) {
            longer.send("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nContent-Length: 3\r\n\r\nabcGARBAGE\r\n\r\n");
            RawHttp.Response first = longer.read(false);
            assertEquals("abc", first.inspected("body"));
            try {
                RawHttp.Response second = longer.read(false);
                assertEquals(400, second.status(), second.toString());
            } catch (IOException | NumberFormatException e) {
                // the connection was closed instead
            }
        }
    }

    @Test
    void rb15AClientThatGoesAwayCancelsTheUpstreamExchange() throws Exception {
        CountDownLatch upstreamClosed = new CountDownLatch(1);
        scripted.on("/silent", (request, in, out, socket) -> {
            // never answers: only a cancel of the exchange closes the connection, long before the
            // read timeout of 20s
            try {
                while (in.read() >= 0) {
                    // nothing is sent after the head
                }
            } catch (IOException e) {
                // reset
            }
            upstreamClosed.countDown();
            return false;
        });
        RawHttp http = new RawHttp(gateway.getPort());
        http.send("GET /raw/silent HTTP/1.1\r\nHost: localhost\r\n\r\n");
        Thread.sleep(500);
        http.close();
        assertTrue(upstreamClosed.await(5, TimeUnit.SECONDS), "the upstream exchange was not cancelled");
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
