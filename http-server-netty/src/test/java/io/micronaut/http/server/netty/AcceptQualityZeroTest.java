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
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

    @ParameterizedTest
    @ValueSource(strings = {"controller", "route"})
    void rejectedPreferredTypeKeepsTheConsumesDisambiguation(String kind) throws Exception {
        String response = post("/q0/" + kind + "/consumes", "application/json", "text/html;q=0, */*", "{}");
        assertStatus(response, 200);
        assertTrue(response.endsWith("json"), response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller", "route"})
    void parametersMakeARangeMoreSpecific(String kind) throws Exception {
        assertStatus(get("/q0/" + kind + "/flowed", "text/plain;format=flowed, text/plain;q=0"), 200);
        assertStatus(get("/q0/" + kind + "/flowed", "text/plain;format=flowed;q=0, text/plain"), 406);
        assertStatus(get("/q0/" + kind + "/flowed", "text/plain;format=fixed, text/plain;q=0"), 406);
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller/all", "route/all", "controller/default"})
    void aRouteThatProducesAllTypesRejectsWhenNoTypeIsAcceptable(String path) throws Exception {
        assertStatus(get("/q0/" + path, "*/*"), 200);
        assertStatus(get("/q0/" + path, "*/*;q=0"), 406);
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller/all", "route/all"})
    void aRouteThatProducesAllTypesChoosesAnAcceptableType(String path) throws Exception {
        String response = get("/q0/" + path, "application/json;q=0, text/plain");
        assertStatus(response, 200);
        assertContentType(response, "text/plain");
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller/all", "route/all"})
    void aRouteThatProducesAllTypesDoesNotAnswerInARejectedType(String path) throws Exception {
        String response = get("/q0/" + path, "application/json;q=0, text/plain");
        assertStatus(response, 200);
        assertContentType(response, "text/plain");
        assertStatus(get("/q0/" + path, "application/json;q=0, */*"), 406);
    }

    @Test
    void anUnannotatedControllerMethodProducesTheJsonOfTheController() throws Exception {
        // @Controller produces JSON by default: as for Accept: text/plain, the route is not acceptable
        assertStatus(get("/q0/controller/default", "text/plain"), 406);
        assertStatus(get("/q0/controller/default", "application/json;q=0, text/plain"), 406);
        assertStatus(get("/q0/controller/default", "application/json;q=0, */*"), 406);
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller", "route"})
    void parameterizedVariantsKeepThePreferenceOfTheClient(String kind) throws Exception {
        String response = get("/q0/" + kind + "/variants", "text/plain;format=fixed, text/plain;format=flowed;q=0.5, application/json;q=0");
        assertStatus(response, 200);
        assertContentType(response, "text/plain;format=fixed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller/all-object", "route/all-object"})
    void aRouteThatProducesAllTypesKeepsJsonWhenItIsAcceptable(String path) throws Exception {
        // the body is only encodable as JSON: the preferred text/plain is not chosen over the acceptable JSON
        String response = get("/q0/" + path, "text/plain, application/json;q=0.5, image/png;q=0");
        assertStatus(response, 200);
        assertContentType(response, "application/json");
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller/all-pojo", "route/all-pojo", "controller/all-response"})
    void aBodyWithoutAWriterForTheOnlyAcceptableTypeIsNotAcceptable(String path) throws Exception {
        assertStatus(get("/q0/" + path, "application/json;q=0, text/plain"), 406);
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller/all-pojo", "route/all-pojo"})
    void aBodyIsWrittenInTheNextAcceptableTypeWithAWriter(String path) throws Exception {
        String response = get("/q0/" + path, "application/json;q=0.1, text/plain");
        assertStatus(response, 200);
        assertContentType(response, "application/json");
        assertTrue(response.endsWith("{\"greeting\":\"hello\"}"), response);
        response = get("/q0/" + path, "text/plain, image/png;q=0, application/json;q=0.1");
        assertStatus(response, 200);
        assertContentType(response, "application/json");
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller/all", "route/all"})
    void aTextBodyIsWrittenInTheAcceptableTextType(String path) throws Exception {
        String response = get("/q0/" + path, "application/json;q=0, text/plain");
        assertStatus(response, 200);
        assertContentType(response, "text/plain");
        assertTrue(response.endsWith("hello"), response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"controller", "route"})
    void theQualityParameterNameIsCaseInsensitive(String kind) throws Exception {
        assertStatus(get("/q0/" + kind + "/plain", "text/plain;Q=0"), 406);
        assertStatus(get("/q0/" + kind + "/plain", "text/plain;Q=0.000"), 406);
        assertStatus(get("/q0/" + kind + "/plain", "text/plain;Q=0.5"), 200);
    }

    @Test
    void anHtmlErrorAcceptedThroughAWildcardIsPreferredOverARejectedJson() throws Exception {
        String response = get("/q0/missing", "text/*, application/json;q=0");
        assertStatus(response, 404);
        assertContentType(response, "text/html");
    }

    @Test
    void aRejectedJsonTypeDoesNotSuppressAnHtmlError() throws Exception {
        String response = get("/q0/missing", "text/html, application/json;q=0");
        assertStatus(response, 404);
        assertContentType(response, "text/html");
    }

    private static void assertStatus(String response, int status) {
        assertTrue(response.startsWith("HTTP/1.1 " + status + " "), response);
    }

    private static void assertContentType(String response, String contentType) {
        assertTrue(response.toLowerCase(Locale.ROOT).contains("\r\ncontent-type: " + contentType), response);
    }

    private static String get(String path, String accept) throws Exception {
        return exchange("GET " + path + " HTTP/1.1\r\n"
            + "Host: " + server.getHost() + "\r\n"
            + "Accept: " + accept + "\r\n"
            + "Connection: close\r\n"
            + "\r\n");
    }

    private static String post(String path, String contentType, String accept, String body) throws Exception {
        return exchange("POST " + path + " HTTP/1.1\r\n"
            + "Host: " + server.getHost() + "\r\n"
            + "Content-Type: " + contentType + "\r\n"
            + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
            + "Accept: " + accept + "\r\n"
            + "Connection: close\r\n"
            + "\r\n" + body);
    }

    private static String exchange(String request) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
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
            routes.GET("/q0/route/flowed")
                .produces(MediaType.of("text/plain;format=flowed"))
                .handle((request, pathVariables) -> HttpResponse.ok("hello"));
            routes.GET("/q0/route/variants")
                .produces(MediaType.of("text/plain;format=flowed"), MediaType.of("text/plain;format=fixed"))
                .handle((request, pathVariables) -> HttpResponse.ok("hello"));
            routes.GET("/q0/route/all-object")
                .produces(MediaType.ALL_TYPE)
                .handle((request, pathVariables) -> HttpResponse.ok(Map.of("greeting", "hello")));
            routes.GET("/q0/route/all-pojo")
                .produces(MediaType.ALL_TYPE)
                .handle((request, pathVariables) -> HttpResponse.ok(new Greeting("hello")));
            routes.GET("/q0/route/all")
                .produces(MediaType.ALL_TYPE)
                .handle((request, pathVariables) -> HttpResponse.ok("hello"));
            routes.POST("/q0/route/consumes")
                .consumes(MediaType.APPLICATION_JSON_TYPE)
                .produces(MediaType.APPLICATION_JSON_TYPE)
                .body(String.class)
                .handle((request, pathVariables, body) -> HttpResponse.ok("json"));
            routes.POST("/q0/route/consumes")
                .consumesAll()
                .produces(MediaType.APPLICATION_JSON_TYPE)
                .body(String.class)
                .handle((request, pathVariables, body) -> HttpResponse.ok("any"));
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

        @Get(value = "/flowed", produces = "text/plain;format=flowed")
        String flowed() {
            return "hello";
        }

        @Get(value = "/variants", produces = {"text/plain;format=flowed", "text/plain;format=fixed"})
        String variants() {
            return "hello";
        }

        @Get(value = "/all-object", produces = MediaType.ALL)
        Map<String, String> allObject() {
            return Map.of("greeting", "hello");
        }

        @Get(value = "/all", produces = MediaType.ALL)
        String all() {
            return "hello";
        }

        @Get(value = "/all-pojo", produces = MediaType.ALL)
        Greeting allPojo() {
            return new Greeting("hello");
        }

        @Get(value = "/all-response", produces = MediaType.ALL)
        HttpResponse<Greeting> allResponse() {
            return HttpResponse.ok(new Greeting("hello"));
        }

        @Get("/default")
        String unannotated() {
            return "hello";
        }

        @Post(value = "/consumes", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
        String consumesJson(@Body String body) {
            return "json";
        }

        @Post(value = "/consumes", consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
        String consumesAll(@Body String body) {
            return "any";
        }
    }

    @Introspected
    record Greeting(String greeting) {
    }
}
