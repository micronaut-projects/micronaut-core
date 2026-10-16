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
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.builder.ValueMatcher;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The responses given as values to direct routes on the Netty server, prepared once: the
 * conditions of the routes choose between them, e.g. a {@code 304} for the current version, and
 * the server writes them for each request with its own headers and the framing of a
 * {@code GET}, a {@code HEAD} or a {@code 304}, and releases their buffers when it stops.
 */
class DirectRoutePreparedTest {
    private static final String SPEC_NAME = "DirectRoutePreparedTest";

    @Test
    void theConditionsChooseBetweenThePreparedResponsesWhichTheServerWritesForEachRequest() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.server-header", "micronaut-test"));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             Socket socket = new Socket("localhost", server.getPort())) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            for (int i = 0; i < 3; i++) {
                // the same prepared response for every request on the connection
                out.write(request("GET", "/version", null));
                String ok = DirectRouteTest.readResponse(in, false);
                assertTrue(ok.startsWith("http/1.1 200 ok"), ok);
                assertTrue(ok.contains("etag: \"v1\""), ok);
                assertTrue(ok.contains("content-type: text/plain"), ok);
                assertTrue(ok.contains("content-length: 7"), ok);
                assertTrue(ok.contains("date: "), ok);
                assertTrue(ok.contains("server: micronaut-test"), ok);
                assertTrue(ok.endsWith("\r\n\r\ncurrent"), ok);
            }

            out.write(request("GET", "/version", "\"v0\", W/\"v1\""));
            String notModified = DirectRouteTest.readResponse(in, false);
            assertTrue(notModified.startsWith("http/1.1 304 not modified"), notModified);
            assertTrue(notModified.contains("etag: \"v1\""), notModified);
            assertFalse(notModified.contains("content-length"), notModified);

            // the length of the GET response, and no body
            out.write(request("HEAD", "/version", null));
            String head = DirectRouteTest.readResponse(in, true);
            assertTrue(head.startsWith("http/1.1 200 ok"), head);
            assertTrue(head.contains("content-length: 7"), head);

            // the server added its headers to each response, not to the prepared one
            out.write(request("GET", "/version", null));
            String again = DirectRouteTest.readResponse(in, false);
            assertEquals(1, again.split("date: ", -1).length - 1, again);
            assertEquals(1, again.split("server: ", -1).length - 1, again);
            assertEquals(1, again.split("content-length: ", -1).length - 1, again);
        }
    }

    private static byte[] request(String method, String path, String ifNoneMatch) {
        String header = ifNoneMatch == null ? "" : "If-None-Match: " + ifNoneMatch + "\r\n";
        return (method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n" + header + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/version")
                .where(RouteCondition.header(HttpHeaders.IF_NONE_MATCH, ValueMatcher.entityTag("\"v1\"")))
                .order(-1)
                .respond(HttpResponse.notModified().header(HttpHeaders.ETAG, "\"v1\""));
            routes.GET("/version", HttpResponse.ok("current").contentType(MediaType.TEXT_PLAIN_TYPE).header(HttpHeaders.ETAG, "\"v1\""));
        }
    }
}
