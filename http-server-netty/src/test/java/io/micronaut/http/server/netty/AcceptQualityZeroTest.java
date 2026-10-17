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
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An accepted media range of the quality 0 is not acceptable, RFC 9110 12.4.2.
 */
class AcceptQualityZeroTest {
    private static final String SPEC_NAME = "AcceptQualityZeroTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        ctx.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller", "route"})
    void acceptedTypeIsAcceptable(String kind) throws Exception {
        assertStatus(get("/q0/" + kind + "/plain", "text/plain"), 200);
        assertStatus(get("/q0/" + kind + "/plain", "text/plain;q=0.5"), 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller", "route"})
    void qualityZeroIsNotAcceptable(String kind) throws Exception {
        assertStatus(get("/q0/" + kind + "/plain", "text/plain;q=0"), 406);
        assertStatus(get("/q0/" + kind + "/plain", "*/*;q=0"), 406);
        assertStatus(get("/q0/" + kind + "/plain", "text/plain;q=0, */*"), 406);
        assertStatus(get("/q0/" + kind + "/plain", "text/plain;q=0, application/json"), 406);
        assertStatus(get("/q0/" + kind + "/both", "*/*;q=0"), 406);
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller", "route"})
    void qualityZeroTypeIsNotChosen(String kind) throws Exception {
        String response = get("/q0/" + kind + "/both", "text/plain;q=0, application/json");
        assertStatus(response, 200);
        assertContentType(response, "application/json");

        response = get("/q0/" + kind + "/both", "text/plain;q=0, */*");
        assertStatus(response, 200);
        assertContentType(response, "application/json");

        response = get("/q0/" + kind + "/both", "*/*;q=0, application/json");
        assertStatus(response, 200);
        assertContentType(response, "application/json");
    }

    private static void assertStatus(String response, int status) {
        assertTrue(response.startsWith("HTTP/1.1 " + status + " "), response);
    }

    private static void assertContentType(String response, String contentType) {
        assertTrue(response.toLowerCase(Locale.ROOT).contains("\r\ncontent-type: " + contentType), response);
    }

    private static String get(String path, String accept) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\n"
                + "Host: " + server.getHost() + "\r\n"
                + "Accept: " + accept + "\r\n"
                + "Connection: close\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            in.transferTo(response);
            return response.toString(StandardCharsets.UTF_8);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/q0/route/plain")
                .produces(MediaType.TEXT_PLAIN_TYPE)
                .handle((request, pathVariables) -> HttpResponse.ok("hello"));
            routes.GET("/q0/route/both")
                .produces(MediaType.TEXT_PLAIN_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .handle((request, pathVariables) -> HttpResponse.ok("hello"));
        }
    }

    @Controller("/q0/controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class QualityController {
        @Get(value = "/plain", produces = MediaType.TEXT_PLAIN)
        String plain() {
            return "hello";
        }

        @Get(value = "/both", produces = {MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
        String both() {
            return "hello";
        }
    }
}
