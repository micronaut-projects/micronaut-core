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
package io.micronaut.http.server;

import io.micronaut.context.propagation.instrument.execution.ContextPropagatingExecutorService;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class CoroutineHelperTest {

    private final CoroutineHelper helper = new CoroutineHelper(List.of());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService otherExecutor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
        otherExecutor.shutdownNow();
    }

    /**
     * A coroutine dispatcher is compared by identity, so building one per request would allocate on the request
     * path and stop {@code withContext} from recognising the route's own dispatcher as the one already in effect.
     */
    @Test
    void theDispatcherIsResolvedOncePerExecutor() {
        assertSame(helper.dispatcherFor(executor), helper.dispatcherFor(executor));
    }

    @Test
    void differentExecutorsGetDifferentDispatchers() {
        assertNotSame(helper.dispatcherFor(executor), helper.dispatcherFor(otherExecutor));
    }

    /**
     * Every {@link ExecutorService} bean is instrumented, so the executor a route is assigned to is normally a
     * {@link ContextPropagatingExecutorService}. Coroutine propagation is handled by {@code KotlinCoroutinePropagation},
     * so the dispatcher must be built from the target rather than the wrapper - otherwise every resumption would
     * also capture the propagated context of whichever thread happened to dispatch it.
     */
    @Test
    void anInstrumentedExecutorResolvesToItsTargetsDispatcher() {
        ExecutorService instrumented = new ContextPropagatingExecutorService(executor);
        assertSame(helper.dispatcherFor(executor), helper.dispatcherFor(instrumented));
    }

    /**
     * The point of the change: the executor a route was assigned to becomes the dispatcher its coroutine runs and
     * resumes on, rather than {@code Dispatchers.Default}.
     */
    @Test
    void theExecutorBecomesTheCoroutinesDispatcher() {
        HttpRequest<?> request = HttpRequest.GET("/");
        Continuation<?> continuation = bindContinuation(request);

        helper.setupCoroutineContext(request, Context.empty(), PropagatedContext.empty(), executor);

        CoroutineContext.Element dispatcher = (CoroutineContext.Element) helper.dispatcherFor(executor);
        assertSame(dispatcher, continuation.getContext().get(dispatcher.getKey()));
    }

    @Test
    void withNoExecutorTheCoroutineKeepsTheDefaultDispatcher() {
        HttpRequest<?> request = HttpRequest.GET("/");
        Continuation<?> continuation = bindContinuation(request);

        helper.setupCoroutineContext(request, Context.empty(), PropagatedContext.empty());

        CoroutineContext.Element dispatcher = (CoroutineContext.Element) helper.dispatcherFor(executor);
        assertNotNull(continuation.getContext().get(dispatcher.getKey()));
        assertNotSame(dispatcher, continuation.getContext().get(dispatcher.getKey()));
    }

    private static Continuation<?> bindContinuation(HttpRequest<?> request) {
        ContinuationArgumentBinder binder = new ContinuationArgumentBinder();
        return binder.bind(ConversionContext.of(binder.argumentType()), request).getValue().orElseThrow();
    }

    @Test
    void aRequestWithNoContinuationIsLeftAlone() {
        HttpRequest<?> request = HttpRequest.GET("/");
        assertDoesNotThrow(() -> helper.setupCoroutineContext(request, Context.empty(), PropagatedContext.empty()));
        assertDoesNotThrow(() -> helper.setupCoroutineContext(request, Context.empty(), PropagatedContext.empty(), executor));
    }
}
