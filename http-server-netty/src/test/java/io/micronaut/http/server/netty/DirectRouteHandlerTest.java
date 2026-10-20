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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.ValueMatcher;
import io.netty.util.AsciiString;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct routes that answer by commands on the exchange of the request: the handler checks the
 * request and writes the response, without a request or a response object.
 */
class DirectRouteHandlerTest {
    private static final String SPEC_NAME = "DirectRouteHandlerTest";

    @Test
    void aHandlerChecksTheRequestAndWritesTheResponse() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.server-header", "micronaut-test"));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             Socket socket = new Socket("localhost", server.getPort())) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write(request("GET", "/handled/version", null));
            String ok = DirectRouteTest.readResponse(in, false);
            assertTrue(ok.startsWith("http/1.1 200 ok"), ok);
            assertTrue(ok.contains("etag: \"v1\""), ok);
            assertTrue(ok.contains("content-type: text/plain"), ok);
            assertTrue(ok.contains("content-length: 7"), ok);
            // the server adds no Date header to the response of a handler
            assertFalse(ok.contains("date: "), ok);
            assertTrue(ok.contains("server: micronaut-test"), ok);
            assertTrue(ok.endsWith("\r\n\r\ncurrent"), ok);

            out.write(request("GET", "/handled/version", "W/\"v1\""));
            String notModified = DirectRouteTest.readResponse(in, false);
            assertTrue(notModified.startsWith("http/1.1 304 not modified"), notModified);
            assertTrue(notModified.contains("etag: \"v1\""), notModified);
            assertFalse(notModified.contains("content-length"), notModified);

            out.write(request("HEAD", "/handled/version", null));
            String head = DirectRouteTest.readResponse(in, true);
            assertTrue(head.startsWith("http/1.1 200 ok"), head);
            assertTrue(head.contains("content-length: 7"), head);

            // the Date header the handler writes
            out.write(request("GET", "/handled/dated", null));
            String dated = DirectRouteTest.readResponse(in, false);
            assertTrue(dated.contains("date: thu, 01 jan 2026 00:00:00 gmt\r\n"), dated);
            assertTrue(dated.indexOf("date: ") == dated.lastIndexOf("date: "), dated);

            // a text body, encoded as UTF-8, and a path variable
            out.write(request("GET", "/handled/greetings/world", null));
            String greeting = DirectRouteTest.readResponse(in, false);
            assertTrue(greeting.contains("content-length: 14"), greeting);
            assertTrue(greeting.endsWith("\r\n\r\nHello world é"), greeting);

            // declined: whatever it wrote, the ordinary route answers
            out.write(request("GET", "/handled/declined", null));
            String declined = DirectRouteTest.readResponse(in, false);
            assertTrue(declined.startsWith("http/1.1 200 ok"), declined);
            assertTrue(declined.endsWith("\r\n\r\nordinary"), declined);
            assertFalse(declined.contains("x-written"), declined);

            // a handler that throws is answered with 500, whatever it wrote
            out.write(request("GET", "/handled/failed", null));
            String failed = DirectRouteTest.readResponse(in, false);
            assertTrue(failed.startsWith("http/1.1 500"), failed);
            assertFalse(failed.contains("x-written"), failed);
        }
    }

    private static byte[] request(String method, String path, String ifNoneMatch) {
        String header = ifNoneMatch == null ? "" : "If-None-Match: " + ifNoneMatch + "\r\n";
        return (method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n" + header + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpDirectRoutes, HttpRoutes {
        private static final AsciiString ETAG = AsciiString.cached("ETag");
        private static final AsciiString VERSION = AsciiString.cached("\"v1\"");
        private static final ValueMatcher CURRENT = ValueMatcher.entityTag("\"v1\"");
        private static final byte[] BODY = "current".getBytes(StandardCharsets.UTF_8);

        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/handled/version").handle((exchange, pathVariables) -> exchange.checkHeader(HttpHeaders.IF_NONE_MATCH, CURRENT)
                ? exchange.writeStatus(304).writeHeader(ETAG, VERSION).end()
                : exchange.writeHeader(ETAG, VERSION).writeHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN).writeBody(BODY).end());
            routes.GET("/handled/dated").handle((exchange, pathVariables) ->
                exchange.writeHeader(HttpHeaders.DATE, "Thu, 01 Jan 2026 00:00:00 GMT").writeBody(BODY).end());
            routes.GET("/handled/greetings/{name}").handle((exchange, pathVariables) ->
                exchange.writeBody("Hello " + pathVariables.getString("name") + " é").end());
            routes.GET("/handled/declined").handle((exchange, pathVariables) -> {
                exchange.writeHeader("X-Written", "yes");
                return false;
            });
            routes.GET("/handled/failed").handle((exchange, pathVariables) -> {
                exchange.writeHeader("X-Written", "yes");
                throw new IllegalStateException("failed");
            });
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/handled/declined", (request, pathVariables) -> HttpResponse.ok("ordinary").contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
