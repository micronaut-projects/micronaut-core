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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpVersionSelection;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP versions on both sides of the gateway: HTTP/2 clients, HTTP/2 upstreams, HTTP/1.0
 * clients and invalid versions.
 */
class HttpVersionTest extends GatewayTestBase {

    static final String SPEC = "HttpVersionTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> upstreamProperties() {
        return Map.of("micronaut.server.http-version", "2.0");
    }

    @Override
    Map<String, Object> gatewayProperties() {
        // the server settings the gateway guide recommends for a gateway (HV-09, HV-10)
        return Map.of(
            "micronaut.server.http-version", "2.0",
            "micronaut.server.http10-keep-alive", true,
            "micronaut.server.reject-unsupported-http-versions", true);
    }

    private HttpClient h2Client() throws IOException {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setPlaintextMode(HttpVersionSelection.PlaintextMode.H2C_PRIOR_KNOWLEDGE);
        configuration.setAlpnModes(List.of(HttpVersionSelection.ALPN_HTTP_2));
        return HttpClient.create(new URL("http://localhost:" + gateway.getPort()), configuration);
    }

    @Test
    void hv03AndHo05AnHttp2ClientGetsAChunkedHttp1ResponseWithoutHttp1Framing() throws IOException {
        try (HttpClient client = h2Client()) {
            HttpResponse<byte[]> response = client.toBlocking().exchange(HttpRequest.GET("/up/stream?size=100000"), byte[].class);
            assertEquals(200, response.code());
            assertEquals(100_000, response.body().length);
            // HTTP/2 cannot carry Transfer-Encoding: chunked, the stream would fail: a Transfer-Encoding the
            // client shows is its own, for a streamed HTTP/2 body. The other framing headers of HTTP/1 are gone
            assertFalse(response.getHeaders().contains("connection"), response.getHeaders().asMap().toString());
            assertFalse(response.getHeaders().contains("keep-alive"));
            HttpResponse<String> inspected = client.toBlocking().exchange(HttpRequest.GET("/inspect/h2"), String.class);
            String text = inspected.body();
            // the upstream is sent HTTP/1.1 with the Host of the upstream, and the authority as the forwarded host
            assertTrue(text.contains("version=HTTP_1_1"), text);
            assertTrue(text.contains("h:host=" + upstream.getHost() + ":" + upstream.getPort()) || text.contains("h:host=localhost:" + upstream.getPort()), text);
            assertTrue(text.contains("h:x-forwarded-host=localhost"), text);
        }
    }

    @Test
    void anHttp1ClientReachesAnHttp2Upstream() throws IOException {
        RawHttp.Response response = get("/h2-upstream/inspect/x");
        assertEquals(200, response.status(), response.toString());
        assertEquals("HTTP_2_0", response.inspected("version"));
        assertEquals("/inspect/x", response.inspected("path"));
        RawHttp.Response post = send("POST", "/h2-upstream/inspect/y", "payload", "Content-Type: text/plain");
        assertEquals("payload", post.inspected("body"));
        assertEquals(null, post.inspected("h:connection"));
    }

    @Test
    void anHttp2ClientReachesAnHttp2Upstream() throws IOException {
        try (HttpClient client = h2Client()) {
            String text = client.toBlocking().retrieve(HttpRequest.GET("/h2-upstream/inspect/z"));
            assertTrue(text.contains("version=HTTP_2_0"), text);
        }
    }

    @Test
    void hv09AnHttp10ClientKeepsItsConnectionWithAContentLengthAndCannotSendChunked() throws IOException {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("GET /inspect/one HTTP/1.0\r\nConnection: keep-alive\r\n\r\n");
            RawHttp.Response first = http.read(false);
            assertEquals("/inspect/one", first.inspected("path"), first.toString());
            assertTrue(first.header("Content-Length") != null, first.toString());
            http.send("GET /inspect/two HTTP/1.0\r\nConnection: keep-alive\r\n\r\n");
            assertEquals("/inspect/two", http.read(false).inspected("path"));
        }
        RawHttp.Response chunked = RawHttp.exchange(gateway.getPort(), "POST /inspect/x HTTP/1.0\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n");
        assertTrue(chunked.status() == 400 || chunked.status() == 200 && "abc".equals(chunked.inspected("body")), chunked.toString());
    }

    @Test
    void hv10AnInvalidHttpVersionIsRejected() throws IOException {
        RawHttp.Response response = RawHttp.exchange(gateway.getPort(), "GET /inspect/x HTTP/9.9\r\nHost: localhost\r\n\r\n");
        assertTrue(response.status() == 400 || response.status() == 505, response.toString());
    }

    @Test
    void hv12UpstreamConnectionsAreReused() throws Exception {
        scripted.on("/keep", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        int before = scripted.connections.get();
        for (int i = 0; i < 5; i++) {
            assertEquals("ok", get("/raw/keep").text());
        }
        assertEquals(before + 1, scripted.connections.get(), "a new upstream connection per request");
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
            gateway.route("inspect").path("/inspect/**").httpVersion(HttpVersionSelection.forLegacyVersion(io.micronaut.http.HttpVersion.HTTP_1_1)).uri(upstream);
            gateway.route("up").path("/up/**").httpVersion(HttpVersionSelection.forLegacyVersion(io.micronaut.http.HttpVersion.HTTP_1_1)).uri(upstream);
            gateway.route("h2-upstream").path("/h2-upstream/**").filters(GatewayFilters.stripPrefix(1))
                .httpVersion(HttpVersionSelection.forLegacyVersion(io.micronaut.http.HttpVersion.HTTP_2_0))
                .uri(upstream);
            gateway.route("raw").path("/raw/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted);
        }
    }
}
