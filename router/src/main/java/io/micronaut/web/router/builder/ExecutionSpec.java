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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;

/**
 * Where code runs: the handler of a route, see {@link HttpRouteSpec}, the handlers of the routes
 * of a group, see {@link HttpRouteGroup}, or a filter, see {@link FilterSpec}. Every kind of code
 * chooses its thread with the same methods, declared once here: a way to choose a thread that is
 * added to this interface, e.g. a future {@code executeOnIO()}, is available to the routes, the
 * groups and the filters at once.
 *
 * <p>On a route, the choice is the one of the route, like {@code @ExecuteOn} or
 * {@code @NonBlocking} on a controller method. On a group, it is the default of the routes
 * declared in the lambda of the group, and of its nested groups, wherever it is declared in the
 * lambda: a route that chooses its thread, or a nested group that does, overrides it. On a
 * filter, it is the choice of that filter only, like {@code @ExecuteOn} on a filter method. The
 * last of {@code executeOn} and {@code nonBlocking} called wins.</p>
 *
 * <pre>{@code
 * routes.path("/reports", reports -> {
 *     reports.executeOn(TaskExecutors.BLOCKING);
 *     reports.before(request -> audit.record(request)).executeOn(TaskExecutors.IO);
 *     reports.GET("/{id}", (request, pathVariables) -> HttpResponse.ok(repository.find(pathVariables.getLong("id"))));
 *     reports.GET("/count", (request, pathVariables) -> HttpResponse.ok(cache.count())).nonBlocking();
 * });
 * }</pre>
 *
 * @param <S> The type that continues the declaration: the route, the group or the filter
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface ExecutionSpec<S extends ExecutionSpec<S>> permits RouteSpec, FilterSpec {

    /**
     * Run the code on the named executor, like {@code @ExecuteOn}. It applies whatever the thread
     * selection of the server.
     *
     * <p>On a route, the handler runs on the executor, like {@code @ExecuteOn} on a controller
     * method. On a group, the routes of the group, and of its nested groups, run on it, like
     * {@code @ExecuteOn} on a controller, unless they, or a nested group, choose their thread.
     * The media types and the executor of a group apply to its routes to handlers, including the
     * implicit {@code HEAD} routes, the routes declared for several methods and the declared
     * routes, not to its locator routes, whose located routes declare their own, nor to its error
     * and status routes.</p>
     *
     * <p>On a filter, the filter runs on the executor, like a filter method annotated
     * {@code @ExecuteOn}: use it for a filter that blocks, e.g. on a database. The filter chain
     * continues on that executor. An asynchronous filter is called on the executor, and the
     * filter chain continues where its stage completes.</p>
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @return The route, the group or the filter
     */
    S executeOn(String executorName);

    /**
     * Run the code without switching the thread. On a route, the handler runs on the event loop,
     * like {@code @NonBlocking} on a controller method, when the server selects threads
     * automatically: the route must not block. On a group, the default of its routes, like
     * {@link #executeOn(String)}. On a filter, the default: the filter runs on the thread of the
     * filter chain, like a filter method without {@code @ExecuteOn}.
     *
     * @return The route, the group or the filter
     */
    S nonBlocking();
}
