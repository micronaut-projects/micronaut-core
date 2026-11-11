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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RouteCondition;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code request} of a {@link RouteCondition} expression is the request of the
 * {@link io.micronaut.http.expression.RequestConditionContext}, also when the method has a
 * parameter named {@code request}: the method arguments are not part of the evaluation context.
 */
class RouteConditionRequestParameterTest {

    @Test
    void theConditionReadsTheRequestOfTheContextNotTheParameter() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "RouteConditionRequestParameterTest", "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                assertEquals("v2 /condition", client.toBlocking().retrieve(HttpRequest.GET("/condition?v=2")));
                assertEquals("v1 /condition", client.toBlocking().retrieve(HttpRequest.GET("/condition")));
            }
        }
    }

    @Requires(property = "spec.name", value = "RouteConditionRequestParameterTest")
    @Controller("/condition")
    static class ConditionController {

        @Get
        @RouteCondition("#{request.parameters.getFirst('v').orElse(null) != '2'}")
        String v1(HttpRequest<?> request) {
            return "v1 " + request.getPath();
        }

        @Get
        @RouteCondition("#{request.parameters.getFirst('v').orElse(null) == '2'}")
        String v2(HttpRequest<?> request) {
            return "v2 " + request.getPath();
        }
    }

    @Requires(property = "spec.name", value = "RouteConditionRequestParameterTest")
    @ServerWebSocket("/condition/ws")
    static class ConditionWebSocket {

        @OnMessage
        @RouteCondition("#{request.path.startsWith('/condition')}")
        String onMessage(String request) {
            return request;
        }
    }
}
