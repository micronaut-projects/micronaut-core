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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.web.router.RouteArguments;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A filter declared on a route, a group or a server filter, see {@link RouteFilterSpec}, with the
 * executor it runs on, which {@link FilterSpec} chooses after the filter was declared. The filter
 * of the filter chain is created when the routes are built, once: the executor is fixed then,
 * and a later change is ignored, like a change of a route the router took. The executor of a
 * filter of a group cannot change once the lambda of the group returned.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class FilterRegistration {
    private final boolean requestFilter;
    private final Function<@Nullable Supplier<Executor>, GenericHttpFilter> factory;
    private @Nullable String executorName;
    private @Nullable GenericHttpFilter created;
    private boolean fixed;
    private boolean closed;

    private FilterRegistration(boolean requestFilter, Function<@Nullable Supplier<Executor>, GenericHttpFilter> factory) {
        this.requestFilter = requestFilter;
        this.factory = factory;
    }

    /**
     * @param filter The request filter
     * @return The registration
     */
    public static FilterRegistration before(ContextReplacingRouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return new FilterRegistration(true, executor -> GenericHttpFilter.createRouteRequestFilter(filter::filter, executor));
    }

    /**
     * @param filter The asynchronous request filter
     * @return The registration
     */
    public static FilterRegistration beforeAsync(AsyncContextReplacingRouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return new FilterRegistration(true, executor -> GenericHttpFilter.createAsyncRouteRequestFilter(filter::filter, executor));
    }

    /**
     * @param filter The response filter
     * @return The registration
     */
    public static FilterRegistration after(ContextReplacingRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return new FilterRegistration(false, executor -> GenericHttpFilter.createRouteResponseFilter(filter::filter, executor));
    }

    /**
     * @param filter The asynchronous response filter
     * @return The registration
     */
    public static FilterRegistration afterAsync(AsyncContextReplacingRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return new FilterRegistration(false, executor -> GenericHttpFilter.createAsyncRouteResponseFilter(filter::filter, executor));
    }

    /**
     * @return Whether it filters the requests, otherwise the responses
     */
    public boolean isRequestFilter() {
        return requestFilter;
    }

    /**
     * Run the filter on the named executor.
     *
     * @param executorName The name of the executor
     * @see FilterSpec#executeOn(String)
     */
    synchronized void executeOn(String executorName) {
        String name = RouteArguments.executorName(executorName);
        if (canChange()) {
            this.executorName = name;
        }
    }

    /**
     * Run the filter on the thread of the filter chain.
     *
     * @see FilterSpec#nonBlocking()
     */
    synchronized void nonBlocking() {
        if (canChange()) {
            this.executorName = null;
        }
    }

    /**
     * Close the filter: the group that declares it is closed, and a change fails, like a change
     * of the settings of a closed group.
     */
    public synchronized void close() {
        closed = true;
    }

    /**
     * Fix the executor: the router took the routes of the filter, and a change is ignored, like a
     * change of the settings of a route the router took.
     */
    public synchronized void fix() {
        fixed = true;
    }

    /**
     * The filter of the filter chain, created on the first call, which fixes the executor.
     *
     * @param executors The executor of a name, looked up when the filter first runs on it
     * @return The filter
     */
    public synchronized GenericHttpFilter filter(Function<String, Supplier<Executor>> executors) {
        GenericHttpFilter filter = created;
        if (filter == null) {
            fixed = true;
            String name = executorName;
            filter = factory.apply(name == null ? null : executors.apply(name));
            created = filter;
        }
        return filter;
    }

    private boolean canChange() {
        if (closed) {
            throw new IllegalStateException("The route group is closed: declare the filters of a group, and their executors, in its lambda");
        }
        return !fixed;
    }
}
