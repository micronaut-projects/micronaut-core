/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.http.tck.ServerUnderTest.BLOCKING_CLIENT_PROPERTY;
import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RedirectTest {

    private static final String SPEC_NAME = "RedirectTest";
    private static final String BODY = "It works";
    private static final BodyAssertion<String, String> EXPECTED_BODY = BodyAssertion.builder().body(BODY).equals();
    private static final String REDIRECT = "redirect";

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    void absoluteRedirection(boolean blocking) throws IOException {
        asserts(SPEC_NAME,
            Map.of(BLOCKING_CLIENT_PROPERTY, blocking),
            HttpRequest.GET("/redirect/redirect"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(EXPECTED_BODY)
                    .build())
        );
    }

    @Test
    void clientRedirection() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME)) {
            RedirectClient client = server.getApplicationContext().getBean(RedirectClient.class);
            assertEquals(BODY, client.redirect());
        }
    }

    @Test
    void clientRelativeUriDirect() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, relativeLoadBalancer(server, "/redirect"))) {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("direct"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals(BODY, exchange.body());
        }
    }

    @Test
    void blockingClientRelativeUriDirect() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, relativeLoadBalancer(server, "/redirect"))) {
            var exchange = client.toBlocking().exchange(HttpRequest.GET("direct"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals(BODY, exchange.body());
        }
    }

    @Test
    void clientRelativeUriNoSlash() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, relativeLoadBalancer(server, REDIRECT))) {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("direct"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals(BODY, exchange.body());
        }
    }

    @Test
    void blockingClientRelativeUriNoSlash() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, relativeLoadBalancer(server, REDIRECT))) {
            var exchange = client.toBlocking().exchange(HttpRequest.GET("direct"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals(BODY, exchange.body());
        }
    }

    @Test
    @SuppressWarnings("java:S3655")
    void clientRelativeUriRedirectAbsolute() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL().get() + "/redirect")) {
            var response = Flux.from(client.exchange(HttpRequest.GET(REDIRECT), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals(BODY, response.body());
        }
    }

    @Test
    @SuppressWarnings("java:S3655")
    void blockingClientRelativeUriRedirectAbsolute() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL().get() + "/redirect")) {
            var response = client.toBlocking().exchange(HttpRequest.GET(REDIRECT), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals(BODY, response.body());
        }
    }

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    @SuppressWarnings("java:S3655")
    void hostHeaderIsCorrectForRedirect(boolean blocking) throws IOException {
        try (ServerUnderTest otherServer = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Collections.singletonMap("redirect.server", "true"))) {
            int otherPort = otherServer.getPort().get();
            asserts(SPEC_NAME,
                Map.of(BLOCKING_CLIENT_PROPERTY, blocking),
                HttpRequest.GET("/redirect/redirect-host").header(REDIRECT, "http://localhost:" + otherPort + "/redirect/host-header"),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .body(BodyAssertion.builder().body("localhost:" + otherPort).equals())
                        .build())
            );
        }
    }

    @Test
    @Disabled("not supported, see -- io.micronaut.http.client.ClientRedirectSpec#test - client: full uri, redirect: relative")
    void relativeRedirection() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/redirect/redirect-relative"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(EXPECTED_BODY)
                    .build())
        );
    }

    @SuppressWarnings("java:S3655")
    private LoadBalancer relativeLoadBalancer(ServerUnderTest server, String path) {
        return new LoadBalancer() {
            @Override
            public Publisher<ServiceInstance> select(@Nullable Object discriminator) {
                URL url = server.getURL().get();
                return Publishers.just(ServiceInstance.of(url.getHost(), url));
            }

            @Override
            public Optional<String> getContextPath() {
                return Optional.of(path);
            }
        };
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/redirect")
    @SuppressWarnings("checkstyle:MissingJavadocType")
    static class RedirectTestController {

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
            return HttpResponse.ok(BODY);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = "redirect.server", value = StringUtils.TRUE)
    @Controller("/redirect")
    @SuppressWarnings("checkstyle:MissingJavadocType")
    static class RedirectHostHeaderController {

        @Get("/host-header")
        @Produces("text/plain")
        HttpResponse<?> hostHeader(@Header String host) {
            return HttpResponse.ok(host);
        }
    }

    @SuppressWarnings("checkstyle:MissingJavadocType")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Client("/redirect")
    interface RedirectClient {

        @Get("/redirect")
        @Consumes({"text/plain", "application/json"})
        String redirect();
    }
}
