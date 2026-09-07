/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.propagation.instrument.execution.ContextPropagatingExecutorService;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.bind.binders.HttpCoroutineContextFactory;
import jakarta.inject.Singleton;
import reactor.util.context.ContextView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import kotlin.coroutines.CoroutineContext;

/**
 * Coroutines helper.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
@Singleton
@Requires(classes = kotlin.coroutines.CoroutineContext.class)
public final class CoroutineHelper {

    private final List<HttpCoroutineContextFactory<?>> coroutineContextFactories;
    /**
     * Dispatchers are resolved once per executor rather than per request: a coroutine dispatcher is compared by
     * identity, so a new instance for every request would allocate on the request path and stop {@code withContext}
     * from recognising the route's own dispatcher. The map is bound to this singleton, and therefore to the
     * application context that owns the executors, so it does not outlive them.
     */
    private final Map<ExecutorService, CoroutineContext> dispatchers = new ConcurrentHashMap<>();

    CoroutineHelper(List<HttpCoroutineContextFactory<?>> coroutineContextFactories) {
        this.coroutineContextFactories = coroutineContextFactories;
    }

    public void setupCoroutineContext(HttpRequest<?> httpRequest, ContextView contextView, PropagatedContext propagatedContext) {
        setupCoroutineContext(httpRequest, contextView, propagatedContext, null);
    }

    /**
     * Sets up the coroutine context for a suspended route, dispatching it onto the given executor.
     *
     * @param httpRequest       The request
     * @param contextView       The reactor context
     * @param propagatedContext The propagated context
     * @param executorService   The executor the route runs on, or {@code null} to use {@code Dispatchers.Default}
     */
    public void setupCoroutineContext(HttpRequest<?> httpRequest,
                                      ContextView contextView,
                                      PropagatedContext propagatedContext,
                                      @Nullable ExecutorService executorService) {
        CoroutineContext dispatcher = executorService == null ? null : dispatcherFor(executorService);
        ContinuationArgumentBinder.setupCoroutineContext(httpRequest, contextView, propagatedContext, coroutineContextFactories, dispatcher);
    }

    private CoroutineContext dispatcherFor(ExecutorService executorService) {
        // the coroutine's own propagation is handled by KotlinCoroutinePropagation, so dispatch on the raw
        // executor rather than the instrumented wrapper, which would otherwise capture the propagated context
        // of whichever thread happened to resume the continuation
        ExecutorService target = ContextPropagatingExecutorService.unwrap(executorService).orElse(executorService);
        return dispatchers.computeIfAbsent(target, ContinuationArgumentBinder::dispatcherFor);
    }
}
