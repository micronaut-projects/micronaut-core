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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.web.router.RouteAssembly;

import java.util.Objects;

/**
 * The {@link ServerFilterSpec}: a server filter of the {@link RouteAssembly}, whose filter routes
 * the router reads with the routes. The server filter holds the configuration.
 *
 * @param serverFilters The server filter
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultServerFilterSpec(RouteAssembly.ServerFilters serverFilters) implements ServerFilterSpec, ContextFilterSpec<ServerFilterSpec> {

    @Override
    public ServerFilterSpec methods(HttpMethod... methods) {
        serverFilters.methods(Objects.requireNonNull(methods, "methods"));
        return this;
    }

    @Override
    public ServerFilterSpec order(int order) {
        serverFilters.order(order);
        return this;
    }

    @Override
    public ServerFilterSpec patternStyle(FilterPatternStyle patternStyle) {
        serverFilters.patternStyle(patternStyle);
        return this;
    }

    @Override
    public ServerFilterSpec appendContextPath(boolean appendContextPath) {
        serverFilters.appendContextPath(appendContextPath);
        return this;
    }

    @Override
    public ServerFilterSpec preMatching() {
        serverFilters.preMatching();
        return this;
    }

    @Override
    public FilterSpec<ServerFilterSpec> addFilter(FilterRegistration filter) {
        serverFilters.filters().add(filter);
        return new DefaultFilterSpec<>(this, filter);
    }
}
