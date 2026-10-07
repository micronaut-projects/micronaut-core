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
        DiscoveryClient composite = new AsyncCompositeDiscoveryClient(first, second);

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
        DiscoveryClient composite = new AsyncCompositeDiscoveryClient(
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
    void oneFailingClientFailsTheCompositeAndCancelsTheOthers() {
        AsyncClient first = new AsyncClient("first");
        AsyncClient second = new AsyncClient("second");
        DiscoveryClient composite = new AsyncCompositeDiscoveryClient(first, second);
        IllegalStateException error = new IllegalStateException("boom");

        CompletableFuture<List<ServiceInstance>> instances = composite.getInstancesAsync("my-service").toCompletableFuture();
        second.instances.get(0).completeExceptionally(error);

        ExecutionException e = assertThrows(ExecutionException.class, instances::get);
        assertSame(error, e.getCause());
        assertTrue(first.instances.get(0).isCancelled());
        // the publisher fails too
        PublisherClient failing = new PublisherClient("failing", Mono.error(error), Mono.error(error));
        DiscoveryClient publishers = new AsyncCompositeDiscoveryClient(new PublisherClient("ok", Publishers.just(List.of(A)), Publishers.just(List.of())), failing);
        assertSame(error, assertThrows(IllegalStateException.class, () -> Mono.from(publishers.getInstances("my-service")).block()));
        e = assertThrows(ExecutionException.class, () -> publishers.getInstancesAsync("my-service").toCompletableFuture().get());
        assertSame(error, e.getCause());
    }

    @Test
    void cancellingTheCompositeCancelsTheClients() {
        AsyncClient first = new AsyncClient("first");
        AsyncClient second = new AsyncClient("second");
        DiscoveryClient composite = new AsyncCompositeDiscoveryClient(first, second);

        composite.getServiceIdsAsync().toCompletableFuture().cancel(false);

        assertTrue(first.serviceIds.get(0).isCancelled());
        assertTrue(second.serviceIds.get(0).isCancelled());
    }

    @Test
    void aCompositeWithoutClientsIsEmpty() {
        DiscoveryClient composite = new AsyncCompositeDiscoveryClient();

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
        DiscoveryClient composite = new AsyncCompositeDiscoveryClient(client);

        CompletableFuture<List<ServiceInstance>> instances = composite.getInstancesAsync("myService").toCompletableFuture();

        assertEquals(List.of("my-service"), client.requested);
        assertSame(client.instances.get(0), instances);
    }

    @Test
    void cancellingThePublisherOfTheCompositeCancelsTheClients() {
        AtomicInteger cancelled = new AtomicInteger();
        PublisherClient never = new PublisherClient("never",
            Flux.<List<ServiceInstance>>never().doOnCancel(cancelled::incrementAndGet),
            Flux.<List<String>>never().doOnCancel(cancelled::incrementAndGet));
        DiscoveryClient composite = new AsyncCompositeDiscoveryClient(never, new PublisherClient("other", Flux.never(), Flux.never()));

        Flux.from(composite.getInstances("my-service")).subscribe().dispose();
        Flux.from(composite.getServiceIds()).subscribe().dispose();

        assertEquals(2, cancelled.get());
    }

    @Test
    void theDefaultBeanCombinesTheStages() {
        try (ApplicationContext context = ApplicationContext.run()) {
            assertTrue(context.getBean(DiscoveryClient.class) instanceof AsyncCompositeDiscoveryClient);
            assertTrue(context.getBean(DefaultCompositeDiscoveryClient.class) instanceof AsyncCompositeDiscoveryClient);
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
        }
    }

    /**
     * Fails the publisher methods, so that only the stages can serve.
     */
    private static final class AsyncClient implements DiscoveryClient {
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
        }
    }
}
