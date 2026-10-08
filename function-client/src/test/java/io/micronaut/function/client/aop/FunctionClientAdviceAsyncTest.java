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
package io.micronaut.function.client.aop;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.function.client.DefaultFunctionDiscoveryClient;
import io.micronaut.function.client.FunctionClient;
import io.micronaut.function.client.FunctionDefinition;
import io.micronaut.function.client.FunctionDiscoveryClient;
import io.micronaut.function.client.FunctionInvoker;
import io.micronaut.function.client.FunctionInvokerChooser;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link FunctionClient} advice finds the function with
 * {@link FunctionDiscoveryClient#getFunctionAsync(String)} for the {@link CompletionStage} and the
 * blocking methods, and with the publisher for the reactive ones.
 */
class FunctionClientAdviceAsyncTest {

    static final String SPEC = "FunctionClientAdviceAsyncTest";
    static final IllegalStateException BOOM = new IllegalStateException("boom");

    private final ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC));
    private final MathClient client = context.getBean(MathClient.class);
    private final StubDiscoveryClient discoveryClient = context.getBean(StubDiscoveryClient.class);

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void aCompletionStageMethodFindsTheFunctionAsynchronously() throws Exception {
        assertEquals(42L, client.maxAsync().get());
        assertEquals(1, discoveryClient.asyncCalls.get());
        assertEquals(0, discoveryClient.publisherCalls.get());
    }

    @Test
    void aCompletionStageMethodWaitsForTheDiscovery() throws Exception {
        CompletableFuture<FunctionDefinition> pending = new CompletableFuture<>();
        discoveryClient.next.set(pending);

        CompletableFuture<Long> result = client.maxAsync();
        assertEquals(false, result.isDone());
        pending.complete(() -> "max");
        assertEquals(42L, result.get());
    }

    @Test
    void aBlockingMethodFindsTheFunctionAsynchronously() {
        assertEquals(42L, client.maxSync());
        assertEquals(1, discoveryClient.asyncCalls.get());
        assertEquals(0, discoveryClient.publisherCalls.get());
    }

    @Test
    void aReactiveMethodKeepsThePublisher() {
        assertEquals(42L, Mono.from(client.maxPublisher()).block());
        assertEquals(0, discoveryClient.asyncCalls.get());
        assertEquals(1, discoveryClient.publisherCalls.get());
    }

    @Test
    void aMissingFunctionFailsTheStageWithTheErrorAsItIs() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CompletableFuture<Long> result = client.missingAsync();
        result.whenComplete((value, throwable) -> failure.set(throwable));

        ExecutionException e = assertThrows(ExecutionException.class, result::get);
        assertInstanceOf(FunctionNotFoundException.class, e.getCause());
        assertEquals("No function found for name: missing", e.getCause().getMessage());
        assertInstanceOf(FunctionNotFoundException.class, failure.get());
    }

    @Test
    void aMissingFunctionFailsTheBlockingMethod() {
        FunctionNotFoundException e = assertThrows(FunctionNotFoundException.class, client::missingSync);
        assertEquals("No function found for name: missing", e.getMessage());
    }

    @Test
    void anEmptyDiscoveryFailsWithFunctionNotFound() {
        ExecutionException e = assertThrows(ExecutionException.class, () -> client.undiscoveredAsync().get());
        assertEquals("No function found for name: undiscovered", e.getCause().getMessage());
        FunctionNotFoundException sync = assertThrows(FunctionNotFoundException.class, client::undiscoveredSync);
        assertEquals("No function found for name: undiscovered", sync.getMessage());
    }

    @Test
    void anEmptyResultFailsTheStageWithFunctionNotFound() {
        ExecutionException e = assertThrows(ExecutionException.class, () -> client.emptyAsync().get());
        assertInstanceOf(FunctionNotFoundException.class, e.getCause());
        assertEquals("No function found for name: empty", e.getCause().getMessage());
    }

    @Test
    void anEmptyResultCompletesAVoidStage() throws Exception {
        assertEquals(null, client.emptyVoidAsync().get());
    }

    @Test
    void aFunctionWithoutInvokerFailsWithFunctionNotFound() {
        ExecutionException e = assertThrows(ExecutionException.class, () -> client.noInvokerAsync().get());
        assertEquals("No function found for name: no-invoker", e.getCause().getMessage());
        FunctionNotFoundException sync = assertThrows(FunctionNotFoundException.class, client::noInvokerSync);
        assertEquals("No function found for name: no-invoker", sync.getMessage());
    }

    @Test
    void theErrorOfTheFunctionIsThrownAsItIs() {
        ExecutionException e = assertThrows(ExecutionException.class, () -> client.failingAsync().get());
        assertSame(BOOM, e.getCause());
        assertSame(BOOM, assertThrows(IllegalStateException.class, client::failingSync));
    }

    @Test
    void theDefaultGetFunctionAsyncAdaptsThePublisher() {
        FunctionDefinition definition = () -> "max";
        FunctionDiscoveryClient publisherOnly = name -> Publishers.just(definition);
        FunctionDiscoveryClient empty = name -> Publishers.empty();
        FunctionDiscoveryClient failing = name -> Publishers.just(new FunctionNotFoundException(name));

        assertSame(definition, publisherOnly.getFunctionAsync("max").toCompletableFuture().getNow(null));
        ExecutionException e = assertThrows(ExecutionException.class, () -> empty.getFunctionAsync("max").toCompletableFuture().get());
        assertInstanceOf(FunctionNotFoundException.class, e.getCause());
        e = assertThrows(ExecutionException.class, () -> failing.getFunctionAsync("max").toCompletableFuture().get());
        assertInstanceOf(FunctionNotFoundException.class, e.getCause());
    }

    @Test
    void aDiscoveryClientWithoutStageIsCalledThroughItsPublisher() throws Exception {
        // like a mock that only stubs the publisher method
        assertEquals(42L, client.mockedAsync().get());
        assertEquals(42L, client.mockedSync());
        assertEquals(2, discoveryClient.publisherCalls.get());
    }

    @Test
    void cancellingTheStageCancelsTheLookupOfTheFramework() {
        CompletableFuture<FunctionDefinition> pending = CompletionStagePublishers.future();
        discoveryClient.next.set(pending);

        client.maxAsync().cancel(false);

        assertTrue(pending.isCancelled());
    }

    @Test
    void cancellingTheStageLeavesASharedLookupAlone() {
        CompletableFuture<FunctionDefinition> shared = new CompletableFuture<>();
        discoveryClient.next.set(shared);

        client.maxAsync().cancel(false);

        assertFalse(shared.isDone());
    }

    @FunctionClient
    @Requires(property = "spec.name", value = SPEC)
    interface MathClient {
        @Named("max")
        CompletableFuture<Long> maxAsync();

        @Named("max")
        Long maxSync();

        @Named("max")
        Publisher<Long> maxPublisher();

        @Named("missing")
        CompletableFuture<Long> missingAsync();

        @Named("missing")
        Long missingSync();

        @Named("undiscovered")
        CompletableFuture<Long> undiscoveredAsync();

        @Named("undiscovered")
        Long undiscoveredSync();

        @Named("empty")
        CompletableFuture<Long> emptyAsync();

        @Named("empty")
        CompletableFuture<Void> emptyVoidAsync();

        @Named("no-invoker")
        CompletableFuture<Long> noInvokerAsync();

        @Named("no-invoker")
        Long noInvokerSync();

        @Named("failing")
        CompletableFuture<Long> failingAsync();

        @Named("failing")
        Long failingSync();

        @Named("mocked")
        CompletableFuture<Long> mockedAsync();

        @Named("mocked")
        Long mockedSync();
    }

    /**
     * Knows every function but "missing", which fails, and "undiscovered", which completes
     * without a definition. Its stage of "mocked" is null.
     */
    @Singleton
    @Replaces(DefaultFunctionDiscoveryClient.class)
    @Requires(property = "spec.name", value = SPEC)
    static final class StubDiscoveryClient implements FunctionDiscoveryClient {
        final AtomicInteger asyncCalls = new AtomicInteger();
        final AtomicInteger publisherCalls = new AtomicInteger();
        final AtomicReference<@Nullable CompletableFuture<FunctionDefinition>> next = new AtomicReference<>();

        @Override
        public Publisher<FunctionDefinition> getFunction(String functionName) {
            publisherCalls.incrementAndGet();
            return Publishers.just(definition(functionName));
        }

        @Override
        public CompletionStage<FunctionDefinition> getFunctionAsync(String functionName) {
            asyncCalls.incrementAndGet();
            CompletableFuture<FunctionDefinition> pending = next.getAndSet(null);
            if (pending != null) {
                return pending;
            }
            return switch (functionName) {
                case "missing" -> CompletableFuture.failedFuture(new FunctionNotFoundException(functionName));
                case "undiscovered" -> CompletableFuture.completedFuture(null);
                case "mocked" -> null;
                default -> CompletableFuture.completedFuture(definition(functionName));
            };
        }

        private static FunctionDefinition definition(String functionName) {
            return () -> functionName;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static final class StubInvokerChooser implements FunctionInvokerChooser {
        private static final Set<String> KNOWN = Set.of("max", "empty", "failing", "mocked");

        @SuppressWarnings("unchecked")
        @Override
        public <I, O> Optional<FunctionInvoker<I, O>> choose(FunctionDefinition definition) {
            if (!KNOWN.contains(definition.getName())) {
                return Optional.empty();
            }
            FunctionInvoker<I, Object> invoker = (def, input, outputType) -> {
                Publisher<Long> result = switch (def.getName()) {
                    case "max", "mocked" -> Publishers.just(42L);
                    case "empty" -> Publishers.empty();
                    default -> Publishers.just(BOOM);
                };
                if (Publisher.class.equals(outputType.getType())) {
                    return result;
                }
                return Mono.from(result).block();
            };
            if (definition.getName().equals("mocked")) {
                // like a mock invoker that only stubs invoke
                FunctionInvoker<I, Object> stubbed = invoker;
                invoker = new FunctionInvoker<>() {
                    @Override
                    public Object invoke(FunctionDefinition def, I input, Argument<Object> outputType) {
                        return stubbed.invoke(def, input, outputType);
                    }

                    @Override
                    public <T> CompletionStage<T> invokeAsync(FunctionDefinition def, I input, Argument<T> valueType) {
                        return null;
                    }
                };
            }
            return Optional.of((FunctionInvoker<I, O>) invoker);
        }
    }
}
