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
import io.micronaut.web.router.RouteAssembly;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The {@link HttpRouteGroup}: the routes it adds carry its filters, which it collects until its
 * lambda returns.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultHttpRouteGroup extends AbstractHttpRouteBuilder implements HttpRouteGroup, ContextFilterSpec<HttpRouteGroup> {

    private final RouteAssembly.RouteFilters filters;

    /**
     * @param assembly The assembly the routes are added to
     * @param filters  The filters of the group
     * @param prefix   The prefix of the URI templates of the routes, or {@code null}
     */
    DefaultHttpRouteGroup(RouteAssembly assembly, RouteAssembly.RouteFilters filters, @Nullable RoutePrefix prefix) {
        super(assembly, filters, prefix);
        this.filters = filters;
    }

    /**
     * Close the group: its lambda returned.
     */
    void close() {
        filters.close();
    }

    @Override
    public HttpRouteGroup before(ContextRouteRequestFilter filter) {
        filters.before(filter, null);
        return this;
    }

    @Override
    public HttpRouteGroup before(String executorName, ContextRouteRequestFilter filter) {
        filters.before(filter, Objects.requireNonNull(executorName, "executorName"));
        return this;
    }

    @Override
    public HttpRouteGroup beforeAsync(AsyncContextRouteRequestFilter filter) {
        filters.beforeAsync(filter);
        return this;
    }

    @Override
    public HttpRouteGroup after(ContextRouteResponseFilter filter) {
        filters.after(filter, null);
        return this;
    }

    @Override
    public HttpRouteGroup after(String executorName, ContextRouteResponseFilter filter) {
        filters.after(filter, Objects.requireNonNull(executorName, "executorName"));
        return this;
    }

    @Override
    public HttpRouteGroup afterAsync(AsyncContextRouteResponseFilter filter) {
        filters.afterAsync(filter);
        return this;
    }
}
