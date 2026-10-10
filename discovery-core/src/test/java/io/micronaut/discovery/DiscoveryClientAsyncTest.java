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
package io.micronaut.discovery;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
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
 * The {@link java.util.concurrent.CompletionStage} counterparts of the {@link DiscoveryClient}
 * publishers: the default adapters and the composite.
 */
class DiscoveryClientAsyncTest {

    private static final ServiceInstance A = ServiceInstance.of("my-service", URI.create("http://a:8080"));
    private static final ServiceInstance B = ServiceInstance.of("my-service", URI.create("http://b:8080"));
    private static final ServiceInstance C = ServiceInstance.of("my-service", URI.create("http://c:8080"));

    @Test
    void theDefaultAdaptersTakeTheSingleResult() {
        DiscoveryClient client = new PublisherClient("p", Publishers.just(List.of(A, B)), Publishers.just(List.of("my-service")));

        assertEquals(List.of(A, B), client.getInstancesAsync("my-service").toCompletableFuture().getNow(null));
        assertEquals(List.of("my-service"), client.getServiceIdsAsync().toCompletableFuture().getNow(null));
    }

    @Test
    void theDefaultAdaptersCompleteWithAnEmptyListForAnEmptyPublisher() {
        DiscoveryClient client = new PublisherClient("p", Publishers.empty(), Publishers.empty());

        assertEquals(List.of(), client.getInstancesAsync("my-service").toCompletableFuture().getNow(null));
        assertEquals(List.of(), client.getServiceIdsAsync().toCompletableFuture().getNow(null));
    }

    @Test
    void theDefaultAdaptersFailWithTheErrorOfThePublisher() {
        IllegalStateException error = new IllegalStateException("boom");
        DiscoveryClient client = new PublisherClient("p", Mono.error(error), Mono.error(error));

        ExecutionException e = assertThrows(ExecutionException.class, () -> client.getInstancesAsync("my-service").toCompletableFuture().get());
        assertSame(error, e.getCause());
        e = assertThrows(ExecutionException.class, () -> client.getServiceIdsAsync().toCompletableFuture().get());
        assertSame(error, e.getCause());
    }

    @Test
    void cancellingTheDefaultAdapterCancelsTheSubscription() {
        AtomicInteger cancelled = new AtomicInteger();
        DiscoveryClient client = new PublisherClient("p", Flux.<List<ServiceInstance>>never().doOnCancel(cancelled::incrementAndGet), Flux.never());

        CompletableFuture<List<ServiceInstance>> future = client.getInstancesAsync("my-service").toCompletableFuture();
        assertFalse(future.isDone());
        future.cancel(false);
        assertEquals(1, cancelled.get());
    }

    @Test
    void theCompositeConcatenatesTheClientsInOrder() {
        AsyncClient first = new AsyncClient("first");
        AsyncClient second = new AsyncClient("second");
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient(first, second);

        CompletableFuture<List<ServiceInstance>> instances = composite.getInstancesAsync("myService").toCompletableFuture();
        // the service ID is hyphenated, like for the publisher
        assertEquals(List.of("my-service"), first.requested);
        assertEquals(List.of("my-service"), second.requested);
        second.instances.get(0).complete(List.of(C));
        assertFalse(instances.isDone());
        first.instances.get(0).complete(List.of(A, B));
        assertEquals(List.of(A, B, C), instances.getNow(null));

        CompletableFuture<List<String>> serviceIds = composite.getServiceIdsAsync().toCompletableFuture();
        first.serviceIds.get(0).complete(List.of("x"));
        second.serviceIds.get(0).complete(List.of("y", "x"));
        // no dedup, like the publisher
        assertEquals(List.of("x", "y", "x"), serviceIds.getNow(null));
    }

    @Test
    void theCompositeAdaptsClientsThatOnlyHavePublishers() {
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient(
            new PublisherClient("one", Publishers.just(List.of(A)), Publishers.just(List.of("one"))),
            new PublisherClient("empty", Publishers.empty(), Publishers.empty()),
            new PublisherClient("two", Publishers.just(List.of(B)), Publishers.just(List.of("two")))
        );

        assertEquals(List.of(A, B), composite.getInstancesAsync("my-service").toCompletableFuture().getNow(null));
        assertEquals(List.of("one", "two"), composite.getServiceIdsAsync().toCompletableFuture().getNow(null));
        // the publishers keep their behaviour
        assertEquals(List.of(A, B), Mono.from(composite.getInstances("my-service")).block());
    }

    @Test
    void oneFailingClientFailsTheCompositeAndTheOthersAreIgnored() {
        AsyncClient first = new AsyncClient("first");
        AsyncClient second = new AsyncClient("second");
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient(first, second);
        IllegalStateException error = new IllegalStateException("boom");

        CompletableFuture<List<ServiceInstance>> instances = composite.getInstancesAsync("my-service").toCompletableFuture();
        second.instances.get(0).completeExceptionally(error);

        ExecutionException e = assertThrows(ExecutionException.class, instances::get);
        assertSame(error, e.getCause());
        // the stage of a client may be shared: it is not cancelled, its result is ignored
        assertFalse(first.instances.get(0).isDone());
        first.instances.get(0).complete(List.of(A));
        assertTrue(instances.isCompletedExceptionally());
        // the publisher fails too
        PublisherClient failing = new PublisherClient("failing", Mono.error(error), Mono.error(error));
        DiscoveryClient publishers = new DefaultCompositeDiscoveryClient(new PublisherClient("ok", Publishers.just(List.of(A)), Publishers.just(List.of())), failing);
        Mono<List<ServiceInstance>> publisherInstances = Mono.from(publishers.getInstances("my-service"));
        assertSame(error, assertThrows(IllegalStateException.class, publisherInstances::block));
        e = assertThrows(ExecutionException.class, () -> publishers.getInstancesAsync("my-service").toCompletableFuture().get());
        assertSame(error, e.getCause());
    }

    @Test
    void oneFailingClientCancelsTheSubscriptionsOfTheOthers() {
        AtomicInteger cancelled = new AtomicInteger();
        IllegalStateException error = new IllegalStateException("boom");
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient(
            new PublisherClient("never", Flux.<List<ServiceInstance>>never().doOnCancel(cancelled::incrementAndGet), Flux.never()),
            new PublisherClient("failing", Mono.error(error), Mono.error(error))
        );

        CompletableFuture<List<ServiceInstance>> instances = composite.getInstancesAsync("my-service").toCompletableFuture();

        assertTrue(instances.isCompletedExceptionally());
        assertEquals(1, cancelled.get());
    }

    @Test
    void cancellingTheCompositeCancelsTheSubscriptionsButNotTheStagesOfTheClients() {
        AtomicInteger cancelled = new AtomicInteger();
        AsyncClient async = new AsyncClient("async");
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient(
            new PublisherClient("never", Flux.never(), Flux.<List<String>>never().doOnCancel(cancelled::incrementAndGet)),
            async
        );

        composite.getServiceIdsAsync().toCompletableFuture().cancel(false);

        assertEquals(1, cancelled.get());
        assertFalse(async.serviceIds.get(0).isDone());
    }

    @Test
    void aSharedStageOfAClientIsNotCancelled() {
        CompletableFuture<List<ServiceInstance>> shared = new CompletableFuture<>();
        AsyncClient client = new AsyncClient("shared") {
            @Override
            public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
                return shared;
            }
        };
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient(client, new PublisherClient("other", Publishers.just(List.of(B)), Publishers.just(List.of())));

        composite.getInstancesAsync("my-service").toCompletableFuture().cancel(false);
        CompletableFuture<List<ServiceInstance>> second = composite.getInstancesAsync("my-service").toCompletableFuture();
        shared.complete(List.of(A));

        assertFalse(shared.isCancelled());
        assertEquals(List.of(A, B), second.getNow(null));
    }

    @Test
    void aClientWithoutStagesIsCalledThroughItsPublishers() {
        // a mock that only stubs the publisher methods returns null, or a stage completed with null
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient(
            new MockLikeClient(null, Publishers.just(List.of(A)), Publishers.just(List.of("a"))),
            new MockLikeClient(CompletableFuture.completedFuture(null), Publishers.just(List.of(B)), Publishers.just(List.of("b")))
        );

        assertEquals(List.of(A, B), composite.getInstancesAsync("my-service").toCompletableFuture().getNow(null));
        assertEquals(List.of("a", "b"), composite.getServiceIdsAsync().toCompletableFuture().getNow(null));
        CompletableFuture<Object> later = new CompletableFuture<>();
        DiscoveryClient single = new DefaultCompositeDiscoveryClient(new MockLikeClient(later, Publishers.just(List.of(C)), Publishers.just(List.of("c"))));
        CompletableFuture<List<ServiceInstance>> instances = single.getInstancesAsync("my-service").toCompletableFuture();
        CompletableFuture<List<String>> serviceIds = single.getServiceIdsAsync().toCompletableFuture();
        later.complete(null);
        assertEquals(List.of(C), instances.getNow(null));
        assertEquals(List.of("c"), serviceIds.getNow(null));
    }

    @Test
    void aCompositeWithoutClientsIsEmpty() {
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient();

        assertEquals(List.of(), composite.getInstancesAsync("my-service").toCompletableFuture().getNow(null));
        assertEquals(List.of(), composite.getServiceIdsAsync().toCompletableFuture().getNow(null));
    }

    @Test
    void aCompositeSubclassThatOnlyOverridesThePublishersKeepsThem() {
        AtomicInteger calls = new AtomicInteger();
        DiscoveryClient composite = new CompositeDiscoveryClient(new DiscoveryClient[] {new PublisherClient("one", Publishers.just(List.of(A)), Publishers.just(List.of("one")))}) {
            @Override
            public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
                calls.incrementAndGet();
                return super.getInstances(serviceId);
            }
        };

        assertEquals(List.of(A), composite.getInstancesAsync("my-service").toCompletableFuture().join());
        assertEquals(1, calls.get());
    }

    @Test
    void aDefaultCompositeSubclassThatOverridesThePublishersIsCalledThroughThem() {
        AtomicInteger instancesCalls = new AtomicInteger();
        AtomicInteger serviceIdsCalls = new AtomicInteger();
        // the stages of the client must not be used, the subclass decides
        AsyncClient client = new AsyncClient("async");
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient(client) {
            @Override
            public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
                instancesCalls.incrementAndGet();
                return Publishers.just(List.of(C));
            }

            @Override
            public Publisher<List<String>> getServiceIds() {
                serviceIdsCalls.incrementAndGet();
                return Publishers.just(List.of("custom"));
            }
        };

        assertEquals(List.of(C), composite.getInstancesAsync("my-service").toCompletableFuture().getNow(null));
        assertEquals(List.of("custom"), composite.getServiceIdsAsync().toCompletableFuture().getNow(null));
        assertEquals(1, instancesCalls.get());
        assertEquals(1, serviceIdsCalls.get());
        assertTrue(client.requested.isEmpty());
    }

    @Test
    void theCompositeWithOneClientReturnsItsStage() {
        AsyncClient client = new AsyncClient("only");
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient(client);

        CompletableFuture<List<ServiceInstance>> instances = composite.getInstancesAsync("myService").toCompletableFuture();

        assertEquals(List.of("my-service"), client.requested);
        client.instances.get(0).complete(List.of(A));
        assertEquals(List.of(A), instances.getNow(null));
    }

    @Test
    void cancellingThePublisherOfTheCompositeCancelsTheClients() {
        AtomicInteger cancelled = new AtomicInteger();
        PublisherClient never = new PublisherClient("never",
            Flux.<List<ServiceInstance>>never().doOnCancel(cancelled::incrementAndGet),
            Flux.<List<String>>never().doOnCancel(cancelled::incrementAndGet));
        DiscoveryClient composite = new DefaultCompositeDiscoveryClient(never, new PublisherClient("other", Flux.never(), Flux.never()));

        Flux.from(composite.getInstances("my-service")).subscribe().dispose();
        Flux.from(composite.getServiceIds()).subscribe().dispose();

        assertEquals(2, cancelled.get());
    }

    @Test
    void theDefaultBeanCombinesTheStages() {
        try (ApplicationContext context = ApplicationContext.run()) {
            assertSame(DefaultCompositeDiscoveryClient.class, context.getBean(DiscoveryClient.class).getClass());
        }
    }

    @Test
    void aReplacementOfTheDefaultCompositeIsCalledThroughItsPublishers() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "DiscoveryClientAsyncTest"))) {
            DiscoveryClient discoveryClient = context.getBean(DiscoveryClient.class);
            assertTrue(discoveryClient instanceof ReplacedCompositeDiscoveryClient);
            assertEquals(List.of(C), discoveryClient.getInstancesAsync("my-service").toCompletableFuture().getNow(null));
        }
    }

    /**
     * A subclass that replaces the default composite, as the caching composite of micronaut-cache does.
     */
    @Primary
    @Singleton
    @Replaces(DefaultCompositeDiscoveryClient.class)
    @Requires(property = "spec.name", value = "DiscoveryClientAsyncTest")
    static class ReplacedCompositeDiscoveryClient extends DefaultCompositeDiscoveryClient {
        ReplacedCompositeDiscoveryClient() {
            super(new DiscoveryClient[0]);
        }

        @Override
        public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            return Publishers.just(List.of(C));
        }
    }

    /**
     * Like a mock that only stubs the publisher methods.
     */
    private record MockLikeClient(CompletableFuture<?> stage, Publisher<List<ServiceInstance>> instances, Publisher<List<String>> serviceIds) implements DiscoveryClient {

        @Override
        public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            return instances;
        }

        @Override
        public Publisher<List<String>> getServiceIds() {
            return serviceIds;
        }

        @Override
        @SuppressWarnings("unchecked")
        public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
            return (CompletionStage<List<ServiceInstance>>) stage;
        }

        @Override
        @SuppressWarnings("unchecked")
        public CompletionStage<List<String>> getServiceIdsAsync() {
            return (CompletionStage<List<String>>) stage;
        }

        @Override
        public String getDescription() {
            return "mock";
        }

        @Override
        public void close() {
            // the test client holds no resources
        }
    }

    private record PublisherClient(String name, Publisher<List<ServiceInstance>> instances, Publisher<List<String>> serviceIds) implements DiscoveryClient {

        @Override
        public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            return instances;
        }

        @Override
        public Publisher<List<String>> getServiceIds() {
            return serviceIds;
        }

        @Override
        public String getDescription() {
            return name;
        }

        @Override
        public void close() {
            // the test client holds no resources
        }
    }

    /**
     * Fails the publisher methods, so that only the stages can serve.
     */
    private static class AsyncClient implements DiscoveryClient {
        final String name;
        final List<String> requested = new ArrayList<>();
        final List<CompletableFuture<List<ServiceInstance>>> instances = new ArrayList<>();
        final List<CompletableFuture<List<String>>> serviceIds = new ArrayList<>();

        AsyncClient(String name) {
            this.name = name;
        }

        @Override
        public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            throw new UnsupportedOperationException("publisher");
        }

        @Override
        public Publisher<List<String>> getServiceIds() {
            throw new UnsupportedOperationException("publisher");
        }

        @Override
        public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
            requested.add(serviceId);
            CompletableFuture<List<ServiceInstance>> future = new CompletableFuture<>();
            instances.add(future);
            return future;
        }

        @Override
        public CompletionStage<List<String>> getServiceIdsAsync() {
            CompletableFuture<List<String>> future = new CompletableFuture<>();
            serviceIds.add(future);
            return future;
        }

        @Override
        public String getDescription() {
            return name;
        }

        @Override
        public void close() {
            // the test client holds no resources
        }
    }
}
