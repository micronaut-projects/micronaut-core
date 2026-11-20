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
package io.micronaut.http.server.netty.jackson;

import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A type-use annotation on the type argument of a wrapped body (for example
 * {@code Mono<@Valid T>}) must not switch the {@code @JsonView} of the parameter or route off.
 */
class JsonViewTypeUseAnnotatedBodyTest {
    private static final String SPEC_NAME = "JsonViewTypeUseAnnotatedBodyTest";

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
    @interface Checked {
    }

    static class PublicView {
    }

    static class AdminView extends PublicView {
    }

    @Introspected
    @JsonView(PublicView.class)
    public static class Account {
        private String name;
        @JsonView(AdminView.class)
        private String password;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"mono", "future"})
    void typeUseAnnotatedBodyHonoursJsonView(String path) {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "jackson.json-view.enabled", true, "micronaut.server.port", -1));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            String password = client.toBlocking().retrieve(HttpRequest.POST("/jsonview-type-use/" + path,
                "{\"name\":\"mallory\",\"password\":\"injected\"}").contentType(MediaType.APPLICATION_JSON), String.class);
            assertEquals("null", password);
        }
    }

    @Test
    void typeUseAnnotatedEventDataHonoursJsonView() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "jackson.json-view.enabled", true, "micronaut.server.port", -1));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            String body = client.toBlocking().retrieve(HttpRequest.GET("/jsonview-type-use/sse")
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM), String.class);
            assertTrue(body.contains("Bob"), body);
            assertFalse(body.contains("secret"), body);
        }
    }

    @Controller("/jsonview-type-use")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ctrl {
        @Post(value = "/mono", produces = MediaType.TEXT_PLAIN)
        Mono<String> mono(@Body @JsonView(PublicView.class) Mono<@Checked Account> m) {
            return m.map(a -> String.valueOf(a.getPassword()));
        }

        @Post(value = "/future", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<String> future(@Body @JsonView(PublicView.class) CompletableFuture<@Checked Account> m) {
            return m.thenApply(a -> String.valueOf(a.getPassword()));
        }

        @JsonView(PublicView.class)
        @Get(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM)
        Flux<Event<@Checked Account>> sse() {
            Account a = new Account();
            a.setName("Bob");
            a.setPassword("secret");
            return Flux.just(Event.of(a).name("created"));
        }
    }
}
