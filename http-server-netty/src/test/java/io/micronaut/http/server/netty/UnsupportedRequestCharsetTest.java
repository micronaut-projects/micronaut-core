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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A String body sent with a charset the server does not support is a client error, not a 500.
 */
class UnsupportedRequestCharsetTest {
    private static final String SPEC_NAME = "UnsupportedRequestCharsetTest";

    @ParameterizedTest
    @ValueSource(strings = {"/charset/controller", "/charset/route"})
    void unsupportedCharsetIsABadRequest(String path) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            assertTrue(post(server, path, "text/plain").startsWith("HTTP/1.1 200 "));
            String response = post(server, path, "text/plain; charset=nope");
            assertTrue(response.startsWith("HTTP/1.1 400 "), response);
        }
    }

    private static String post(EmbeddedServer server, String path, String contentType) throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST " + path + " HTTP/1.1\r\n"
                + "Host: " + server.getHost() + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
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
            routes.POST("/charset/route")
                .consumesAll()
                .body(String.class)
                .handle((request, pathVariables, body) -> HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    @Controller("/charset/controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CharsetController {
        @Post(consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String post(@Body String body) {
            return body;
        }
    }
}
