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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The media types and the executor of a group of handler routes,
 * {@link io.micronaut.web.router.builder.HttpRouteGroup#consumes},
 * {@link io.micronaut.web.router.builder.HttpRouteGroup#produces} and
 * {@link io.micronaut.web.router.builder.HttpRouteGroup#executeOn}: the routes of the group and of
 * its nested groups inherit them, like the methods of a controller inherit its {@code @Consumes},
 * {@code @Produces} and {@code @ExecuteOn}, unless the route or a nested group sets its own.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteGroupSettingsTest {
    public static final String SPEC_NAME = "HandlerRouteGroupSettingsTest";
    private static final String EXECUTOR = "group-settings";
    private static final String EXECUTOR_THREAD = "group-settings-thread";

    @Test
    void theRoutesOfAGroupConsumeWhatTheGroupConsumesUnlessTheyDeclareTheirOwn() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/group-settings/echo", "hello")
                .contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("echo hello")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.POST("/group-settings/echo", "{\"text\":\"hello\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .build());
            // the route consumes JSON instead
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/group-settings/json", "{\"text\":\"hello\"}")
                .contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("json hello")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.POST("/group-settings/json", "hello")
                .contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .build());
        }
    }

    @Test
    void theRoutesOfAGroupProduceWhatTheGroupProduces() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/group-produces/text"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("text")
                .assertResponse(response -> assertEquals(MediaType.TEXT_PLAIN_TYPE,
                    response.getContentType().map(MediaType::getName).map(MediaType::of).orElse(null)))
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/group-produces/text").accept(MediaType.APPLICATION_JSON_TYPE),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_ACCEPTABLE)
                    .build());
        }
    }

    @Test
    void aNestedGroupOverridesTheMediaTypesOfTheGroupAroundIt() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/group-produces/nested/note").accept(MediaType.APPLICATION_JSON_TYPE),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("{\"text\":\"nested\"}")
                    .assertResponse(response -> assertEquals(MediaType.APPLICATION_JSON_TYPE,
                        response.getContentType().map(MediaType::getName).map(MediaType::of).orElse(null)))
                    .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/group-produces/nested/note").accept(MediaType.TEXT_PLAIN_TYPE),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_ACCEPTABLE)
                    .build());
            // the nested group consumes what the outer group consumes
            AssertionUtils.assertThrows(server, HttpRequest.POST("/group-settings/nested/echo", "{}")
                .contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/group-settings/nested/echo", "hi")
                .contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"text\":\"hi\"}")
                .build());
        }
    }

    @Test
    void theRoutesOfAGroupRunOnItsExecutorUnlessTheyChooseTheirThread() throws Exception {
        try (ServerUnderTest server = server()) {
            ExecutorService blocking = server.getApplicationContext().getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.BLOCKING));
            String blockingKind = blocking.submit(() -> kind(Thread.currentThread())).get();
            String plainKind = body(server, "/group-threads-plain");

            assertEquals(blockingKind, body(server, "/group-threads/blocking"));
            // the route runs where a route without an executor runs, e.g. on the event loop
            String nonBlocking = body(server, "/group-threads/non-blocking");
            assertEquals(plainKind, nonBlocking);
            assertNotEquals(blockingKind, nonBlocking);
            // the nested group runs its routes on its executor
            assertEquals(EXECUTOR_THREAD, body(server, "/group-threads/named/route"));
            assertEquals(plainKind, body(server, "/group-threads/named/non-blocking"));
        }
    }

    private static String body(ServerUnderTest server, String path) {
        return server.exchange(HttpRequest.GET(path), String.class).body();
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    /**
     * @param thread A thread
     * @return The kind of the thread: virtual, or its name without its numbers, e.g. the name of
     * its pool
     */
    static String kind(Thread thread) {
        if (thread.isVirtual()) {
            return "virtual";
        }
        String name = thread.getName();
        return EXECUTOR_THREAD.equals(name) ? name : name.replaceAll("(-?\\d+)+$", "");
    }

    private static HttpResponse<?> threadKind() {
        return HttpResponse.ok(kind(Thread.currentThread())).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Introspected
    public record Note(String text) {
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Executors {
        @Singleton
        @Named(EXECUTOR)
        @Bean(preDestroy = "shutdown")
        ExecutorService groupSettingsExecutor() {
            return java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, EXECUTOR_THREAD));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class GroupSettingsRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/group-settings", group -> {
                group.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE);
                group.POST("/echo", Argument.of(String.class), (request, pathVariables, body) -> HttpResponse.ok("echo " + body));
                group.POST("/json", Argument.of(Note.class), (request, pathVariables, note) -> HttpResponse.ok("json " + note.text()))
                    .consumes(MediaType.APPLICATION_JSON_TYPE);
                group.path("/nested", nested -> {
                    nested.POST("/echo", Argument.of(String.class), (request, pathVariables, body) -> HttpResponse.ok(new Note(body)));
                    // after the routes: it applies to them
                    nested.produces(MediaType.APPLICATION_JSON_TYPE);
                });
            });
            // without consumes: some clients send a content type with a GET request
            routes.path("/group-produces", group -> {
                group.produces(MediaType.TEXT_PLAIN_TYPE);
                group.GET("/text", (request, pathVariables) -> HttpResponse.ok("text"));
                group.path("/nested", nested -> {
                    nested.GET("/note", (request, pathVariables) -> HttpResponse.ok(new Note("nested")));
                    nested.produces(MediaType.APPLICATION_JSON_TYPE);
                });
            });
            routes.path("/group-threads", threads -> {
                threads.GET("/blocking", (request, pathVariables) -> threadKind());
                threads.GET("/non-blocking", (request, pathVariables) -> threadKind()).nonBlocking();
                threads.path("/named", named -> {
                    named.executeOn(EXECUTOR);
                    named.GET("/route", (request, pathVariables) -> threadKind());
                    named.GET("/non-blocking", (request, pathVariables) -> threadKind()).nonBlocking();
                });
                threads.executeOn(TaskExecutors.BLOCKING);
            });
            routes.GET("/group-threads-plain", (request, pathVariables) -> threadKind());
        }
    }
}
