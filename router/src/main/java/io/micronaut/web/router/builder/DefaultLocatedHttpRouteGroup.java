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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.web.router.RouteAssembly;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The {@link LocatedHttpRouteGroup}: a group of the routes of located targets, whose routes and
 * locators receive the target like the ones of the {@link DefaultLocatedHttpRouteBuilder}.
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultLocatedHttpRouteGroup<T> extends AbstractHttpRouteGroup<LocatedHttpRouteGroup<T>> implements LocatedHttpRouteGroup<T> {

    private final LocatedTarget<T> target;

    /**
     * @param assembly The assembly the routes are added to
     * @param filters  The filters of the group
     * @param settings The other settings of the group
     * @param defaults The media types and the executor of the group
     * @param prefix   The prefix of the URI templates of the routes, or {@code null}
     * @param placeholderResolver Resolves the placeholders of the ports given as strings, or {@code null}
     * @param target   Reads the located target
     */
    @SuppressWarnings("java:S107") // the parts of a group, and the target of its routes
    private DefaultLocatedHttpRouteGroup(RouteAssembly assembly, RouteAssembly.RouteFilters filters, RouteAssembly.RouteGroup settings,
                                         RouteGroupDefaults defaults, @Nullable RoutePrefix prefix,
                                         @Nullable PropertyPlaceholderResolver placeholderResolver, LocatedTarget<T> target) {
        super(assembly, filters, settings, defaults, prefix, placeholderResolver);
        this.target = target;
    }

    /**
     * @param target Reads the located target
     * @param <T>    The type of the located target
     * @return Creates the groups of the routes of the target
     */
    static <T> GroupFactory<DefaultLocatedHttpRouteGroup<T>> factory(LocatedTarget<T> target) {
        return (assembly, filters, settings, defaults, prefix, placeholderResolver) ->
            new DefaultLocatedHttpRouteGroup<>(assembly, filters, settings, defaults, prefix, placeholderResolver, target);
    }

    @Override
    LocatedHttpRouteGroup<T> self() {
        return this;
    }

    @Override
    boolean located() {
        return true;
    }

    @Override
    public Argument<T> targetType() {
        return target.targetType();
    }

    @Override
    public LocatedHttpRouteSpec<T> route(HttpMethod method, String uri) {
        return target.route(super.route(method, uri));
    }

    @Override
    public LocatedHttpRouteSpec<T> route(Set<HttpMethod> methods, String uri) {
        return target.route(super.route(methods, uri));
    }

    @Override
    public LocatedHttpRouteSpec<T> route(String httpMethodName, String uri) {
        return target.route(super.route(httpMethodName, uri));
    }

    @Override
    public LocatedHttpRouteSpec<T> any(String uri) {
        return target.route(super.any(uri));
    }

    @Override
    public <U> void locate(String prefixUri, LocatedLocatorHandler<T, ? extends U> locator,
                           Function<? super U, ? extends LocatedRoutes<?>> routesOf) {
        locate(prefixUri, target.<U>locator(locator), routesOf);
    }

    @Override
    public <U> void locateAsync(String prefixUri, LocatedAsyncLocatorHandler<T, ? extends U> locator,
                                Function<? super U, ? extends LocatedRoutes<?>> routesOf) {
        locateAsync(prefixUri, target.<U>asyncLocator(locator), routesOf);
    }

    @Override
    public void group(Consumer<LocatedHttpRouteGroup<T>> routes) {
        declareGroup(factory(target), routes);
    }

    @Override
    public void path(String prefix, Consumer<LocatedHttpRouteGroup<T>> routes) {
        declarePath(prefix, factory(target), routes);
    }
}
