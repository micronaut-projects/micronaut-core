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

import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code @JsonView} of a server-sent events handler route selects the properties the data of
 * the events sent with its emitter is written with, like for a controller returning a publisher.
 */
class HandlerRouteSseJsonViewTest {

    @Test
    void theViewOfTheRouteSelectsTheWrittenPropertiesOfTheEvents() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "HandlerRouteSseJsonViewTest",
            "micronaut.server.port", -1, "jackson.json-view.enabled", true))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                // the controller, for comparison
                assertEquals("data: {\"name\":\"Fred\"}", get(client, "/view/controller"));
                assertAll(
                    () -> assertEquals("data: {\"name\":\"Fred\"}", get(client, "/view/data")),
                    () -> assertEquals("id: 1\ndata: {\"name\":\"Fred\"}", get(client, "/view/event")),
                    () -> assertEquals("data: {\"name\":\"Fred\"}", get(client, "/route-view")),
                    () -> assertEquals("data: {\"name\":\"Fred\",\"secret\":\"hidden\"}", get(client, "/no-view"))
                );
            }
        }
    }

    private static String get(HttpClient client, String uri) {
        return client.toBlocking().retrieve(HttpRequest.GET(uri).accept(MediaType.TEXT_EVENT_STREAM_TYPE)).strip();
    }

    @Factory
    @Requires(property = "spec.name", value = "HandlerRouteSseJsonViewTest")
    static class Routes {
        @Singleton
        HttpRoutes routes() {
            return routes -> {
                routes.path("/view", view -> {
                    view.annotate(JsonView.class, annotation -> annotation.value(PublicView.class));
                    view.GET("/data").sse((request, variables, events) -> events.send(new Profile()));
                    view.GET("/event").sse((request, variables, events) -> events.send(Event.of(new Profile()).id("1")));
                });
                routes.GET("/route-view")
                    .annotate(JsonView.class, annotation -> annotation.value(PublicView.class))
                    .sse((request, variables, events) -> events.send(new Profile()));
                routes.GET("/no-view").sse((request, variables, events) -> events.send(new Profile()));
            };
        }
    }

    @Controller("/view")
    @Requires(property = "spec.name", value = "HandlerRouteSseJsonViewTest")
    static class ViewController {
        @Get(value = "/controller", produces = MediaType.TEXT_EVENT_STREAM)
        @JsonView(PublicView.class)
        Publisher<Profile> view() {
            return Flux.just(new Profile());
        }
    }

    static class PublicView {
    }

    static class PrivateView {
    }

    @Introspected
    public static class Profile {
        @JsonView(PublicView.class)
        public String getName() {
            return "Fred";
        }

        @JsonView(PrivateView.class)
        public String getSecret() {
            return "hidden";
        }
    }
}
