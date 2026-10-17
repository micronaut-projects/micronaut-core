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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.form.FormData;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Route locators that read the body of the request before they locate the target, like a JAX-RS
 * resource that reads {@code @FormParam} fields before its sub-resource locator runs: the
 * located route reads the body again.
 */
class HandlerRouteLocatorBodyTest {
    private static final String SPEC_NAME = "HandlerRouteLocatorBodyTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;
    private static Recorder recorder;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1, "micronaut.server.cors.enabled", true));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = ctx.createBean(HttpClient.class, server.getURL());
        recorder = ctx.getBean(Recorder.class);
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.close();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @BeforeEach
    void reset() {
        recorder.forms.clear();
        recorder.calls.set(0);
    }

    @Test
    void theLocatorAndTheLocatedRouteReceiveTheOneFormOfTheRequest() {
        String response = client.toBlocking().retrieve(HttpRequest.POST("/accounts/7/greet", "token=gold&name=Ann")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        assertEquals("account 7 gold: hello Ann", response);
        assertEquals(1, recorder.calls.get());
        assertEquals(2, recorder.forms.size());
        assertSame(recorder.forms.get(0), recorder.forms.get(1), "the form is read once");
    }

    @Test
    void aMultipartFormWithAFileTheLocatedRouteReads() {
        MultipartBody body = MultipartBody.builder()
            .addPart("token", "silver")
            .addPart("name", "Bob")
            .addPart("file", "note.txt", MediaType.TEXT_PLAIN_TYPE, "the note".getBytes(StandardCharsets.UTF_8))
            .build();
        String response = client.toBlocking().retrieve(HttpRequest.POST("/accounts/8/upload", body)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE));
        assertEquals("account 8 silver: note.txt the note", response);
    }

    @Test
    void aGetIsLocatedWithTheEmptyForm() {
        assertEquals("account 1 anonymous", client.toBlocking().retrieve(HttpRequest.GET("/accounts/1/profile")));
        assertEquals(List.of(FormData.empty()), recorder.forms);
    }

    @Test
    void aHeadIsLocatedWithTheEmptyForm() {
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.HEAD("/accounts/1/profile"));
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(1, recorder.calls.get());
    }

    @Test
    void aJsonBodyUnderAFormLocatorIsLeftToTheLocatedRoute() {
        String response = client.toBlocking().retrieve(HttpRequest.POST("/accounts/2/order", "{\"kind\":\"express\",\"id\":3}")
            .contentType(MediaType.APPLICATION_JSON_TYPE));
        assertEquals("account 2 anonymous: order 3", response);
        assertEquals(List.of(FormData.empty()), recorder.forms);
    }

    @Test
    void aMalformedMultipartBodyFailsInsteadOfBecomingEmpty() {
        BlockingHttpClient blocking = client.toBlocking();
        HttpRequest<?> request = HttpRequest.POST("/accounts/3/greet", "--x\r\nnot a part")
            .contentType("multipart/form-data");
        HttpClientResponseException error = assertThrows(HttpClientResponseException.class, () -> blocking.retrieve(request));
        // a multipart body without a boundary cannot be decoded: the reading of the form fails it
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatus());
        assertEquals(0, recorder.calls.get(), "the locator is not called");
    }

    @Test
    void aCorsPreflightUnderAFormLocatorIsAnswered() {
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.OPTIONS("/accounts/4/greet")
            .header("Origin", "https://example.com")
            .header("Access-Control-Request-Method", "POST"));
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("https://example.com", response.getHeaders().get("Access-Control-Allow-Origin"));
        assertEquals(List.of(FormData.empty()), recorder.forms);
    }

    @Test
    void anAsyncLocatorOfTheFormThatLocatesNothingIsNotFound() {
        BlockingHttpClient blocking = client.toBlocking();
        HttpRequest<?> request = HttpRequest.POST("/accounts/9/greet", "token=none&name=Ann").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        HttpClientResponseException error = assertThrows(HttpClientResponseException.class, () -> blocking.retrieve(request));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @Test
    void theLocatorAndTheLocatedRouteDecodeTheBody() {
        String response = client.toBlocking().retrieve(HttpRequest.POST("/orders/route", "{\"kind\":\"express\",\"id\":3}")
            .contentType(MediaType.APPLICATION_JSON_TYPE));
        assertEquals("express: order 3", response);
    }

    @Test
    void aRequestWithoutTheBodyOfARequiredTypeIsABadRequest() {
        BlockingHttpClient blocking = client.toBlocking();
        HttpRequest<?> request = HttpRequest.POST("/orders/route", "").contentType(MediaType.APPLICATION_JSON_TYPE);
        HttpClientResponseException error = assertThrows(HttpClientResponseException.class, () -> blocking.retrieve(request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals(0, recorder.calls.get(), "the locator is not called");
    }

    @Test
    void theLocatorReadsTheBodyItselfAndTheLocatedRouteReadsItAgain() {
        String response = client.toBlocking().retrieve(HttpRequest.POST("/raw/echo", "a long text").contentType(MediaType.TEXT_PLAIN_TYPE));
        assertEquals("11 bytes: a long text", response);
    }

    @Test
    void theErrorOfTheLocatorIsHandledLikeAControllerError() {
        BlockingHttpClient blocking = client.toBlocking();
        HttpRequest<?> request = HttpRequest.POST("/raw/echo", "fail").contentType(MediaType.TEXT_PLAIN_TYPE);
        HttpClientResponseException error = assertThrows(HttpClientResponseException.class, () -> blocking.retrieve(request));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatus());
    }

    @Introspected
    record Order(String kind, int id) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder {
        final List<FormData> forms = new CopyOnWriteArrayList<>();
        final AtomicInteger calls = new AtomicInteger();
    }

    record Account(long id, String token) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        private final Recorder recorder;

        Routes(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            AccountRoutes accounts = new AccountRoutes(recorder);
            routes.locator("/accounts/{id}")
                .form()
                .locateAsync((request, pathVariables, form) -> {
                    recorder.calls.incrementAndGet();
                    recorder.forms.add(form);
                    String token = form.find("token", String.class).orElse("anonymous");
                    return CompletableFuture.completedFuture("none".equals(token) ? null : new Account(pathVariables.getLong("id"), token));
                }, accounts);
            routes.locator("/orders")
                .body(Order.class)
                .locate((request, pathVariables, order) -> {
                    recorder.calls.incrementAndGet();
                    return order.kind();
                }, target -> new OrderRoutes());
            routes.locator("/raw")
                .body()
                .locateAsync((request, pathVariables, body) -> body.text().thenApply(text -> {
                    if ("fail".equals(text)) {
                        throw new IllegalStateException("the locator failed");
                    }
                    return text.length() + " bytes";
                }), target -> new RawRoutes());
        }
    }

    static final class AccountRoutes implements LocatedRoutes<Account> {
        private final Recorder recorder;

        AccountRoutes(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public Argument<Account> targetType() {
            return Argument.of(Account.class);
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Account> located) {
            located.POST("/greet").form().handle((request, pathVariables, account, form) -> {
                recorder.forms.add(form);
                return HttpResponse.ok("account " + account.id() + " " + account.token() + ": hello " + form.getString("name"))
                    .contentType(MediaType.TEXT_PLAIN_TYPE);
            });
            located.GET("/profile", (request, pathVariables) -> {
                Account account = LocatedRoutes.locatedTarget(pathVariables) instanceof Account a ? a : null;
                return HttpResponse.ok("account " + account.id() + " " + account.token()).contentType(MediaType.TEXT_PLAIN_TYPE);
            });
            located.POST("/order").body(Order.class).handle((request, pathVariables, account, order) ->
                HttpResponse.ok("account " + account.id() + " " + account.token() + ": order " + order.id()).contentType(MediaType.TEXT_PLAIN_TYPE));
            located.POST("/upload").form().handleAsync((request, pathVariables, account, form) ->
                form.getFile("file").text(1024).thenApply(text ->
                    HttpResponse.ok("account " + account.id() + " " + account.token() + ": " + form.getFile("file").fileName() + " " + text)
                        .contentType(MediaType.TEXT_PLAIN_TYPE)));
        }
    }

    static final class OrderRoutes implements LocatedRoutes<String> {
        @Override
        public Argument<String> targetType() {
            return Argument.STRING;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<String> located) {
            located.POST("/route").body(Order.class).handle((request, pathVariables, kind, order) ->
                HttpResponse.ok(kind + ": order " + order.id()).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    static final class RawRoutes implements LocatedRoutes<String> {
        @Override
        public Argument<String> targetType() {
            return Argument.STRING;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<String> located) {
            located.POST("/echo").consumes(MediaType.TEXT_PLAIN_TYPE).body(String.class).handle((request, pathVariables, length, text) ->
                HttpResponse.ok(length + ": " + text).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
