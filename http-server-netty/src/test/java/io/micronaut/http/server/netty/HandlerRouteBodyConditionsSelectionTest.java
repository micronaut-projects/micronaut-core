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
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * How the server chooses a handler route by the body of the request: the body decoded by the
 * message body readers, converted, or as a filter set it, conditions decided later or failing,
 * and the routes of route locators chosen by the body.
 */
class HandlerRouteBodyConditionsSelectionTest {
    private static final String SPEC_NAME = "HandlerRouteBodyConditionsSelectionTest";
    private static final Argument<Order> ORDER = Argument.of(Order.class);

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = ctx.createBean(HttpClient.class, server.getURL());
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

    @Test
    void theBodyDecodedByAReaderOnceForTheConditionsOfTheSameArgument() {
        assertEquals("express 1", post("/sel/orders", "{\"kind\":\"express\",\"id\":1}", MediaType.APPLICATION_JSON_TYPE));
        assertEquals("bulk 2", post("/sel/orders", "{\"kind\":\"bulk\",\"id\":2}", MediaType.APPLICATION_JSON_TYPE));
        // does not decode: the conditions are not met
        assertEquals("other", post("/sel/orders", "{not json", MediaType.APPLICATION_JSON_TYPE));
    }

    @Test
    void theBodyConvertedWithoutAReader() {
        assertEquals("number", post("/sel/number", "42", MediaType.TEXT_PLAIN_TYPE));
        assertEquals("other", post("/sel/number", "forty-two", MediaType.TEXT_PLAIN_TYPE));
    }

    @Test
    void conditionsDecidedLater() {
        assertEquals("later both", post("/sel/later", "vip express", MediaType.TEXT_PLAIN_TYPE));
        assertEquals("later vip", post("/sel/later", "vip", MediaType.TEXT_PLAIN_TYPE));
        assertEquals("other", post("/sel/later", "none", MediaType.TEXT_PLAIN_TYPE));
    }

    @Test
    void conditionsThatFailAreNotMet() {
        assertEquals("other", post("/sel/failing", "stage", MediaType.TEXT_PLAIN_TYPE));
        assertEquals("other", post("/sel/failing", "throw", MediaType.TEXT_PLAIN_TYPE));
        assertEquals("other", post("/sel/failing", "null", MediaType.TEXT_PLAIN_TYPE));
    }

    @Test
    void theBodyAFilterSet() {
        assertEquals("text", postWithHeader("/sel/set", "X-Set", "text"));
        assertEquals("bytes", postWithHeader("/sel/set", "X-Set", "bytes"));
        assertEquals("order", postWithHeader("/sel/set", "X-Set", "order"));
        assertEquals("other", postWithHeader("/sel/set", "X-Set", "none"));
    }

    @Test
    void anEmptyBody() {
        assertEquals("empty", client.toBlocking().retrieve(HttpRequest.POST("/sel/empty", "").contentType(MediaType.TEXT_PLAIN_TYPE)));
    }

    @Test
    void theRoutesOfALocatedTargetChosenByTheBody() {
        assertEquals("vip shop 3 express", post("/sel/vip/shops/3/orders", "vip express", MediaType.TEXT_PLAIN_TYPE));
        assertEquals("vip shop 3 express", post("/sel/vip-async/shops/3/orders", "vip express", MediaType.TEXT_PLAIN_TYPE));
        HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
            () -> post("/sel/vip/shops/3/orders", "vip lamp", MediaType.TEXT_PLAIN_TYPE));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
    }

    private static String post(String path, String body, MediaType contentType) {
        BlockingHttpClient http = client.toBlocking();
        return http.retrieve(HttpRequest.POST(path, body).contentType(contentType));
    }

    private static String postWithHeader(String path, String name, String value) {
        return client.toBlocking().retrieve(HttpRequest.POST(path, "x").header(name, value).contentType(MediaType.TEXT_PLAIN_TYPE));
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    /**
     * A stage completed later on an executor of the application, like a lookup would.
     */
    private static <T> CompletionStage<T> later(Executor executor, T value) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return value;
        }, executor);
    }

    @Introspected
    record Order(String kind, int id) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        private final Executor executor;

        Routes(@Named(TaskExecutors.BLOCKING) Executor executor) {
            this.executor = executor;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/sel/orders").where(RequestCondition.body(ORDER, order -> "express".equals(order.kind()))).order(-2)
                .body(ORDER).handle((request, pathVariables, order) -> text("express " + order.id()));
            routes.POST("/sel/orders").where(RequestCondition.body(ORDER, order -> "bulk".equals(order.kind()))).order(-1)
                .body(ORDER).handle((request, pathVariables, order) -> text("bulk " + order.id()));
            routes.POST("/sel/orders").handle((request, pathVariables) -> text("other"));

            routes.POST("/sel/number").where(RequestCondition.body(Integer.class, number -> number == 42)).order(-1).consumesAll()
                .handle((request, pathVariables) -> text("number"));
            routes.POST("/sel/number").consumesAll().handle((request, pathVariables) -> text("other"));

            routes.POST("/sel/later")
                .where(RequestCondition.bodyAsync(String.class, text -> later(executor, text.startsWith("vip"))))
                .where(RequestCondition.bodyAsync(String.class, text -> later(executor, text.contains("express"))))
                .order(-2).consumesAll()
                .handle((request, pathVariables) -> text("later both"));
            routes.POST("/sel/later")
                .where(RequestCondition.bodyAsync(String.class, text -> later(executor, text.startsWith("vip"))))
                .order(-1).consumesAll()
                .handle((request, pathVariables) -> text("later vip"));
            routes.POST("/sel/later").consumesAll().handle((request, pathVariables) -> text("other"));

            routes.POST("/sel/failing")
                .where(RequestCondition.bodyAsync(String.class, text -> switch (text) {
                    case "stage" -> CompletableFuture.failedFuture(new IllegalStateException("lookup failed"));
                    case "throw" -> throw new IllegalStateException("condition failed");
                    default -> null;
                }))
                .order(-1).consumesAll()
                .handle((request, pathVariables) -> text("failing"));
            routes.POST("/sel/failing").consumesAll().handle((request, pathVariables) -> text("other"));

            routes.serverFilter("/sel/set").preMatching().beforeReplacing(request -> switch (request.getHeaders().get("X-Set")) {
                case "text" -> request.body("text body");
                case "bytes" -> request.body(new byte[]{1, 2});
                case "order" -> request.body(new Order("set", 5));
                case null, default -> null;
            });
            routes.POST("/sel/set").where(RequestCondition.bodyText("text body"::equals)).order(-3).consumesAll()
                .handle((request, pathVariables) -> text("text"));
            routes.POST("/sel/set").where(RequestCondition.bodyBytes(bytes -> bytes.length == 2)).order(-2).consumesAll()
                .handle((request, pathVariables) -> text("bytes"));
            routes.POST("/sel/set").where(RequestCondition.body(ORDER, order -> "set".equals(order.kind()))).order(-1).consumesAll()
                .handle((request, pathVariables) -> text("order"));
            routes.POST("/sel/set").consumesAll().handle((request, pathVariables) -> text("other"));

            routes.POST("/sel/empty").where(RequestCondition.bodyText(String::isEmpty)).consumesAll()
                .handle((request, pathVariables) -> text("empty"));

            LocatedRoutes<String> shops = new LocatedRoutes<>() {
                @Override
                public Argument<String> targetType() {
                    return Argument.STRING;
                }

                @Override
                public void routes(io.micronaut.web.router.builder.LocatedHttpRouteBuilder<String> shop) {
                    shop.POST("/orders").where(RequestCondition.bodyText(text -> text.contains("express"))).consumesAll()
                        .handle((request, pathVariables, id) -> text("vip shop " + id + " express"));
                }
            };
            routes.path("/sel/vip", vip -> {
                vip.where(RequestCondition.bodyText(text -> text.startsWith("vip")));
                vip.locate("/shops/{id}", (request, pathVariables) -> pathVariables.getString("id"), shop -> shops);
            });
            routes.path("/sel/vip-async", vip -> {
                vip.where(RequestCondition.bodyText(text -> text.startsWith("vip")));
                vip.locateAsync("/shops/{id}", (request, pathVariables) ->
                    later(executor, pathVariables.getString("id")), shop -> shops);
            });
        }
    }
}
