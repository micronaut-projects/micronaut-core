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

import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.web.router.RouteAssembly;
import org.jspecify.annotations.Nullable;

/**
 * The {@link HttpRouteBuilder}: adds the routes to handler functions to a {@link RouteAssembly},
 * like the legacy route builder adds its routes, without depending on it. It has no route filter
 * methods: it is shared by the {@link HttpRoutes} beans, and a group, see {@link #group}, is the
 * scope of route filters.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultHttpRouteBuilder extends AbstractHttpRouteBuilder {

    /**
     * @param assembly The assembly the routes are added to
     */
    public DefaultHttpRouteBuilder(RouteAssembly assembly) {
        this(assembly, null);
    }

    /**
     * @param assembly            The assembly the routes are added to
     * @param placeholderResolver Resolves the placeholders of the ports given as strings, see
     *                            {@link HttpRouteSpec#port(String)}, or {@code null} without an environment
     */
    public DefaultHttpRouteBuilder(RouteAssembly assembly, @Nullable PropertyPlaceholderResolver placeholderResolver) {
        super(assembly, null, null, null, placeholderResolver);
    }

    /**
     * Close the builder once the routes were declared on it, e.g. when
     * {@link HttpRoutes#routes(HttpRouteBuilder)} returned: a route, a group, an error or status
     * route or a server filter declared on it later fails with an
     * {@link IllegalStateException}, instead of being dropped.
     */
    public void close() {
        closeBuilder();
    }
}
