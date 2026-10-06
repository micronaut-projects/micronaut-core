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

import java.util.function.Consumer;

/**
 * The {@link HttpRouteGroup}: the routes it adds carry its filters, which it collects until its
 * lambda returns.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultHttpRouteGroup extends AbstractHttpRouteGroup<HttpRouteGroup> implements HttpRouteGroup {

    /**
     * @param assembly The assembly the routes are added to
     * @param filters  The filters of the group
     * @param settings The other settings of the group
     * @param defaults The media types and the executor of the group
     * @param prefix   The prefix of the URI templates of the routes, or {@code null}
     * @param placeholderResolver Resolves the placeholders of the ports given as strings, or {@code null}
     */
    DefaultHttpRouteGroup(RouteAssembly assembly, RouteAssembly.RouteFilters filters, RouteAssembly.RouteGroup settings,
                          RouteGroupDefaults defaults, @Nullable RoutePrefix prefix, @Nullable PropertyPlaceholderResolver placeholderResolver) {
        super(assembly, filters, settings, defaults, prefix, placeholderResolver);
    }

    @Override
    HttpRouteGroup self() {
        return this;
    }

    @Override
    public void group(Consumer<HttpRouteGroup> routes) {
        declareGroup(DefaultHttpRouteGroup::new, routes);
    }

    @Override
    public void path(String prefix, Consumer<HttpRouteGroup> routes) {
        declarePath(prefix, DefaultHttpRouteGroup::new, routes);
    }
}
