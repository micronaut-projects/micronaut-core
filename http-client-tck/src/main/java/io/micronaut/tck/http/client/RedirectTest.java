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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface RedirectTest extends AbstractTck {

    @Test
    default void absoluteRedirection() {
        runTest("RedirectTest", (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("/redirect/redirect"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void clientRelativeUriDirect() {
        runTest("RedirectTest", "/redirect", (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("direct"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void clientRelativeUriNoSlash() {
        runTest("RedirectTest", "redirect", (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("direct"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void clientRelativeUriRedirectAbsolute() {
        runTest("RedirectTest", "/redirect", (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("redirect"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void hostHeaderIsCorrectForRedirect() {
        runTest("RedirectTest", (server, otherServer, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("/redirect/redirect-host").header("redirect", "http://localhost:" + otherServer.getPort() + "/redirect/host-header"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("localhost:" + otherServer.getPort(), exchange.body());
        });
    }

    @Test
    @Disabled("not supported, see -- io.micronaut.http.client.ClientRedirectSpec.test - client: full uri, redirect: relative")
    default void relativeRedirection() {
        runTest("RedirectTest", (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("/redirect/redirect-relative"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void blockingAbsoluteRedirection() {
        runBlockingTest("RedirectTest", (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("/redirect/redirect"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void blockingClientRelativeUriDirect() {
        runBlockingTest("RedirectTest", "/redirect", (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("direct"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void blockingClientRelativeUriNoSlash() {
        runBlockingTest("RedirectTest", "redirect", (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("direct"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void blockingClientRelativeUriRedirectAbsolute() {
        runBlockingTest("RedirectTest", "/redirect", (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("redirect"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }

    @Test
    default void blockingHostHeaderIsCorrectForRedirect() {
        runBlockingTest("RedirectTest", (server, otherServer, client) -> {
            var exchange = client.exchange(HttpRequest.GET("/redirect/redirect-host").header("redirect", "http://localhost:" + otherServer.getPort() + "/redirect/host-header"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("localhost:" + otherServer.getPort(), exchange.body());
        });
    }

    @Test
    @Disabled("not supported, see -- io.micronaut.http.client.ClientRedirectSpec.test - client: full uri, redirect: relative")
    default void blockingRelativeRedirection() {
        runBlockingTest("RedirectTest", (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("/redirect/redirect-relative"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("It works!", exchange.body());
        });
    }
}
