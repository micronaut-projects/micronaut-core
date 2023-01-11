/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.tck.http.client;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.annotation.Client;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface RedirectTest extends AbstractTck {

    String REDIRECT_TEST = "RedirectTest";

    @Test
    default void absoluteRedirection() {
        runTest(REDIRECT_TEST, (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("/redirect/redirect"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
        runBlockingTest(REDIRECT_TEST, (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("/redirect/redirect"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void clientRedirection() {
        runTest(REDIRECT_TEST, (server, client) -> {
            var redirectClient = server.getApplicationContext().getBean(RedirectClient.class);
            assertEquals("It works!", redirectClient.redirect());
        });
    }

    @Test
    default void clientRelativeUriDirect() {
        runTest(REDIRECT_TEST, "/redirect", (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("direct"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
        runBlockingTest(REDIRECT_TEST, "/redirect", (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("direct"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void clientRelativeUriNoSlash() {
        runTest(REDIRECT_TEST, "redirect", (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("direct"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
        runBlockingTest(REDIRECT_TEST, "redirect", (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("direct"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void clientRelativeUriRedirectAbsolute() {
        runTest(REDIRECT_TEST, "/redirect", (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("redirect"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
        runBlockingTest(REDIRECT_TEST, "/redirect", (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("redirect"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void hostHeaderIsCorrectForRedirect() {
        runTest(REDIRECT_TEST, (server, otherServer, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("/redirect/redirect-host").header("redirect", "http://localhost:" + otherServer.getPort() + "/redirect/host-header"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("localhost:" + otherServer.getPort(), exchange.body());
        });
        runBlockingTest(REDIRECT_TEST, (server, otherServer, client) -> {
            var exchange = client.exchange(HttpRequest.GET("/redirect/redirect-host").header("redirect", "http://localhost:" + otherServer.getPort() + "/redirect/host-header"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("localhost:" + otherServer.getPort(), exchange.body());
        });
    }

    @Test
    @Disabled("not supported, see -- io.micronaut.http.client.ClientRedirectSpec.test - client: full uri, redirect: relative")
    default void relativeRedirection() {
        runTest(REDIRECT_TEST, (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("/redirect/redirect-relative"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
        runBlockingTest(REDIRECT_TEST, (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("/redirect/redirect-relative"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Requires(property = "spec.name", value = REDIRECT_TEST)
    @Controller("/redirect")
    class RedirectTestController {

        @Get("/redirect")
        HttpResponse<?> redirect() {
            return HttpResponse.redirect(URI.create("/redirect/direct"));
        }

        @Get("/redirect-relative")
        HttpResponse<?> redirectRelative() {
            return HttpResponse.redirect(URI.create("./direct"));
        }

        @Get("/redirect-host")
        HttpResponse<?> redirectHost(@Header String redirect) {
            return HttpResponse.redirect(URI.create(redirect));
        }

        @Get("/direct")
        @Produces("text/plain")
        HttpResponse<?> direct() {
            return HttpResponse.ok("It works!");
        }
    }

    @Requires(property = "spec.name", value = REDIRECT_TEST)
    @Requires(property = "redirect.server", value = StringUtils.TRUE)
    @Controller("/redirect")
    class RedirectHostHeaderController {

        @Get("/host-header")
        @Produces("text/plain")
        HttpResponse<?> hostHeader(@Header String host) {
            return HttpResponse.ok(host);
        }
    }

    @SuppressWarnings("checkstyle:MissingJavadocType")
    @Requires(property = "spec.name", value = REDIRECT_TEST)
    @Client("/redirect")
    public interface RedirectClient {

        @Get("/redirect")
        @Consumes({"text/plain", "application/json"})
        String redirect();
    }
}
