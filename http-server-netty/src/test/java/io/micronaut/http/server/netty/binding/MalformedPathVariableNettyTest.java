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
package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Netty runtime passes a malformed percent-encoded path to the router, so a local error
 * handler answers it, and it decodes a malformed query value leniently. A runtime with a strict
 * URI validation rejects both before the router, so these are not part of the TCK.
 */
class MalformedPathVariableNettyTest {
    private static final String SPEC_NAME = "MalformedPathVariableNettyTest";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static ApplicationContext ctx;
    private static int port;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        port = ctx.getBean(EmbeddedServer.class).start().getPort();
    }

    @AfterAll
    static void stop() {
        ctx.close();
    }

    @Test
    void aLocalErrorHandlerAnswersAMalformedVariable() throws Exception {
        java.net.http.HttpResponse<String> response = get(port, "/malformed-netty/handled/%C0%AE");
        assertEquals(400, response.statusCode());
        assertEquals("handled", response.body());
    }

    @Test
    void aMalformedQueryValueIsNotAServerError() throws Exception {
        // query values are decoded by the runtime, which replaces a malformed sequence
        assertEquals(200, get(port, "/malformed-netty/query?q=%C0%AE").statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%C0%AE", "%FF", "%E2%82", "a+%FF"})
    void aMalformedVariableIsABadRequestWhenAPlusIsASpace(String malformed) throws Exception {
        // the decoding before 5.3 replaced a malformed sequence instead of rejecting it
        try (ApplicationContext plusAsSpace = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1,
            "micronaut.router.path-variable-plus-as-space", true))) {
            int plusPort = plusAsSpace.getBean(EmbeddedServer.class).start().getPort();
            assertEquals(400, get(plusPort, "/malformed-netty/items/" + malformed).statusCode());
            java.net.http.HttpResponse<String> decoded = get(plusPort, "/malformed-netty/items/a+b%20c%2B%E2%82%AC");
            assertEquals(200, decoded.statusCode());
            assertEquals("item a b c+€", decoded.body());
        }
    }

    private static java.net.http.HttpResponse<String> get(int port, String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(), BodyHandlers.ofString());
    }

    @Controller("/malformed-netty")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MalformedController {
        @Get("/items/{id:[^/]+}")
        @Produces(MediaType.TEXT_PLAIN)
        String item(String id) {
            return "item " + id;
        }

        @Get("/query")
        @Produces(MediaType.TEXT_PLAIN)
        String query(@QueryValue String q) {
            return "query " + q.length();
        }

        @Get("/handled/{id}")
        @Produces(MediaType.TEXT_PLAIN)
        String handled(String id) {
            return "handled " + id;
        }

        @Error(HttpStatusException.class)
        HttpResponse<String> badRequest(HttpStatusException e) {
            return HttpResponse.<String>status(e.getStatus()).body("handled").contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
