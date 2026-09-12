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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpRequestTerminatedEvent} is published once for every request, whether it succeeded, failed in the
 * route or matched no route at all, so that per-request resources are released on every path.
 */
@SuppressWarnings({"java:S5960", "checkstyle:MissingJavadocType", "checkstyle:DesignForExtension"})
public class RequestTerminatedEventTest {
    public static final String SPEC_NAME = "RequestTerminatedEventTest";

    @Test
    void aSuccessfulRequestIsTerminated() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/terminated/ok"),
            (server, request) -> {
                Counter counter = counter(server);
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder().status(HttpStatus.OK).build());
                awaitTerminated(counter, "/terminated/ok");
            });
    }

    @Test
    void aFailedRequestIsTerminated() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/terminated/boom"),
            (server, request) -> {
                Counter counter = counter(server);
                AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder().status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                awaitTerminated(counter, "/terminated/boom");
            });
    }

    @Test
    void anUnmatchedRequestIsTerminated() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/terminated/missing"),
            (server, request) -> {
                Counter counter = counter(server);
                AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder().status(HttpStatus.NOT_FOUND).build());
                awaitTerminated(counter, "/terminated/missing");
            });
    }

    private static Counter counter(ServerUnderTest server) {
        Counter counter = server.getApplicationContext().getBean(Counter.class);
        counter.terminated.set(0);
        return counter;
    }

    /**
     * The event is published after the response has been sent, so the client can return before it fires.
     */
    private static void awaitTerminated(Counter counter, String path) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (counter.terminated.get() == 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(counter.terminated.get() == 1, () -> "expected one terminated event for " + path + " but saw " + counter.terminated.get());
    }

    @Controller("/terminated")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TerminatedController {

        @Get("/ok")
        @Produces(MediaType.TEXT_PLAIN)
        String ok() {
            return "ok";
        }

        @Get("/boom")
        @Produces(MediaType.TEXT_PLAIN)
        String boom() {
            throw new IllegalStateException("boom");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Counter implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        final AtomicInteger terminated = new AtomicInteger();

        @Override
        public void onApplicationEvent(HttpRequestTerminatedEvent event) {
            terminated.incrementAndGet();
        }
    }
}
