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
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code respond} route with a supplier or a function runs on the executor of its route or
 * group, and on the event loop when it has none. A constant response runs no code of the
 * application: it is answered on the event loop.
 */
class HandlerRouteRespondExecutorTest {

    private static final String LOOP = "default-eventLoopGroup";

    @Test
    void aSupplierRunsOnTheExecutorOfItsGroup() {
        assertFalse(thread("/blocking/supplier").startsWith(LOOP), "the blocking executor of the group");
        assertFalse(thread("/blocking/function").startsWith(LOOP), "the blocking executor of the group");
    }

    @Test
    void aSupplierWithoutAnExecutorRunsOnTheEventLoop() {
        assertTrue(thread("/plain/supplier").startsWith(LOOP));
    }

    @Test
    void aConstantResponseIsAnsweredOnTheEventLoop() {
        assertTrue(thread("/blocking/constant").startsWith(LOOP), "a constant response runs no application code");
    }

    private static String thread(String path) {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "HandlerRouteRespondExecutorTest", "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET(path), String.class);
                return response.header("X-Thread");
            }
        }
    }

    private static HttpResponse<?> answer() {
        return HttpResponse.ok("ok").contentType(MediaType.TEXT_PLAIN_TYPE).header("X-Thread", Thread.currentThread().getName());
    }

    @Factory
    @Requires(property = "spec.name", value = "HandlerRouteRespondExecutorTest")
    static class Routes {
        @Singleton
        HttpRoutes routes() {
            return routes -> {
                routes.path("/blocking", group -> {
                    group.executeOn(TaskExecutors.BLOCKING);
                    group.GET("/supplier").respond(HandlerRouteRespondExecutorTest::answer);
                    group.GET("/function").respond(pathVariables -> answer());
                    group.GET("/constant").respond(HttpResponse.ok("ok").contentType(MediaType.TEXT_PLAIN_TYPE));
                    // a filter records the thread the constant response was answered on
                    group.after((request, response) -> {
                        if (response.header("X-Thread") == null) {
                            response.header("X-Thread", Thread.currentThread().getName());
                        }
                    });
                });
                routes.path("/plain", group -> group.GET("/supplier").respond(HandlerRouteRespondExecutorTest::answer));
            };
        }
    }
}
