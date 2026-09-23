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

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The error and status routes declared in a group of handler routes are local to the routes of
 * the group, like the error routes of a controller that are not global: the innermost group
 * first, then the groups around it, then the global error routes.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteGroupErrorsTest {
    public static final String SPEC_NAME = "HandlerRouteGroupErrorsTest";

    @Test
    void anErrorOfARouteOfTheGroupIsAnsweredByTheErrorRouteOfTheGroupLikeByTheLocalErrorRouteOfAController() throws IOException {
        try (ServerUnderTest server = server()) {
            conflict(server, "/group-errors/api/fails", "group");
            conflict(server, "/group-errors/controller/fails", "controller local");
            // outside the group and the controller: the global error route
            conflict(server, "/group-errors/outside/fails", "global");
        }
    }

    @Test
    void theInnermostGroupAnswersFirstEvenForASupertype() throws IOException {
        try (ServerUnderTest server = server()) {
            conflict(server, "/group-errors/api/inner/fails", "inner runtime");
            // an error only the outer group handles
            AssertionUtils.assertThrows(server, HttpRequest.GET("/group-errors/api/inner/io"), HttpResponseAssertion.builder()
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body("group io")
                .build());
        }
    }

    @Test
    void anErrorOfAFilterOfTheGroupAndAnAsyncErrorRouteOfTheGroup() throws IOException {
        try (ServerUnderTest server = server()) {
            conflict(server, "/group-errors/api/filter-fails", "group");
            conflict(server, "/group-errors/async/fails", "async group");
        }
    }

    @Test
    void theStatusRoutesOfTheGroupAnswerTheStatusResponsesOfItsRoutesLikeALocalStatusRouteOfAController() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/group-errors/api/missing"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .body("group not found")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.GET("/group-errors/controller/missing"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .body("controller not found")
                .build());
            // an HttpStatusException of a route of the group
            AssertionUtils.assertThrows(server, HttpRequest.GET("/group-errors/api/gone"), HttpResponseAssertion.builder()
                .status(HttpStatus.GONE)
                .body("group gone")
                .build());
        }
    }

    @Test
    void aRequestUnderThePrefixThatNoRouteMatchesIsAnsweredByTheGlobalStatusRoutesOnly() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/group-errors/api/no-such-route"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .assertResponse(response -> assertNotEquals("group not found", response.getBody(String.class).orElse(null)))
                .build());
        }
    }

    private static void conflict(ServerUnderTest server, String path, String body) {
        AssertionUtils.assertThrows(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
            .status(HttpStatus.CONFLICT)
            .body(body)
            .build());
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<?> text(HttpStatus status, String body) {
        return HttpResponse.status(status).body(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    static final class GroupErrorsFailure extends RuntimeException {
        GroupErrorsFailure() {
            super("group errors failure");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class GroupErrorRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.error(GroupErrorsFailure.class, (request, error) -> text(HttpStatus.CONFLICT, "global"));
            routes.GET("/group-errors/outside/fails", (request, pathVariables) -> {
                throw new GroupErrorsFailure();
            });
            routes.path("/group-errors/api", api -> {
                api.GET("/fails", (request, pathVariables) -> {
                    throw new GroupErrorsFailure();
                });
                api.GET("/filter-fails", (request, pathVariables) -> text(HttpStatus.OK, "not failed"))
                    .before(request -> {
                        throw new GroupErrorsFailure();
                    });
                api.GET("/missing", (request, pathVariables) -> HttpResponse.notFound());
                api.GET("/gone", (request, pathVariables) -> {
                    throw new HttpStatusException(HttpStatus.GONE, "gone");
                });
                api.path("/inner", inner -> {
                    inner.GET("/fails", (request, pathVariables) -> {
                        throw new GroupErrorsFailure();
                    });
                    inner.GET("/io", (request, pathVariables) -> {
                        throw new IOException("io");
                    });
                    inner.error(IllegalStateException.class, (request, error) -> text(HttpStatus.CONFLICT, "inner state"));
                    // a supertype of the failure: the innermost group answers first
                    inner.error(RuntimeException.class, (request, error) -> text(HttpStatus.CONFLICT, "inner runtime"));
                });
                api.error(GroupErrorsFailure.class, (request, error) -> text(HttpStatus.CONFLICT, "group"));
                api.error(IOException.class, (request, error) -> text(HttpStatus.UNPROCESSABLE_ENTITY, "group io"));
                api.status(HttpStatus.NOT_FOUND, request -> text(HttpStatus.NOT_FOUND, "group not found"));
                api.status(HttpStatus.GONE, request -> text(HttpStatus.GONE, "group gone"));
            });
            routes.path("/group-errors/async", async -> {
                async.GET("/fails", (request, pathVariables) -> {
                    throw new GroupErrorsFailure();
                });
                async.errorAsync(GroupErrorsFailure.class, (request, error) ->
                    CompletableFuture.supplyAsync(() -> text(HttpStatus.CONFLICT, "async group")));
            });
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/group-errors/controller")
    static class LocalErrorController {
        @Get("/fails")
        String fails() {
            throw new GroupErrorsFailure();
        }

        @Get("/missing")
        HttpResponse<?> missing() {
            return HttpResponse.notFound();
        }

        @Error(GroupErrorsFailure.class)
        HttpResponse<?> localError() {
            return text(HttpStatus.CONFLICT, "controller local");
        }

        @Error(status = HttpStatus.NOT_FOUND)
        HttpResponse<?> localNotFound() {
            return text(HttpStatus.NOT_FOUND, "controller not found");
        }
    }
}
