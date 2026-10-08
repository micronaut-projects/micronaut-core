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
package io.micronaut.discovery.config;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.async.publisher.Publishers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link CompletionStage} counterpart of {@link ConfigurationClient#getPropertySources}.
 */
class ConfigurationClientAsyncTest {

    private static final PropertySource ONE = PropertySource.of("one", Map.of("a", "1"));
    private static final PropertySource TWO = PropertySource.of("two", Map.of("b", "2"));
    private static final PropertySource THREE = PropertySource.of("three", Map.of("c", "3"));

    private final ApplicationContext context = ApplicationContext.builder().build();
    private final Environment environment = context.getEnvironment();

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void theDefaultAdapterCollectsAllThePropertySources() {
        ConfigurationClient client = new PublisherClient(Flux.just(ONE, TWO));

        assertEquals(List.of(ONE, TWO), client.getPropertySourcesAsync(environment).toCompletableFuture().getNow(null));
        assertEquals(List.of(), new PublisherClient(Publishers.empty()).getPropertySourcesAsync(environment).toCompletableFuture().getNow(null));
    }

    @Test
    void theDefaultAdapterFailsWithTheErrorOfThePublisher() {
        IllegalStateException error = new IllegalStateException("boom");
        ConfigurationClient client = new PublisherClient(Flux.just(ONE).concatWith(Mono.error(error)));

        ExecutionException e = assertThrows(ExecutionException.class, () -> client.getPropertySourcesAsync(environment).toCompletableFuture().get());
        assertSame(error, e.getCause());
    }

    @Test
    void cancellingTheDefaultAdapterCancelsTheSubscription() {
        AtomicInteger cancelled = new AtomicInteger();
        ConfigurationClient client = new PublisherClient(Flux.<PropertySource>never().doOnCancel(cancelled::incrementAndGet));

        CompletableFuture<List<PropertySource>> future = client.getPropertySourcesAsync(environment).toCompletableFuture();
        assertFalse(future.isDone());
        future.cancel(false);
        assertEquals(1, cancelled.get());
    }

    @Test
    void theCompositeConcatenatesTheClientsInOrder() {
        AsyncClient first = new AsyncClient();
        DefaultCompositeConfigurationClient composite = new DefaultCompositeConfigurationClient(new ConfigurationClient[] {
            first,
            new PublisherClient(Flux.just(THREE))
        });

        CompletableFuture<List<PropertySource>> future = composite.getPropertySourcesAsync(environment).toCompletableFuture();
        assertFalse(future.isDone());
        first.futures.get(0).complete(List.of(ONE, TWO));
        assertEquals(List.of(ONE, TWO, THREE), future.getNow(null));
    }

    @Test
    void oneFailingClientFailsTheComposite() {
        AsyncClient first = new AsyncClient();
        IllegalStateException error = new IllegalStateException("boom");
        DefaultCompositeConfigurationClient composite = new DefaultCompositeConfigurationClient(new ConfigurationClient[] {
            first,
            new PublisherClient(Mono.error(error))
        });

        ExecutionException e = assertThrows(ExecutionException.class, () -> composite.getPropertySourcesAsync(environment).toCompletableFuture().get());
        assertSame(error, e.getCause());
        // the stage of a client may be shared: it is not cancelled
        assertFalse(first.futures.get(0).isDone());
        // like the publisher
        assertSame(error, assertThrows(IllegalStateException.class, () -> Flux.from(new DefaultCompositeConfigurationClient(new ConfigurationClient[] {
            new PublisherClient(Flux.just(ONE)),
            new PublisherClient(Mono.error(error))
        }).getPropertySources(environment)).collectList().block()));
    }

    @Test
    void aCompositeWithoutClientsIsEmpty() {
        DefaultCompositeConfigurationClient composite = new DefaultCompositeConfigurationClient(new ConfigurationClient[0]);

        assertEquals(List.of(), composite.getPropertySourcesAsync(environment).toCompletableFuture().getNow(null));
    }

    @Test
    void aDefaultCompositeSubclassThatOverridesThePublisherIsCalledThroughIt() {
        AtomicInteger calls = new AtomicInteger();
        AsyncClient client = new AsyncClient();
        ConfigurationClient composite = new DefaultCompositeConfigurationClient(new ConfigurationClient[] {client}) {
            @Override
            public Publisher<PropertySource> getPropertySources(Environment environment) {
                calls.incrementAndGet();
                return Flux.just(THREE, ONE);
            }
        };

        assertEquals(List.of(THREE, ONE), composite.getPropertySourcesAsync(environment).toCompletableFuture().getNow(null));
        assertEquals(1, calls.get());
        assertTrue(client.futures.isEmpty());
    }

    @Test
    void theDefaultBeanCombinesTheStages() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            assertSame(DefaultCompositeConfigurationClient.class, ctx.getBean(ConfigurationClient.class).getClass());
        }
    }

    @Test
    void aClientWithoutStagesIsCalledThroughItsPublisher() {
        // a mock that only stubs the publisher method returns null, or a stage completed with null
        ConfigurationClient composite = new DefaultCompositeConfigurationClient(new ConfigurationClient[] {
            new MockLikeClient(null, Flux.just(ONE)),
            new MockLikeClient(CompletableFuture.completedFuture(null), Flux.just(TWO))
        });

        assertEquals(List.of(ONE, TWO), composite.getPropertySourcesAsync(environment).toCompletableFuture().getNow(null));
    }

    @Test
    void cancellingTheCompositeCancelsTheSubscriptionsOfTheClients() {
        AtomicInteger cancelled = new AtomicInteger();
        AsyncClient async = new AsyncClient();
        ConfigurationClient composite = new DefaultCompositeConfigurationClient(new ConfigurationClient[] {
            new PublisherClient(Flux.<PropertySource>never().doOnCancel(cancelled::incrementAndGet)),
            async
        });

        composite.getPropertySourcesAsync(environment).toCompletableFuture().cancel(false);

        assertEquals(1, cancelled.get());
        assertFalse(async.futures.get(0).isDone());
    }

    /**
     * Like a mock that only stubs the publisher method.
     */
    private record MockLikeClient(CompletionStage<List<PropertySource>> stage, Publisher<PropertySource> propertySources) implements ConfigurationClient {

        @Override
        public Publisher<PropertySource> getPropertySources(Environment environment) {
            return propertySources;
        }

        @Override
        public CompletionStage<List<PropertySource>> getPropertySourcesAsync(Environment environment) {
            return stage;
        }

        @Override
        public String getDescription() {
            return "mock";
        }
    }

    private record PublisherClient(Publisher<PropertySource> propertySources) implements ConfigurationClient {

        @Override
        public Publisher<PropertySource> getPropertySources(Environment environment) {
            return propertySources;
        }

        @Override
        public String getDescription() {
            return "publisher";
        }
    }

    private static final class AsyncClient implements ConfigurationClient {
        final List<CompletableFuture<List<PropertySource>>> futures = new ArrayList<>();

        @Override
        public Publisher<PropertySource> getPropertySources(Environment environment) {
            throw new UnsupportedOperationException("publisher");
        }

        @Override
        public CompletionStage<List<PropertySource>> getPropertySourcesAsync(Environment environment) {
            CompletableFuture<List<PropertySource>> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        }

        @Override
        public String getDescription() {
            return "async";
        }
    }
}
