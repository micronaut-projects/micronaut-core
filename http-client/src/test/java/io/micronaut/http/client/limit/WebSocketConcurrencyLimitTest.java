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
package io.micronaut.http.client.limit;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.WebSocketSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;

import java.util.Map;

/**
 * A WebSocket connect publisher of a capped service takes its permit when it is subscribed, not
 * when it is created: a publisher that is never subscribed holds no permit.
 */
class WebSocketConcurrencyLimitTest {

    @Test
    void anUnsubscribedConnectPublisherHoldsNoPermit() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "micronaut.http.services.ws.url", "http://localhost:1",
            "micronaut.http.services.ws.max-concurrent-requests", 1))) {
            WebSocketClient client = (WebSocketClient) ctx.getBean(HttpClientRegistry.class).getClient(HttpVersion.HTTP_1_1, "ws", null);
            ClientConcurrencyLimiter limiter = ctx.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("ws")).getConcurrencyLimiter();
            Assertions.assertNotNull(limiter);
            for (int i = 0; i < 3; i++) {
                Publisher<WebSocketSession> unused = client.connect(WebSocketSession.class, HttpRequest.GET("/ws"));
                Assertions.assertNotNull(unused);
            }
            Assertions.assertEquals(0, limiter.getInFlight());
            Assertions.assertEquals(0, limiter.getAcquiredCount());
        }
    }

    @Test
    void aSubscriberThatCancelsBeforeTheHandshakeGivesThePermitBack() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "micronaut.http.services.ws.url", "http://localhost:1",
            "micronaut.http.services.ws.max-concurrent-requests", 1))) {
            WebSocketClient client = (WebSocketClient) ctx.getBean(HttpClientRegistry.class).getClient(HttpVersion.HTTP_1_1, "ws", null);
            ClientConcurrencyLimiter limiter = ctx.getBean(ServiceHttpClientConfiguration.class, Qualifiers.byName("ws")).getConcurrencyLimiter();
            Assertions.assertNotNull(limiter);
            for (int i = 0; i < 3; i++) {
                client.connect(WebSocketSession.class, HttpRequest.GET("/ws")).subscribe(new BaseSubscriber<WebSocketSession>() {
                    @Override
                    protected void hookOnSubscribe(Subscription subscription) {
                        // gone before the target reaches the handshake
                        cancel();
                    }
                });
            }
            Assertions.assertEquals(3, limiter.getAcquiredCount());
            Assertions.assertEquals(0, limiter.getInFlight());
        }
    }
}
