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
package io.micronaut.function.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.function.LocalFunctionRegistry;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import io.micronaut.health.HealthStatus;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultFunctionDiscoveryClient#getFunctionAsync(String)} looks the functions up with the
 * {@link CompletionStage}s of the discovery client.
 */
class DefaultFunctionDiscoveryClientAsyncTest {

    private static ServiceInstance instance(String host, HealthStatus status, Map<String, String> metadata) {
        return ServiceInstance.builder("svc", URI.create("http://" + host + ":8080/")).status(status).metadata(metadata).build();
    }

    @Test
    void aLocalDefinitionCompletesRightAway() {
        FunctionDefinition local = () -> "local";
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient();
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0], local);

        assertSame(local, client.getFunctionAsync("local").toCompletableFuture().getNow(null));
        assertEquals(0, discoveryClient.serviceIds.size());
    }

    @Test
    void theFirstInstanceThatIsUpAndOffersTheFunctionIsSelected() {
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient();
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]);
        String key = LocalFunctionRegistry.FUNCTION_PREFIX + "max";

        CompletableFuture<FunctionDefinition> future = client.getFunctionAsync("max").toCompletableFuture();
        assertFalse(future.isDone());
        discoveryClient.serviceIds.get(0).complete(List.of("other", "math"));
        assertEquals(List.of("other", "math"), discoveryClient.requested);
        discoveryClient.instances.get(1).complete(List.of(
            instance("down", HealthStatus.DOWN, Map.of(key, "/max")),
            instance("up", HealthStatus.UP, Map.of(key, "/max"))
        ));
        // the first lookup with a match completes the function, like the first item of the publisher
        assertTrue(future.isDone());
        discoveryClient.instances.get(0).complete(List.of(instance("plain", HealthStatus.UP, Map.of())));

        FunctionDefinition definition = future.getNow(null);
        assertEquals("max", definition.getName());
        assertEquals(Optional.of(URI.create("http://up:8080/max")), definition.getURI());
    }

    @Test
    void noInstanceFailsWithFunctionNotFound() {
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient();
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]);

        CompletableFuture<FunctionDefinition> future = client.getFunctionAsync("max").toCompletableFuture();
        discoveryClient.serviceIds.get(0).complete(List.of("math"));
        discoveryClient.instances.get(0).complete(List.of(instance("plain", HealthStatus.UP, Map.of())));

        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(FunctionNotFoundException.class, e.getCause());
        assertEquals("No function found for name: max", e.getCause().getMessage());
        // like the publisher
        DefaultFunctionDiscoveryClient publisherClient = new DefaultFunctionDiscoveryClient(new DiscoveryClient() {
            @Override
            public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
                return Mono.just(List.of(instance("plain", HealthStatus.UP, Map.of())));
            }

            @Override
            public Publisher<List<String>> getServiceIds() {
                return Mono.just(List.of("math"));
            }

            @Override
            public String getDescription() {
                return "publisher";
            }

            @Override
            public void close() {
                // the test client holds no resources
            }
        }, new FunctionDefinitionProvider[0]);
        assertThrows(FunctionNotFoundException.class, () -> Mono.from(publisherClient.getFunction("max")).block());
    }

    @Test
    void aFailingLookupFailsTheFunction() {
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient();
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]);
        IllegalStateException error = new IllegalStateException("boom");

        CompletableFuture<FunctionDefinition> future = client.getFunctionAsync("max").toCompletableFuture();
        discoveryClient.serviceIds.get(0).completeExceptionally(error);

        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertSame(error, e.getCause());
    }

    @Test
    void cancellingTheLookupCancelsTheServiceIds() {
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient();
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]);

        client.getFunctionAsync("max").toCompletableFuture().cancel(false);

        assertTrue(discoveryClient.serviceIds.get(0).isCancelled());
    }

    @Test
    void aServiceThatOffersTheFunctionCompletesTheLookupWithoutWaitingForTheOthers() {
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient();
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]);
        String key = LocalFunctionRegistry.FUNCTION_PREFIX + "max";

        CompletableFuture<FunctionDefinition> future = client.getFunctionAsync("max").toCompletableFuture();
        discoveryClient.serviceIds.get(0).complete(List.of("hanging", "math", "failing"));
        discoveryClient.instances.get(1).complete(List.of(instance("up", HealthStatus.UP, Map.of(key, "/max"))));

        assertEquals(Optional.of(URI.create("http://up:8080/max")), future.getNow(null).getURI());
        // the lookups that are no longer needed are cancelled, a later failure is ignored
        assertTrue(discoveryClient.instances.get(0).isCancelled());
        assertTrue(discoveryClient.instances.get(2).isCancelled());
        discoveryClient.instances.get(2).completeExceptionally(new IllegalStateException("late"));
        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void aServiceThatFailsBeforeAMatchFailsTheLookupAndCancelsTheOthers() {
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient();
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]);
        IllegalStateException error = new IllegalStateException("boom");

        CompletableFuture<FunctionDefinition> future = client.getFunctionAsync("max").toCompletableFuture();
        discoveryClient.serviceIds.get(0).complete(List.of("hanging", "failing"));
        discoveryClient.instances.get(1).completeExceptionally(error);

        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertSame(error, e.getCause());
        assertTrue(discoveryClient.instances.get(0).isCancelled());
    }

    @Test
    void aMatchThatIsAlreadyKnownStopsTheLookup() {
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient();
        discoveryClient.completeInstancesWith = List.of(instance("up", HealthStatus.UP, Map.of(LocalFunctionRegistry.FUNCTION_PREFIX + "max", "/max")));
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]);

        CompletableFuture<FunctionDefinition> future = client.getFunctionAsync("max").toCompletableFuture();
        discoveryClient.serviceIds.get(0).complete(List.of("math", "other"));

        assertEquals("max", future.getNow(null).getName());
        // like the publisher, which the first item cancels before the next service is looked up
        assertEquals(List.of("math"), discoveryClient.requested);
    }

    @Test
    void cancellingTheLookupCancelsTheInstanceLookups() {
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient();
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]);

        CompletableFuture<FunctionDefinition> future = client.getFunctionAsync("max").toCompletableFuture();
        discoveryClient.serviceIds.get(0).complete(List.of("a", "b"));
        future.cancel(false);

        assertTrue(discoveryClient.instances.get(0).isCancelled());
        assertTrue(discoveryClient.instances.get(1).isCancelled());
    }

    @Test
    void aSubclassThatOverridesThePublisherIsCalledThroughIt() {
        AtomicInteger calls = new AtomicInteger();
        FunctionDefinition custom = () -> "custom";
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient();
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]) {
            @Override
            public Publisher<FunctionDefinition> getFunction(String functionName) {
                calls.incrementAndGet();
                return Mono.just(custom);
            }
        };

        assertSame(custom, client.getFunctionAsync("max").toCompletableFuture().getNow(null));
        assertEquals(1, calls.get());
        assertTrue(discoveryClient.serviceIds.isEmpty());
    }

    @Test
    void theDefaultBeanLooksUpWithoutAPublisher() {
        try (ApplicationContext context = ApplicationContext.run()) {
            assertSame(DefaultFunctionDiscoveryClient.class, context.getBean(FunctionDiscoveryClient.class).getClass());
        }
    }

    @Test
    void theSharedStagesOfTheDiscoveryClientAreNotCancelled() {
        CompletableFuture<List<ServiceInstance>> shared = new CompletableFuture<>();
        AsyncDiscoveryClient discoveryClient = new AsyncDiscoveryClient() {
            @Override
            public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
                return serviceId.equals("shared") ? shared : super.getInstancesAsync(serviceId);
            }
        };
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]);
        String key = LocalFunctionRegistry.FUNCTION_PREFIX + "max";

        CompletableFuture<FunctionDefinition> future = client.getFunctionAsync("max").toCompletableFuture();
        discoveryClient.serviceIds.get(0).complete(List.of("shared", "math"));
        discoveryClient.instances.get(0).complete(List.of(instance("up", HealthStatus.UP, Map.of(key, "/max"))));

        assertEquals("max", future.getNow(null).getName());
        assertFalse(shared.isDone());
    }

    @Test
    void aDiscoveryClientWithoutStagesIsCalledThroughItsPublishers() {
        String key = LocalFunctionRegistry.FUNCTION_PREFIX + "max";
        // like a mock that only stubs the publisher methods
        DiscoveryClient discoveryClient = new DiscoveryClient() {
            @Override
            public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
                return Mono.just(List.of(instance("up", HealthStatus.UP, Map.of(key, "/max"))));
            }

            @Override
            public Publisher<List<String>> getServiceIds() {
                return Mono.just(List.of("math"));
            }

            @Override
            public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<List<String>> getServiceIdsAsync() {
                return null;
            }

            @Override
            public String getDescription() {
                return "mock";
            }

            @Override
            public void close() {
                // the test client holds no resources
            }
        };
        DefaultFunctionDiscoveryClient client = new DefaultFunctionDiscoveryClient(discoveryClient, new FunctionDefinitionProvider[0]);

        assertEquals(Optional.of(URI.create("http://up:8080/max")), client.getFunctionAsync("max").toCompletableFuture().join().getURI());
    }

    @Test
    void theDefaultAdapterFailsWithFunctionNotFoundForAnEmptyPublisher() {
        FunctionDiscoveryClient client = functionName -> Mono.empty();

        ExecutionException e = assertThrows(ExecutionException.class, () -> client.getFunctionAsync("max").toCompletableFuture().get());
        assertInstanceOf(FunctionNotFoundException.class, e.getCause());
    }

    /**
     * Fails the publisher methods, so that only the stages can serve. Its stages are new for
     * each call, so the framework may cancel them.
     */
    private static class AsyncDiscoveryClient implements DiscoveryClient {
        final List<String> requested = new ArrayList<>();
        final List<CompletableFuture<List<ServiceInstance>>> instances = new ArrayList<>();
        final List<CompletableFuture<List<String>>> serviceIds = new ArrayList<>();

        @Override
        public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            throw new UnsupportedOperationException("publisher");
        }

        @Override
        public Publisher<List<String>> getServiceIds() {
            throw new UnsupportedOperationException("publisher");
        }

        List<ServiceInstance> completeInstancesWith;

        @Override
        public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
            requested.add(serviceId);
            CompletableFuture<List<ServiceInstance>> future = CompletionStagePublishers.future();
            if (completeInstancesWith != null) {
                future.complete(completeInstancesWith);
            }
            instances.add(future);
            return future;
        }

        @Override
        public CompletionStage<List<String>> getServiceIdsAsync() {
            CompletableFuture<List<String>> future = CompletionStagePublishers.future();
            serviceIds.add(future);
            return future;
        }

        @Override
        public String getDescription() {
            return "async";
        }

        @Override
        public void close() {
            // the test client holds no resources
        }
    }
}
