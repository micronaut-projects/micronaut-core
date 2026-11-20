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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The headers of relayed requests and responses: hop-by-hop, end-to-end, forwarded and
 * {@code Host}, with the default configuration, which trusts no proxy.
 */
class HeadersTest extends GatewayTestBase {

    static final String SPEC = "HeadersTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Test
    void hh01TheHopByHopHeadersOfARequestAreRemoved() throws IOException {
        RawHttp.Response response = get("/inspect/x",
            "Keep-Alive: timeout=5",
            "Proxy-Authorization: Basic Zm9vOmJhcg==",
            "Proxy-Connection: keep-alive",
            "TE: gzip",
            "Trailer: X-Checksum",
            "Upgrade: foo/1",
            "X-End-To-End: kept");
        assertEquals(200, response.status(), response.toString());
        for (String name : List.of("keep-alive", "proxy-authorization", "proxy-connection", "te", "trailer", "upgrade")) {
            assertNull(response.inspected("h:" + name), name + " " + response);
        }
        assertEquals("kept", response.inspected("h:x-end-to-end"));
    }

    @Test
    void theRealIpOfAClientThatIsNotATrustedProxyIsNotRelayed() throws IOException {
        RawHttp.Response response = get("/inspect/x", "X-Real-IP: 6.6.6.6", "X-Forwarded-For: 6.6.6.6");
        assertEquals(200, response.status(), response.toString());
        assertNull(response.inspected("h:x-real-ip"), response.toString());
    }

    @Test
    void hh01TheHopByHopHeadersOfAResponseAreRemoved() throws IOException {
        RawHttp.Response response = get("/up/headers");
        assertEquals(200, response.status());
        assertNull(response.header("Proxy-Authenticate"));
        assertNull(response.header("Keep-Alive"));
    }

    @Test
    void hh02AndHh03HeaderNamesAreCaseInsensitiveAndTheHeadersNamedInConnectionAreRemoved() throws IOException {
        RawHttp.Response response = RawHttp.exchange(gateway.getPort(), "GET /inspect/x HTTP/1.1\r\nHost: localhost\r\n"
            + "CONNECTION: close, X-Foo\r\nX-Foo: secret\r\nkeep-ALIVE: 5\r\nX-Bar: kept\r\n\r\n");
        assertNull(response.inspected("h:x-foo"), response.toString());
        assertNull(response.inspected("h:keep-alive"));
        assertEquals("kept", response.inspected("h:x-bar"));
    }

    @Test
    void hh04HeadersNamedInConnectionDoNotRemoveTheHeadersTheGatewayAdds() throws IOException {
        RawHttp.Response response = get("/inspect/x", "Connection: X-Forwarded-For, X-Forwarded-Host, Forwarded");
        assertEquals("127.0.0.1", response.inspected("h:x-forwarded-for"), response.toString());
        assertTrue(response.inspected("h:forwarded").startsWith("for=127.0.0.1"), response.toString());
        assertTrue(response.inspected("h:x-forwarded-host").startsWith("localhost"), response.toString());
    }

    @Test
    void hh05EndToEndHeadersAreKeptWithTheirDuplicatesInOrderAndTheirCase() throws Exception {
        RawHttp.Response response = get("/inspect/x", "X-Multi: 1", "X-Multi: 2", "X-Multi: 3", "X-Unknown-Thing: v");
        assertEquals(List.of("1", "2", "3"), response.inspected().get("h:x-multi"));
        assertEquals("v", response.inspected("h:x-unknown-thing"));
        scripted.on("/case", (request, in, out, socket) -> {
            String line = request.headerLines().stream().filter(l -> l.toLowerCase().startsWith("x-mixed-case")).findFirst().orElse("none");
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + line.length() + "\r\nX-Upstream-CaSe: yes\r\n\r\n" + line);
            return true;
        });
        RawHttp.Response cased = get("/raw/case", "X-MiXeD-Case: v");
        assertEquals("X-MiXeD-Case: v", cased.text());
        assertTrue(cased.headers().stream().anyMatch(h -> h.getKey().equals("X-Upstream-CaSe")), cased.toString());
    }

    @Test
    void hh06SetCookieHeadersStaySeparateAndSeveralCookieHeadersAreAllowed() throws IOException {
        RawHttp.Response response = get("/up/cookies");
        assertEquals(2, response.headers("Set-Cookie").size(), response.toString());
        assertTrue(response.headers("Set-Cookie").get(0).contains("Expires=Wed, 21 Oct 2037"), response.toString());
        RawHttp.Response cookies = get("/inspect/x", "Cookie: a=1", "Cookie: b=2");
        assertEquals(List.of("a=1", "b=2"), cookies.inspected().get("h:cookie"), cookies.toString());
    }

    @Test
    void hh08UpgradeIsKeptOnA426AndRemovedFromOtherResponses() throws Exception {
        scripted.on("/426", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 426 Upgrade Required\r\nUpgrade: TLS/1.2, HTTP/1.1\r\nConnection: Upgrade\r\nContent-Length: 0\r\n\r\n");
            return true;
        });
        scripted.on("/200-upgrade", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nUpgrade: h2c\r\nConnection: Upgrade\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        RawHttp.Response required = get("/raw/426");
        assertEquals(426, required.status());
        assertEquals("TLS/1.2, HTTP/1.1", required.header("Upgrade"));
        // an HTTP/1.1 recipient reads Upgrade only with the connection option
        RawHttp.Response keptAlive = RawHttp.exchange(gateway.getPort(), "GET /raw/426 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        assertEquals(426, keptAlive.status());
        assertTrue(String.valueOf(keptAlive.header("Connection")).toLowerCase(java.util.Locale.ROOT).contains("upgrade"), keptAlive.toString());
        RawHttp.Response ok = get("/raw/200-upgrade");
        assertEquals(200, ok.status());
        assertNull(ok.header("Upgrade"));
    }

    @Test
    void hh09TeTrailersIsKeptAndTheOtherCodingsAreDropped() throws IOException {
        assertEquals("trailers", get("/inspect/x", "TE: trailers").inspected("h:te"));
        assertEquals("trailers", get("/inspect/x", "TE: gzip, trailers;q=0.5").inspected("h:te"));
        assertNull(get("/inspect/x", "TE: gzip").inspected("h:te"));
    }

    @Test
    void hh10ARequestWithTransferEncodingAndContentLengthIsNotRelayedWithBoth() throws IOException {
        int before = counters().get("inspect");
        RawHttp.Response response = RawHttp.exchange(gateway.getPort(), "POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
            + "Transfer-Encoding: chunked\r\nContent-Length: 3\r\n\r\n5\r\nhello\r\n0\r\n\r\n");
        if (response.status() == 200) {
            // the server dropped Content-Length and read the chunked body, as RFC 9112 allows
            assertNull(response.inspected("h:content-length"), response.toString());
            assertEquals("hello", response.inspected("body"));
        } else {
            assertEquals(400, response.status(), response.toString());
            assertEquals(before, counters().get("inspect"));
        }
        // only a Content-Length: kept
        assertEquals("5", send("POST", "/inspect/x", "hello").inspected("h:content-length"));
    }

    @Test
    void hh13HeadersTheGatewayAddsAreMergedWithThoseOfTheUpstream() throws IOException {
        RawHttp.Response response = get("/add-response/up/status/200");
        assertEquals(List.of("yes", "gw"), response.headers("X-Upstream"));
        assertEquals(List.of("gw"), get("/set-response/up/status/200").headers("X-Upstream"));
    }

    @Test
    void hh14InteriorSpacesLongAndEmptyValuesArePreserved() throws IOException {
        RawHttp.Response response = get("/up/headers");
        assertEquals("a  b   c", response.header("X-Spaces"));
        assertEquals("", response.header("X-Empty"));
        String longValue = "v".repeat(4000);
        RawHttp.Response request = get("/inspect/x", "X-Long: " + longValue, "X-Empty-Request:", "X-Spaced: a  b");
        assertEquals(longValue, request.inspected("h:x-long"));
        assertEquals("", request.inspected("h:x-empty-request"));
        assertEquals("a  b", request.inspected("h:x-spaced"));
    }

    @Test
    void fw01TheForwardedHeadersOfThisHopAreSent() throws IOException {
        RawHttp.Response response = RawHttp.exchange(gateway.getPort(), "GET /inspect/x HTTP/1.1\r\nHost: gw.example\r\nConnection: close\r\n\r\n");
        assertEquals("127.0.0.1", response.inspected("h:x-forwarded-for"));
        assertEquals("http", response.inspected("h:x-forwarded-proto"));
        assertEquals("gw.example", response.inspected("h:x-forwarded-host"));
        assertEquals("80", response.inspected("h:x-forwarded-port"));
        assertEquals("for=127.0.0.1;proto=http;host=gw.example", response.inspected("h:forwarded"));
    }

    @Test
    void fw03TheForwardedHeadersOfAnUntrustedPeerAreReplacedNotAppendedTo() throws IOException {
        RawHttp.Response response = get("/inspect/x",
            "X-Forwarded-For: 6.6.6.6",
            "X-Forwarded-Host: evil.example",
            "X-Forwarded-Proto: https",
            "X-Forwarded-Port: 443",
            "X-Forwarded-Prefix: /evil",
            "Forwarded: for=6.6.6.6;host=evil.example;proto=https");
        assertEquals("127.0.0.1", response.inspected("h:x-forwarded-for"), response.toString());
        assertTrue(response.inspected("h:x-forwarded-host").startsWith("localhost"));
        assertEquals("http", response.inspected("h:x-forwarded-proto"));
        assertEquals(String.valueOf(gateway.getPort()), response.inspected("h:x-forwarded-port"));
        assertNull(response.inspected("h:x-forwarded-prefix"));
        assertFalse(response.inspected("h:forwarded").contains("6.6.6.6"));
    }

    @Test
    void fw04AnIpv6ClientIsQuotedAndBracketedInForwarded() throws IOException {
        InetAddress loopback6;
        try {
            loopback6 = InetAddress.getByName("::1");
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress(loopback6, gateway.getPort()), 1000);
            }
        } catch (IOException e) {
            Assumptions.abort("No IPv6 loopback: " + e);
            return;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(loopback6, gateway.getPort()), 5000);
            socket.setSoTimeout(20000);
            socket.getOutputStream().write("GET /inspect/x HTTP/1.1\r\nHost: [::1]\r\nConnection: close\r\n\r\n".getBytes());
            RawHttp.Response response = RawHttp.read(socket.getInputStream(), false);
            assertEquals("for=\"[0:0:0:0:0:0:0:1]\";proto=http;host=\"[::1]\"", response.inspected("h:forwarded"), response.toString());
            assertEquals("0:0:0:0:0:0:0:1", response.inspected("h:x-forwarded-for"));
        }
    }

    @Test
    void fw08TheForwardedSchemeOfAnUpgradeIsTheSchemeOfTheConnection() throws Exception {
        scripted.on("/ws-headers", (request, in, out, socket) -> {
            String proto = String.valueOf(request.header("x-forwarded-proto"));
            ScriptedUpstream.write(out, "HTTP/1.1 403 Forbidden\r\nContent-Length: " + proto.length() + "\r\n\r\n" + proto);
            return true;
        });
        RawHttp.Response response = get("/raw/ws-headers", "Connection: Upgrade", "Upgrade: websocket",
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==", "Sec-WebSocket-Version: 13");
        assertEquals(403, response.status());
        assertEquals("http", response.text());
    }

    @Test
    void fw11AForwardedHeaderCanBeRemovedByAFilter() throws IOException {
        RawHttp.Response response = get("/no-xff/inspect/x");
        assertNull(response.inspected("h:x-forwarded-for"), response.toString());
        assertEquals("http", response.inspected("h:x-forwarded-proto"));
    }

    @Test
    void fw12UnderscoreAliasesOfTheForwardedHeadersAreDropped() throws IOException {
        RawHttp.Response response = get("/inspect/x", "X_Forwarded_For: 6.6.6.6", "X_FORWARDED_HOST: evil", "Forwarded_: x", "X_Real_IP: 6.6.6.6", "X_Other_Header: kept");
        assertNull(response.inspected("h:x_forwarded_for"), response.toString());
        assertNull(response.inspected("h:x_forwarded_host"));
        assertNull(response.inspected("h:x_real_ip"));
        assertEquals("kept", response.inspected("h:x_other_header"));
    }

    @Test
    void ho01TheHostIsTheUpstreamsUnlessPreserveHostHeader() throws IOException {
        String upstreamHost = upstream.getHost() + ":" + upstream.getPort();
        assertEquals(upstreamHost, get("/inspect/x").inspected("h:host"));
        RawHttp.Response preserved = RawHttp.exchange(gateway.getPort(), "GET /preserve/inspect/x HTTP/1.1\r\nHost: client.example:1234\r\nConnection: close\r\n\r\n");
        assertEquals("client.example:1234", preserved.inspected("h:host"), preserved.toString());
    }

    @Test
    void ho02AHostSetByAFilterWins() throws IOException {
        RawHttp.Response response = RawHttp.exchange(gateway.getPort(), "GET /sethost/inspect/x HTTP/1.1\r\nHost: client.example\r\nConnection: close\r\n\r\n");
        assertEquals("api.example.com", response.inspected("h:host"), response.toString());
    }

    @Test
    void ho03AnIpv6UpstreamAuthorityIsBracketed() throws IOException {
        try (Socket probe = new Socket()) {
            probe.connect(new InetSocketAddress(InetAddress.getByName("::1"), upstream.getPort()), 1000);
        } catch (IOException e) {
            Assumptions.abort("No IPv6 loopback: " + e);
        }
        RawHttp.Response response = get("/ipv6/inspect/x");
        assertEquals(200, response.status(), response.toString());
        assertEquals("[::1]:" + upstream.getPort(), response.inspected("h:host"));
    }

    @Test
    void rs22ResponseHeaderFiltersApplyToTheUpstreamResponse() throws IOException {
        assertEquals("*", get("/dedupe-first/up/headers").header("Access-Control-Allow-Origin"));
        assertEquals(List.of("https://gw.example"), get("/dedupe-last/up/headers").headers("Access-Control-Allow-Origin"));
        assertEquals(List.of("one", "two"), get("/dedupe-unique/up/headers").headers("X-Dup"));
        assertEquals(List.of("0ne", "two", "0ne"), get("/rewrite-response/up/headers").headers("X-Dup"));
        RawHttp.Response removed = get("/remove-response/up/headers");
        assertNull(removed.header("X-Spaces"));
        RawHttp.Response secure = get("/secure/up/headers");
        assertEquals("nosniff", secure.header("X-Content-Type-Options"));
        assertEquals("DENY", secure.header("X-Frame-Options"));
        assertTrue(secure.header("Strict-Transport-Security").startsWith("max-age="));
    }

    @Test
    void rs23RequestHeaderFiltersApplyToTheUpstreamRequest() throws IOException {
        RawHttp.Response response = get("/req-headers/inspect/x", "X-Set: client", "X-Remove: gone", "X-Maybe: client", "X-From: mapped");
        assertEquals("a", response.inspected("h:x-add"));
        assertEquals("s", response.inspected("h:x-set"));
        assertNull(response.inspected("h:x-remove"));
        assertEquals("client", response.inspected("h:x-maybe"));
        assertEquals("mapped", response.inspected("h:x-to"));
        assertEquals("gw", get("/req-headers/inspect/x").inspected("h:x-maybe"));
        assertEquals(List.of("a", "b"), get("/req-headers/inspect/x", "X-Add: b").inspected().get("h:x-add").stream().sorted().toList());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;
        private final int upstreamPort;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted) {
            this.upstream = upstream;
            this.scripted = scripted;
            this.upstreamPort = java.net.URI.create(upstream).getPort();
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("inspect").path("/inspect/**").uri(upstream);
            gateway.route("up").path("/up/**").uri(upstream);
            gateway.route("raw").path("/raw/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted);
            gateway.route("no-xff").path("/no-xff/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.removeRequestHeader("X-Forwarded-For")).uri(upstream);
            gateway.route("preserve").path("/preserve/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.preserveHostHeader()).uri(upstream);
            gateway.route("sethost").path("/sethost/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.preserveHostHeader(), GatewayFilters.setRequestHostHeader("api.example.com"))
                .uri(upstream);
            gateway.route("ipv6").path("/ipv6/**").filters(GatewayFilters.stripPrefix(1)).uri("http://[::1]:" + upstreamPort);
            gateway.route("add-response").path("/add-response/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Upstream", "gw")).uri(upstream);
            gateway.route("set-response").path("/set-response/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.setResponseHeader("X-Upstream", "gw")).uri(upstream);
            gateway.route("dedupe-first").path("/dedupe-first/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("Access-Control-Allow-Origin", "https://gw.example"),
                    GatewayFilters.dedupeResponseHeader(List.of("Access-Control-Allow-Origin"), GatewayFilters.DedupeStrategy.RETAIN_FIRST))
                .uri(upstream);
            gateway.route("dedupe-last").path("/dedupe-last/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("Access-Control-Allow-Origin", "https://gw.example"),
                    GatewayFilters.dedupeResponseHeader(List.of("Access-Control-Allow-Origin"), GatewayFilters.DedupeStrategy.RETAIN_LAST))
                .uri(upstream);
            gateway.route("dedupe-unique").path("/dedupe-unique/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.dedupeResponseHeader(List.of("X-Dup"), GatewayFilters.DedupeStrategy.RETAIN_UNIQUE))
                .uri(upstream);
            gateway.route("rewrite-response").path("/rewrite-response/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.rewriteResponseHeader("X-Dup", "o(ne)", "0$1"))
                .uri(upstream);
            gateway.route("remove-response").path("/remove-response/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.removeResponseHeader("X-Spaces"))
                .uri(upstream);
            gateway.route("secure").path("/secure/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.secureHeaders()).uri(upstream);
            gateway.route("req-headers").path("/req-headers/**")
                .filters(GatewayFilters.stripPrefix(1),
                    GatewayFilters.addRequestHeader("X-Add", "a"),
                    GatewayFilters.setRequestHeader("X-Set", "s"),
                    GatewayFilters.removeRequestHeader("X-Remove"),
                    GatewayFilters.addRequestHeaderIfNotPresent("X-Maybe", "gw"),
                    GatewayFilters.mapRequestHeader("X-From", "X-To"))
                .uri(upstream);
        }
    }
}
