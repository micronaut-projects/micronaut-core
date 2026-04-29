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
package io.micronaut.docs.http.client.retry;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.retry.HttpResponseRetryPredicate;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the {@link CustomResponseRetryPredicate} doc-example end-to-end: replaces the
 * default {@link HttpResponseRetryPredicate} with a custom one that also retries
 * {@code 425 Too Early}, fires a request to a controller that returns 425 then 200, and
 * asserts the retry happened.
 */
class CustomResponseRetryPredicateSpec {

    EmbeddedServer server;
    HttpClient client;

    @BeforeEach
    void setUp() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", getClass().getSimpleName(),
            "micronaut.http.client.retry.enabled", "true",
            "micronaut.http.client.retry.attempts", "3",
            "micronaut.http.client.retry.delay", "1ms",
            "micronaut.http.client.retry.max-delay", "10ms",
            "micronaut.http.client.retry.multiplier", "1.0",
            "micronaut.http.client.retry.jitter", "0.0",
            "micronaut.http.client.read-timeout", "5s",
            "micronaut.http.client.request-timeout", "10s"
        ));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    @Test
    void customPredicateRetries425TooEarly() {
        TooEarlyController controller = server.getApplicationContext().getBean(TooEarlyController.class);
        controller.reset(2); // succeed on the second attempt

        String body = client.toBlocking().retrieve("/too-early");

        assertEquals("ok", body);
        assertEquals(2, controller.attempts.get());
    }

    @Test
    void customPredicateStillRespectsTerminal4xx() {
        // The custom predicate delegates to rfc9110() for non-425 statuses, so 404 is still terminal.
        TooEarlyController controller = server.getApplicationContext().getBean(TooEarlyController.class);
        controller.reset(0); // never succeed → keep returning 404 from /not-found

        var ex = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve("/not-found"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals(1, controller.notFoundAttempts.get()); // single attempt only
    }

    @Requires(property = "spec.name", value = "CustomResponseRetryPredicateSpec")
    @Controller
    static class TooEarlyController {
        final AtomicInteger attempts = new AtomicInteger();
        final AtomicInteger notFoundAttempts = new AtomicInteger();
        int succeedAt = 1;

        void reset(int succeedAt) {
            this.succeedAt = succeedAt;
            attempts.set(0);
            notFoundAttempts.set(0);
        }

        @Get("/too-early")
        HttpResponse<?> tooEarly() {
            int n = attempts.incrementAndGet();
            return n >= succeedAt ? HttpResponse.ok("ok") : HttpResponse.status(HttpStatus.TOO_EARLY);
        }

        @Get("/not-found")
        HttpResponse<?> notFound() {
            notFoundAttempts.incrementAndGet();
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }
}
