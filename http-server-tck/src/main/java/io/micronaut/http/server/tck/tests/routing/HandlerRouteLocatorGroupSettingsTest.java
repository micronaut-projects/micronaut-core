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
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;

/**
 * A located route inherits the settings of the groups of its locator route, like a route
 * declared in those groups: their attributes, media types and executor, unless it, or a group of
 * its located routes, sets its own. Its URI template, e.g. for the metrics, is the template under
 * the prefixes of the locator routes. The groups of located routes route to handlers and locators
 * that receive the located target.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteLocatorGroupSettingsTest {
    public static final String SPEC_NAME = "HandlerRouteLocatorGroupSettingsTest";
    private static final String THREAD = "locator-group-thread";

    @Test
    void aLocatedRouteInheritsTheSettingsOfTheGroupOfItsLocatorRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/settings/5/inherited"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("shop " + THREAD)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .build());
        }
    }

    @Test
    void theSettingsOfALocatedRouteOverrideTheInheritedOnes() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/settings/5/own"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("item")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                .build());
        }
    }

    @Test
    void aLocatedRouteConsumesWhatTheGroupOfItsLocatorRouteConsumes() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/settings/5/echo", "text").contentType(MediaType.TEXT_PLAIN_TYPE),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("echo text")
                    .build());
            AssertionUtils.assertThrows(server, HttpRequest.POST("/settings/5/echo", "{}").contentType(MediaType.APPLICATION_JSON_TYPE),
                HttpResponseAssertion.builder()
                    .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .build());
        }
    }

    @Test
    void theUriTemplateOfALocatedRouteIsUnderThePrefixesOfItsLocatorRoutes() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/settings/5/template/7"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("/settings/{id}/template/{item}")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/settings/5/parts/3/template"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("/settings/{id}/parts/{part}/template")
                .build());
        }
    }

    @Test
    void theRoutesOfAGroupOfLocatedRoutesReceiveTheTarget() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/settings/5/typed/items/7"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("item 7 of 5")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/settings/5/typed/parts/3/template"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("/settings/{id}/typed/parts/{part}/template")
                .build());
            // the error route of the group of located routes answers the errors of its routes
            AssertionUtils.assertThrows(server, HttpRequest.GET("/settings/5/typed/items/0"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .body("no item 0 of 5")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of());
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body);
    }

    record Shop(String id) {
    }

    record Part(String shop, String id) {
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Executors {
        @Singleton
        @Named("locator-group")
        @Bean(preDestroy = "shutdown")
        ExecutorService locatorGroupExecutor() {
            return java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, THREAD));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SettingsLocatorRoutes implements HttpRoutes {

        @Override
        public void routes(HttpRouteBuilder routes) {
            LocatedRoutes<Part> parts = TckLocatedRoutes.of(Part.class, located ->
                located.GET("/template").handle((request, pathVariables, part) ->
                    text(BasicHttpAttributes.getUriTemplate(request).orElse("none"))));
            LocatedRoutes<Shop> shops = TckLocatedRoutes.of(Shop.class, located -> {
                located.GET("/inherited").handle((request, pathVariables, shop) ->
                    text(RouteAttributes.getRouteInfo(request).flatMap(route -> route.getAttribute("bean", String.class)).orElse("none")
                        + " " + Thread.currentThread().getName()));
                located.GET("/own").produces(MediaType.TEXT_HTML_TYPE).handle((request, pathVariables, shop) -> text("item"));
                located.POST("/echo").body(String.class).handle((request, pathVariables, shop, body) -> text("echo " + body));
                located.GET("/template/{item}").handle((request, pathVariables, shop) ->
                    text(BasicHttpAttributes.getUriTemplate(request).orElse("none")));
                located.locate("/parts/{part}", (request, pathVariables, shop) -> new Part(shop.id(), pathVariables.getString("part")), parts);
                located.path("/typed", typed -> {
                    typed.error(NoSuchElementException.class, (request, error) ->
                        HttpResponse.notFound(error.getMessage()));
                    typed.GET("/items/{item}").handle((request, pathVariables, shop) -> {
                        int item = pathVariables.getInt("item");
                        if (item == 0) {
                            throw new NoSuchElementException("no item 0 of " + shop.id());
                        }
                        return text("item " + item + " of " + shop.id());
                    });
                    typed.locate("/parts/{part}", (request, pathVariables, shop) -> new Part(shop.id(), pathVariables.getString("part")), parts);
                });
            });
            routes.path("/settings", group -> {
                group.attribute("bean", "shop")
                    .produces(MediaType.TEXT_PLAIN_TYPE)
                    .consumes(MediaType.TEXT_PLAIN_TYPE)
                    .executeOn("locator-group");
                group.locate("/{id}", (request, pathVariables) -> new Shop(pathVariables.getString("id")), shops);
            });
        }
    }
}
