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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The responses of direct routes at the edges: an informational status is not a final
 * response, a body written by a message body writer, e.g. a character, is encoded with the charset of its own
 * response, and the query of an absolute-form target is decoded like an ordinary request
 * decodes it.
 */
class DirectRouteResponseEdgeCaseTest {
    private static final String SPEC_NAME = "DirectRouteResponseEdgeCaseTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void anInformationalStatusIsAnsweredWith500() throws IOException {
        for (String target : new String[] {"/edge/early-hints", "/edge/early-hints-body", "/edge/early-hints-async"}) {
            String response = text(exchange(get(target)));
            assertTrue(response.startsWith("HTTP/1.1 500 "), target + ": " + response);
        }
    }

    @Test
    void aWrittenBodyIsEncodedWithTheCharsetOfItsResponse() throws IOException {
        assertArrayEquals("é".getBytes(StandardCharsets.UTF_8), body(exchange(get("/edge/label/utf8"))));
        assertArrayEquals("é".getBytes(StandardCharsets.ISO_8859_1), body(exchange(get("/edge/label/latin1"))));
        assertArrayEquals("é".getBytes(StandardCharsets.UTF_8), body(exchange(get("/edge/label/utf8"))));
    }

    @Test
    void theQueryOfAnAbsoluteFormTargetIsDecodedLikeAnOrdinaryRequest() throws IOException {
        // the ordinary request decodes the query of the URI it creates: the encoded delimiter separates
        String ordinary = text(exchange(get("http://localhost/edge/ordinary?x=a%26admin=true")));
        assertTrue(ordinary.endsWith("x=a admin=true"), ordinary);
        String direct = text(exchange(get("http://localhost/edge/direct?x=a%26admin=true")));
        assertTrue(direct.endsWith("x=a admin=true"), direct);
        // a query condition of a direct route decodes it the same
        String admin = text(exchange(get("http://localhost/edge/admin?x=a%26admin=true")));
        assertTrue(admin.endsWith("admin"), admin);
        // a target in origin form: the delimiter stays encoded, for both
        String origin = text(exchange(get("/edge/direct?x=a%26admin=true")));
        assertTrue(origin.endsWith("x=a&admin=true admin=null"), origin);
        String ordinaryOrigin = text(exchange(get("/edge/ordinary?x=a%26admin=true")));
        assertTrue(ordinaryOrigin.endsWith("x=a&admin=true admin=null"), ordinaryOrigin);
    }

    private static String get(String target) {
        return "GET " + target + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
    }

    private static byte[] exchange(String request) throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private static String text(byte[] response) {
        return new String(response, StandardCharsets.ISO_8859_1);
    }

    private static byte[] body(byte[] response) {
        String text = text(response);
        assertTrue(text.startsWith("HTTP/1.1 200 "), text);
        int start = text.indexOf("\r\n\r\n") + 4;
        byte[] body = new byte[response.length - start];
        System.arraycopy(response, start, body, 0, body.length);
        return body;
    }

    @Controller("/edge/ordinary")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OrdinaryController {
        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String query(@QueryValue @Nullable String x, @QueryValue @Nullable String admin) {
            return "x=" + x + " admin=" + admin;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/edge/early-hints").respond(direct -> HttpResponse.status(HttpStatus.EARLY_HINTS));
            routes.GET("/edge/early-hints-body").respond(direct -> HttpResponse.status(HttpStatus.EARLY_HINTS).body("hint"));
            routes.GET("/edge/early-hints-async").respondAsync(direct -> CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.EARLY_HINTS)));
            routes.GET("/edge/label/utf8").executeOn(TaskExecutors.BLOCKING).respond(direct -> HttpResponse.ok(Character.valueOf('é'))
                .contentType(MediaType.of("text/plain;charset=UTF-8")));
            routes.GET("/edge/label/latin1").executeOn(TaskExecutors.BLOCKING).respond(direct -> HttpResponse.ok(Character.valueOf('é'))
                .contentType(MediaType.of("text/plain;charset=ISO-8859-1")));
            routes.GET("/edge/admin").where(RequestCondition.query("admin", "true")).respond(HttpResponse.ok("admin"));
            routes.GET("/edge/direct").respond(direct -> HttpResponse.ok("x=" + direct.request().queryParameter("x")
                + " admin=" + direct.request().queryParameter("admin")).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
