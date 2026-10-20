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
package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.FormOptions;
import io.micronaut.http.form.FormData;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A url-encoded form with a malformed percent escape is a client error: every reader of the form
 * refuses it with a 400, unless {@code micronaut.server.netty.form-lenient-percent-decoding}
 * restores the lenient decoding of the escapes.
 */
class FormMalformedPercentEscapeTest {
    private static final String SPEC_NAME = "FormMalformedPercentEscapeTest";
    private static final List<String> PATHS = List.of("/escape/body", "/escape/form-data", "/escape/async-form", "/escape/raw-form", "/escape/fn");
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @ParameterizedTest
    @ValueSource(strings = {"g=%2", "g=%GG", "g=%", "g=a%G1", "%GG=v", "g=%2&h=v"})
    void aMalformedEscapeIsRejectedByDefault(String body) throws Exception {
        try (ApplicationContext ctx = run(false)) {
            int port = ctx.getBean(EmbeddedServer.class).start().getPort();
            for (String path : PATHS) {
                assertEquals(400, send(port, path, HttpRequest.BodyPublishers.ofString(body)).statusCode(), path);
            }
        }
    }

    @Test
    void aWellFormedFormIsAccepted() throws Exception {
        try (ApplicationContext ctx = run(false)) {
            int port = ctx.getBean(EmbeddedServer.class).start().getPort();
            for (String path : PATHS) {
                java.net.http.HttpResponse<String> response = send(port, path, HttpRequest.BodyPublishers.ofString("g=a%2Fb+c"));
                assertEquals(200, response.statusCode(), path);
                assertEquals(path.equals("/escape/raw-form") ? "a%2Fb+c" : "a/b c", response.body(), path);
            }
        }
    }

    @Test
    void anEscapeSplitBetweenChunksIsDecoded() throws Exception {
        try (ApplicationContext ctx = run(false)) {
            int port = ctx.getBean(EmbeddedServer.class).start().getPort();
            for (String path : PATHS) {
                java.net.http.HttpResponse<String> response = send(port, path,
                    HttpRequest.BodyPublishers.fromPublisher(new ChunkPublisher("g=a%2", "Fb%C", "3%A9")));
                assertEquals(200, response.statusCode(), path);
                assertEquals(path.equals("/escape/raw-form") ? "a%2Fb%C3%A9" : "a/bé", response.body(), path);
            }
        }
    }

    @Test
    void aMalformedEscapeSplitBetweenChunksIsRejected() throws Exception {
        try (ApplicationContext ctx = run(false)) {
            int port = ctx.getBean(EmbeddedServer.class).start().getPort();
            for (String path : PATHS) {
                assertEquals(400, send(port, path,
                    HttpRequest.BodyPublishers.fromPublisher(new ChunkPublisher("g=a%", "G1&h=v"))).statusCode(), path);
                assertEquals(400, send(port, path,
                    HttpRequest.BodyPublishers.fromPublisher(new ChunkPublisher("g=a%2", ""))).statusCode(), path);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"g=%2", "g=%GG"})
    void theLenientDecodingCanBeRestored(String body) throws Exception {
        try (ApplicationContext ctx = run(true)) {
            int port = ctx.getBean(EmbeddedServer.class).start().getPort();
            for (String path : PATHS) {
                assertEquals(200, send(port, path, HttpRequest.BodyPublishers.ofString(body)).statusCode(), path);
            }
        }
    }

    private static ApplicationContext run(boolean lenient) {
        return ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1,
            "micronaut.server.netty.form-lenient-percent-decoding", lenient));
    }

    private static java.net.http.HttpResponse<String> send(int port, String path, HttpRequest.BodyPublisher body) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
                .POST(body).build(),
            BodyHandlers.ofString());
    }

    /**
     * A body in the given pieces, each sent once the previous one was requested.
     */
    private record ChunkPublisher(String... chunks) implements Flow.Publisher<ByteBuffer> {
        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private int next;
                private boolean done;

                @Override
                public synchronized void request(long n) {
                    for (long i = 0; i < n && !done; i++) {
                        if (next == chunks.length) {
                            done = true;
                            subscriber.onComplete();
                            return;
                        }
                        subscriber.onNext(ByteBuffer.wrap(chunks[next++].getBytes(StandardCharsets.US_ASCII)));
                    }
                }

                @Override
                public synchronized void cancel() {
                    done = true;
                }
            });
        }
    }

    @Controller("/escape")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class EscapeController {
        @Post(uri = "/body", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String body(@Body Map<String, String> form) {
            return form.getOrDefault("g", "missing");
        }

        @Post(uri = "/form-data", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String formData(FormData form) {
            return form.getString("g", "missing");
        }

        @Post(uri = "/async-form", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> asyncForm(AsyncRequestBody body) {
            return body.form().thenApply(form -> form.getString("g", "missing"));
        }

        @Post(uri = "/raw-form", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> rawForm(AsyncRequestBody body) {
            return body.form(FormOptions.defaults().withRawValues()).thenApply(form -> form.getString("g", "missing"));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class EscapeRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/escape/fn").form().handle((request, variables, form) ->
                HttpResponse.ok(form.getString("g", "missing")).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
