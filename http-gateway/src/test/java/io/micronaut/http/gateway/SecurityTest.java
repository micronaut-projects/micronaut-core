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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security edge cases: request smuggling, path tricks, Host tricks, header injection and
 * oversized requests. The rule for paths: the router matches the raw path, and the gateway
 * relays a path only if an upstream cannot resolve it to another path; a path with a dot
 * segment, an encoded slash or backslash, a path parameter or a control character is a 400.
 */
class SecurityTest extends GatewayTestBase {

    static final String SPEC = "SecurityTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of("micronaut.server.idle-timeout", "8s");
    }

    private RawHttp.Response raw(String request) throws IOException {
        return RawHttp.exchange(gateway.getPort(), request);
    }

    private void assertRejected(String request, int... statuses) throws IOException {
        int before = counters().get("inspect");
        RawHttp.Response response;
        try {
            response = raw(request);
        } catch (IOException e) {
            // closed without a response: rejected too
            assertEquals(before, counters().get("inspect"), "the request reached the upstream");
            return;
        }
        boolean expected = false;
        for (int status : statuses) {
            expected |= response.status() == status;
        }
        assertTrue(expected, "unexpected " + response);
        assertEquals(before, counters().get("inspect"), "the request reached the upstream: " + response);
    }

    @Test
    void sec01ContentLengthAndTransferEncodingTogetherAreNeverRelayedAsBoth() throws IOException {
        RawHttp.Response response = raw("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nContent-Length: 4\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
            + "5\r\nhello\r\n0\r\n\r\n");
        if (response.status() == 200) {
            assertNull(response.inspected("h:content-length"), response.toString());
            assertEquals("hello", response.inspected("body"));
        } else {
            assertEquals(400, response.status(), response.toString());
        }
    }

    @Test
    void sec02InvalidOrConflictingContentLengthsAreRejected() throws IOException {
        assertRejected("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nContent-Length: 3\r\nContent-Length: 4\r\n\r\nabcd", 400);
        assertRejected("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nContent-Length: 3, 4\r\n\r\nabcd", 400);
        assertRejected("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nContent-Length: abc\r\n\r\nabcd", 400);
        assertRejected("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nContent-Length: -1\r\n\r\nabcd", 400);
    }

    @Disabled("pending security advisory fix")
    @Test
    void sec03InvalidTransferEncodingsAreRejected() throws IOException {
        assertRejected("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked, gzip\r\n\r\n3\r\nabc\r\n0\r\n\r\n", 400);
        assertRejected("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: gzip, chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n", 501);
        assertRejected("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked;x=y\r\n\r\n3\r\nabc\r\n0\r\n\r\n", 400, 501);
        // a malformed chunk fails the body once the relay started: the upstream never gets a complete request
        RawHttp.Response malformed = raw("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\nabc\r\n0\r\n\r\n");
        assertTrue(malformed.status() == 400 || malformed.status() == 502, malformed.toString());
    }

    @Disabled("pending security advisory fix")
    @Test
    void sec04AndSec05ChunkExtensionsAreBoundedAndTheRequestIsFramedAgain() throws Exception {
        scripted.on("/framing", (request, in, out, socket) -> {
            String te = request.header("transfer-encoding");
            String text;
            if (te != null) {
                // the chunk lines as the gateway framed them
                StringBuilder lines = new StringBuilder();
                String size;
                while (!(size = ScriptedUpstream.line(in)).startsWith("0")) {
                    lines.append(size).append(',');
                    lines.append(new String(in.readNBytes(Integer.parseInt(size.split(";")[0].strip(), 16))));
                    ScriptedUpstream.line(in);
                }
                ScriptedUpstream.line(in);
                text = "chunks " + lines + "|hello";
            } else {
                text = "length " + request.header("content-length") + "|" + new String(request.readBody(in));
            }
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + text.length() + "\r\n\r\n" + text);
            return true;
        });
        RawHttp.Response response = raw("POST /raw/framing HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
            + "5;name=value\r\nhello\r\n0\r\n\r\n");
        assertEquals(200, response.status(), response.toString());
        // the upstream gets chunks the gateway framed: no extension of the client
        assertTrue(response.text().endsWith("|hello"), response.toString());
        assertTrue(!response.text().contains("name=value"), response.toString());
        RawHttp.Response huge;
        try {
            huge = raw("POST /inspect/x HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
                + "5;" + "x".repeat(20_000) + "\r\nhello\r\n0\r\n\r\n");
            // the body fails: never relayed as a complete, empty body
            assertTrue(huge.status() != 200 || "hello".equals(huge.inspected("body")), huge.toString());
        } catch (IOException e) {
            // closed: rejected
        }
    }

    @Test
    void sec06Sec07AndPt05DotSegmentsAreRejectedAndNotRelayed() throws IOException {
        assertRejected("GET /inspect/../admin HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET /inspect/./x HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET /inspect/%2e%2e/admin HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET /inspect/%2E./admin HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET /inspect/%C0%AE%C0%AE/admin HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET /inspect/x/../../../etc/passwd HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        // a literal .... is a name, not a traversal
        assertEquals("/inspect/..../x", get("/inspect/..../x").inspected("path"));
    }

    @Test
    void sec08EncodedSlashesAndBackslashesAreRejected() throws IOException {
        assertRejected("GET /inspect/a%2Fb HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET /inspect/..%2Fadmin HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET /inspect/a%5Cb HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET /inspect/a%00b HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
    }

    @Test
    void sec09PathParametersAreRejected() throws IOException {
        assertRejected("GET /inspect;x=y HTTP/1.1\r\nHost: localhost\r\n\r\n", 400, 404);
        assertRejected("GET /inspect/..;/admin HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET /inspect/a;jsessionid=1 HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
    }

    @Test
    void sec10ADoubleEncodingIsDecodedOnceAndAnInvalidOneIsRejected() throws IOException {
        // %252e is the text %2e: a name, relayed byte for byte
        assertEquals("/inspect/%252e%252e/x", get("/inspect/%252e%252e/x").inspected("path"));
        assertRejected("GET /inspect/%GG HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
    }

    @Test
    void sec11MergedSlashesAreRelayedAsSentAndNeverAnAuthority() throws IOException {
        RawHttp.Response response = raw("GET //inspect///x HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        if (response.status() == 200) {
            assertEquals("//inspect///x", response.inspected("path"), response.toString());
        } else {
            assertTrue(response.status() == 400 || response.status() == 404, response.toString());
        }
        RawHttp.Response inner = get("/inspect//x///y");
        assertEquals("/inspect//x///y", inner.inspected("path"), inner.toString());
    }

    @Test
    void sec11bAStrippedPathNeverBecomesAnAuthority() throws IOException {
        // without the check, the stripped path //127.0.0.1:port/inspect/x is a network-path
        // reference, and the client sends the request to the authority in the path
        String authority = "127.0.0.1:" + upstream.getPort();
        assertRejected("GET /strip//" + authority + "/inspect/x HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n", 400, 502);
        assertRejected("GET //" + authority + "/inspect/x HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n", 400, 404);
        // empty segments that are not leading are still relayed as sent
        RawHttp.Response inner = get("/strip/inspect//x");
        assertEquals("/inspect//x", inner.inspected("path"), inner.toString());
    }

    @Test
    void sec12AFragmentIsRejectedOrStripped() throws IOException {
        int before = counters().get("inspect");
        RawHttp.Response response = raw("GET /inspect/x#frag HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        if (response.status() == 200) {
            assertEquals("/inspect/x", response.inspected("path"), response.toString());
            assertTrue(!response.inspected("uri").contains("#"));
        } else {
            assertEquals(400, response.status(), response.toString());
            assertEquals(before, counters().get("inspect"));
        }
    }

    @Test
    void sec13AndSec15TheUpstreamIsChosenByTheConfigurationOnly() throws IOException {
        RawHttp.Response absolute = raw("GET http://evil.example/inspect/x HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertEquals(200, absolute.status(), absolute.toString());
        assertEquals("/inspect/x", absolute.inspected("path"));
        RawHttp.Response spoofed = raw("GET /inspect/x HTTP/1.1\r\nHost: evil.example\r\nX-Forwarded-Host: evil2.example\r\nConnection: close\r\n\r\n");
        assertEquals(200, spoofed.status());
        assertEquals("127.0.0.1:" + upstream.getPort(), spoofed.inspected("h:host").replace("localhost", "127.0.0.1"));
        assertRejected("CONNECT evil.example:443 HTTP/1.1\r\nHost: evil.example:443\r\n\r\n", 400, 404, 405);
    }

    @Test
    void sec14SeveralOrInvalidHostHeadersAreRejected() throws IOException {
        assertRejected("GET /inspect/x HTTP/1.1\r\nHost: localhost\r\nHost: evil.example\r\n\r\n", 400);
        assertRejected("GET /inspect/x HTTP/1.1\r\nHost: evil.example/path\r\n\r\n", 400);
        assertRejected("GET /inspect/x HTTP/1.1\r\nHost: user@evil.example\r\n\r\n", 400);
        assertRejected("GET /inspect/x HTTP/1.1\r\nHost: a b\r\n\r\n", 400);
    }

    @Test
    void sec16LineBreaksAndNulsInHeadersAreRejected() throws IOException {
        // from a path variable into a header
        assertRejected("GET /tmpl/a%0D%0AX-Injected:%20yes HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET /tmpl/a%00b HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        // from the client
        assertRejected("GET /inspect/x HTTP/1.1\r\nHost: localhost\r\nX-Bad: a\rb\r\n\r\n", 400);
        assertRejected("GET /inspect/x HTTP/1.1\r\nHost: localhost\r\nX Bad: a\r\n\r\n", 400);
        assertRejected("GET /inspect/x HTTP/1.1\r\nHost: localhost\r\nX-Bad: a\u0000b\r\n\r\n", 400);
        // a segment variable of a header is relayed decoded when it is text
        assertEquals("a b", get("/tmpl/a%20b").inspected("h:x-segment"));
    }

    @Test
    void sec17HeadersTheGatewayAddsCannotBeRemovedThroughConnection() throws IOException {
        RawHttp.Response response = get("/tmpl/x", "Connection: X-Segment, X-Forwarded-For");
        assertEquals("x", response.inspected("h:x-segment"));
        assertEquals("127.0.0.1", response.inspected("h:x-forwarded-for"));
    }

    @Test
    void sec20OversizedHeadersAndUrisAreRejectedWithoutCallingTheUpstream() throws IOException {
        // the decoder of the server rejects them before any route: 413, as MaxHeaderSizeSpec pins it,
        // where 431 and 414 would be more precise
        assertRejected("GET /inspect/x HTTP/1.1\r\nHost: localhost\r\nX-Big: " + "b".repeat(20_000) + "\r\n\r\n", 413, 431, 400);
        assertRejected("GET /inspect/" + "u".repeat(10_000) + " HTTP/1.1\r\nHost: localhost\r\n\r\n", 413, 414, 400, 431);
    }

    @Test
    void sec21And30ProxyAuthorizationIsNotRelayedAndAuthorizationAndCookiesAre() throws IOException {
        RawHttp.Response response = get("/inspect/x", "Proxy-Authorization: Basic cHJveHk6c2VjcmV0", "Authorization: Bearer token", "Cookie: session=abc");
        assertNull(response.inspected("h:proxy-authorization"));
        assertEquals("Bearer token", response.inspected("h:authorization"));
        assertEquals("session=abc", response.inspected("h:cookie"));
    }

    @Test
    void sec25AMalformedRequestLineAndHttp09AreRejected() throws IOException {
        assertRejected("GET /inspect/x\u0001y HTTP/1.1\r\nHost: localhost\r\n\r\n", 400);
        assertRejected("GET/inspect/x HTTP/1.1\r\nHost: localhost\r\n\r\n", 400, 505);
        assertRejected("GET /inspect/x\r\n\r\n", 400, 505);
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
            gateway.route("raw").path("/raw/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted);
            gateway.route("strip").path("/strip/**").filters(GatewayFilters.stripPrefix(1)).uri(upstream);
            gateway.route("tmpl").path("/tmpl/{segment}")
                .filters(GatewayFilters.setPath("/inspect/tmpl"), GatewayFilters.addRequestHeader("X-Segment", "{segment}"))
                .uri(upstream);
        }
    }
}
