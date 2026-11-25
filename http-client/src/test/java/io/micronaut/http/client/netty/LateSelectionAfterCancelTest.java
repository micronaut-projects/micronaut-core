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
package io.micronaut.http.client.netty;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.AsyncLoadBalancer;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.limit.ClientConcurrencyLimiter;
import io.micronaut.runtime.ApplicationConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An exchange of a capped client that is cancelled while an asynchronous load balancer selects
 * gives its permit back, and the selection that arrives later is released, not sent.
 */
class LateSelectionAfterCancelTest {

    @Test
    void aSelectionThatArrivesAfterTheCancellationIsReleased() throws Exception {
        ServiceHttpClientConfiguration configuration = new ServiceHttpClientConfiguration("late", null, null, new ApplicationConfiguration());
        configuration.setMaxConcurrentRequests(1);
        PendingLoadBalancer balancer = new PendingLoadBalancer();
        try (DefaultHttpClient client = DefaultHttpClient.builder().configuration(configuration).loadBalancer(balancer).build()) {
            ClientConcurrencyLimiter limiter = configuration.getConcurrencyLimiter();
            Assertions.assertNotNull(limiter);

            Disposable exchange = Mono.from(client.retrieve(HttpRequest.GET("/late"), String.class)).subscribe(v -> { }, e -> { });
            Assertions.assertEquals(1, limiter.getInFlight());
            exchange.dispose();
            Assertions.assertEquals(0, limiter.getInFlight());

            // the future of the balancer is not one the client can cancel: it completes anyway
            balancer.pending.complete(ServiceInstance.of("late", URI.create("http://localhost:1")));
            Thread.sleep(500);
            Assertions.assertEquals(List.of(LoadBalancer.Outcome.CANCELLED), balancer.outcomes);
            Assertions.assertEquals(0, limiter.getInFlight());
        }
    }

    static final class PendingLoadBalancer implements LoadBalancer, AsyncLoadBalancer {
        final CompletableFuture<@Nullable ServiceInstance> pending = new CompletableFuture<>();
        final List<Outcome> outcomes = new CopyOnWriteArrayList<>();

        @Override
        public CompletionStage<@Nullable ServiceInstance> selectAsync(@Nullable Object discriminator) {
            return pending;
        }

        @Override
        public Publisher<ServiceInstance> select(@Nullable Object discriminator) {
            return Publishers.empty();
        }

        @Override
        public void report(ServiceInstance serviceInstance, Outcome outcome) {
            outcomes.add(outcome);
        }
    }
}
