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
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.HttpClient;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code @JsonView} given on the body type of a handler route selects the properties the body
 * is decoded with, like on a {@code @Body} parameter of a controller: in the body a handler
 * receives, and in the body an asynchronous handler reads.
 */
class HandlerRouteBodyJsonViewTest {

    private static final String JSON = "{\"name\":\"Fred\",\"secret\":\"hidden\"}";

    @Test
    void theViewOfTheBodyTypeSelectsTheDecodedProperties() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "HandlerRouteBodyJsonViewTest",
            "micronaut.server.port", -1, "jackson.json-view.enabled", true))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                // the controller, for comparison
                assertEquals("Fred:null", post(client, "/view/controller"));
                assertAll(
                    () -> assertEquals("Fred:null", post(client, "/view/body")),
                    () -> assertEquals("Fred:null", post(client, "/view/async"))
                );
            }
        }
    }

    private static String post(HttpClient client, String uri) {
        return client.toBlocking().retrieve(HttpRequest.POST(uri, JSON).contentType(MediaType.APPLICATION_JSON_TYPE));
    }

    private static Argument<Profile> publicProfile() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(JsonView.class.getName(),
            Map.of(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<?>[]{new AnnotationClassValue<>(PublicView.class)}));
        return Argument.of(Profile.class, "profile", metadata);
    }

    private static HttpResponse<String> describe(Profile profile) {
        return HttpResponse.ok(profile.getName() + ":" + profile.getSecret()).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Factory
    @Requires(property = "spec.name", value = "HandlerRouteBodyJsonViewTest")
    static class Routes {
        @Singleton
        HttpRoutes routes() {
            Argument<Profile> profile = publicProfile();
            return routes -> {
                routes.POST("/view/body", profile, (request, variables, body) -> describe(body));
                routes.asyncPOST("/view/async", (request, variables, body) -> body.body(profile).thenApply(HandlerRouteBodyJsonViewTest::describe));
            };
        }
    }

    @Controller("/view")
    @Requires(property = "spec.name", value = "HandlerRouteBodyJsonViewTest")
    static class ViewController {
        @Post(value = "/controller", produces = MediaType.TEXT_PLAIN)
        String view(@Body @JsonView(PublicView.class) Profile body) {
            return body.getName() + ":" + body.getSecret();
        }
    }

    static class PublicView {
    }

    static class PrivateView {
    }

    @Introspected
    public static class Profile {
        private String name;
        private String secret;

        @JsonView(PublicView.class)
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonView(PrivateView.class)
        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
