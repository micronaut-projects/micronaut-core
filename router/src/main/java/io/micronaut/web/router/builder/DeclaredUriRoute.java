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
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.UriRouteInfo;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * A handler function bound to a {@link RouteDeclaration}. The route is configured like any other,
 * but it is built only the first time the router uses it: until then its {@link RouteSettings}
 * are recorded, and the router indexes and orders the route with the keys of the declaration.
 * The settings are fixed when the router takes the route: the route is created with them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DeclaredUriRoute {
    private final IndexedRouteDeclaration declaration;
    private final RouteSettings settings;
    private final Supplier<RouteAssembly.DefaultUriRoute> route;
    private @Nullable RouteSettings fixed;

    /**
     * @param declaration The declaration
     * @param factory     Creates the route with its settings, not added to the assembly
     * @param exposePort  Exposes the port of the route when it is declared: the server opens the
     *                    exposed ports before the route is built
     */
    public DeclaredUriRoute(IndexedRouteDeclaration declaration, Function<RouteSettings, RouteAssembly.DefaultUriRoute> factory, IntConsumer exposePort) {
        this.declaration = declaration;
        // the settings of the handler are recorded: the handler is given them when the route is created
        this.settings = new RouteSettings(exposePort, null);
        this.route = SupplierUtil.memoized(() -> factory.apply(current()));
    }

    /**
     * @return The declaration
     */
    public IndexedRouteDeclaration declaration() {
        return declaration;
    }

    /**
     * @return The settings the configuration of the route is recorded in
     */
    public RouteSettings settings() {
        return settings;
    }

    /**
     * Fix the configuration: a change after the router took the route does not change it.
     */
    public void fix() {
        if (fixed == null) {
            fixed = settings.fix();
        }
    }

    /**
     * @return The settings the route is created with: the fixed ones once the router took the route
     */
    private RouteSettings current() {
        RouteSettings fixedSettings = fixed;
        return fixedSettings != null ? fixedSettings : settings;
    }

    /**
     * @return The order of the route, or {@code null} if it has none of its own: the router orders
     * the route before it is built
     */
    public @Nullable Integer order() {
        return current().getOrder();
    }

    /**
     * @return The settings of the group of the route, or {@code null}
     */
    public RouteAssembly.@Nullable RouteGroup group() {
        return current().getGroup();
    }

    /**
     * @return The route info, built on first use
     */
    public UriRouteInfo<Object, Object> toRouteInfo() {
        return route.get().toRouteInfo();
    }

    /**
     * @return The route info of the implicit {@code HEAD} route of this {@code GET} route
     */
    public UriRouteInfo<Object, Object> implicitHeadRouteInfo() {
        return route.get().implicitHeadCopy().toRouteInfo();
    }

    @Override
    public String toString() {
        return declaration.httpMethodName() + " " + declaration.uriTemplate() + " (declared)";
    }
}
