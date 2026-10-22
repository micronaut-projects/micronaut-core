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
package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StatusOutcomeTest {
    private static final String SPEC = "StatusOutcomeTest";

    @ParameterizedTest
    @CsvSource({"false,true", "true,true", "false,false", "true,false"})
    void supportedOutcomesKeepTheirPoliciesAndAStageStatusIsDeliberate(boolean compatibility, boolean missingBodyNotFound) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC, "micronaut.server.port", -1,
            "micronaut.server.not-found-on-missing-body", missingBodyNotFound))) {
            ctx.getBean(RouteExecutor.class).forceCompatibility = compatibility;
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            Outcomes controller = ctx.getBean(Outcomes.class);
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.HEAD}) {
                    for (String shape : new String[]{"value", "stage-value", "mono-value"}) {
                        var response = response(client, method, shape);
                        assertEquals(HttpStatus.OK, response.status(), shape);
                        assertEquals(method == HttpMethod.HEAD ? null : "value", response.body(), shape);
                    }
                    for (String shape : new String[]{"status", "stage-status", "mono-status"}) {
                        var response = response(client, method, shape);
                        assertEquals(HttpStatus.ACCEPTED, response.status(), shape);
                        assertNull(response.body(), shape);
                    }
                    assertEquals(HttpStatus.ACCEPTED, response(client, method, "annotated-status").status());
                    assertEquals(HttpStatus.CREATED, response(client, method, "annotated-stage").status());
                    assertEquals(HttpStatus.CREATED, response(client, method, "annotated-mono").status());
                    for (String shape : new String[]{"null", "stage-null", "optional", "stage-optional", "mono-empty"}) {
                        assertEquals(missingBodyNotFound ? HttpStatus.NOT_FOUND : HttpStatus.NO_CONTENT, response(client, method, shape).status(), shape);
                    }
                    for (String shape : new String[]{"void", "stage-void", "mono-void"}) {
                        var response = response(client, method, shape);
                        assertEquals(HttpStatus.OK, response.status(), shape);
                        assertNull(response.body(), shape);
                    }
                    for (String shape : new String[]{"response", "stage-response"}) {
                        var response = response(client, method, shape);
                        assertEquals(HttpStatus.ACCEPTED, response.status(), shape);
                        assertEquals("yes", response.header("X-Explicit"), shape);
                        assertNull(response.body(), shape);
                    }
                    assertEquals(HttpStatus.NOT_FOUND, response(client, method, "nested-empty").status());
                    var stream = response(client, method, "stream");
                    assertEquals(HttpStatus.OK, stream.status());
                    assertEquals(method == HttpMethod.HEAD ? null : "[]", stream.body());

                    var request = HttpRequest.create(method, "/outcomes/delayed");
                    var pendingResponse = Mono.from(client.exchange(request, String.class)).toFuture();
                    Step step = controller.entered.poll(5, TimeUnit.SECONDS);
                    assertNotNull(step);
                    assertFalse(pendingResponse.isDone());
                    step.value.complete(HttpStatus.ACCEPTED);
                    var response = pendingResponse.get(5, TimeUnit.SECONDS);
                    assertEquals(HttpStatus.ACCEPTED, response.status());
                    assertNull(response.body());
                }
                var failed = Mono.from(client.exchange(HttpRequest.GET("/outcomes/delayed"), String.class)).toFuture();
                Step step = controller.entered.poll(5, TimeUnit.SECONDS);
                assertNotNull(step);
                step.value.completeExceptionally(new IllegalStateException("late failure"));
                var failure = assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, assertInstanceOf(HttpClientResponseException.class, failure.getCause()).getStatus());
                Step abandoned;
                try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                    socket.getOutputStream().write("GET /outcomes/delayed HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    abandoned = controller.entered.poll(5, TimeUnit.SECONDS);
                    assertNotNull(abandoned);
                }
                abandoned.closed.get(5, TimeUnit.SECONDS);
                assertFalse(abandoned.value.isCancelled());
                abandoned.value.complete(HttpStatus.ACCEPTED);
            }
        }
    }

    private static HttpResponse<?> response(HttpClient client, HttpMethod method, String path) {
        try {
            return client.toBlocking().exchange(HttpRequest.create(method, "/outcomes/" + path), String.class);
        } catch (HttpClientResponseException error) {
            return error.getResponse();
        }
    }

    private static final class Step {
        final CompletableFuture<HttpStatus> value = new CompletableFuture<>();
        final CompletableFuture<Void> closed = new CompletableFuture<>();
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/outcomes")
    static class Outcomes {
        final LinkedBlockingQueue<Step> entered = new LinkedBlockingQueue<>();

        @Get(value = "/value", produces = MediaType.TEXT_PLAIN)
        String value() { return "value"; }
        @Get(value = "/stage-value", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> stageValue() { return CompletableFuture.completedFuture("value"); }
        @Get(value = "/mono-value", produces = MediaType.TEXT_PLAIN)
        Mono<String> monoValue() { return Mono.just("value"); }
        @Get("/status")
        HttpStatus status() { return HttpStatus.ACCEPTED; }
        @Get("/stage-status")
        CompletionStage<HttpStatus> stageStatus() { return CompletableFuture.completedFuture(HttpStatus.ACCEPTED); }
        @Get("/mono-status")
        Mono<HttpStatus> monoStatus() { return Mono.just(HttpStatus.ACCEPTED); }
        @Status(HttpStatus.CREATED)
        @Get("/annotated-status")
        HttpStatus annotatedStatus() { return HttpStatus.ACCEPTED; }
        @Status(HttpStatus.CREATED)
        @Get("/annotated-stage")
        CompletionStage<HttpStatus> annotatedStage() { return stageStatus(); }
        @Status(HttpStatus.CREATED)
        @Get("/annotated-mono")
        Mono<HttpStatus> annotatedMono() { return monoStatus(); }
        @Get("/null")
        String nullValue() { return null; }
        @Get("/stage-null")
        CompletionStage<String> stageNull() { return CompletableFuture.completedFuture(null); }
        @Get("/optional")
        Optional<String> optional() { return Optional.empty(); }
        @Get("/stage-optional")
        CompletionStage<Optional<String>> stageOptional() { return CompletableFuture.completedFuture(Optional.empty()); }
        @Get("/mono-empty")
        Mono<String> monoEmpty() { return Mono.empty(); }
        @Get("/void")
        void voidValue() { }
        @Get("/stage-void")
        CompletionStage<Void> stageVoid() { return CompletableFuture.completedFuture(null); }
        @Get("/mono-void")
        Mono<Void> monoVoid() { return Mono.empty(); }
        @Get("/response")
        HttpResponse<String> response() { return HttpResponse.<String>accepted().header("X-Explicit", "yes"); }
        @Get("/stage-response")
        CompletionStage<HttpResponse<String>> stageResponse() { return CompletableFuture.completedFuture(response()); }
        @Get("/nested-empty")
        CompletionStage<HttpResponse<CompletionStage<String>>> nestedEmpty() {
            return CompletableFuture.completedFuture(HttpResponse.ok(CompletableFuture.completedFuture(null)));
        }
        @Get(value = "/stream", produces = MediaType.APPLICATION_JSON)
        Flux<String> stream() { return Flux.empty(); }
        @Get("/delayed")
        CompletionStage<HttpStatus> delayed(HttpRequest<?> request) {
            var step = new Step();
            ((NettyHttpRequest<?>) request).getChannelHandlerContext().channel().closeFuture()
                .addListener(future -> step.closed.complete(null));
            entered.add(step);
            return step.value;
        }
    }
}
