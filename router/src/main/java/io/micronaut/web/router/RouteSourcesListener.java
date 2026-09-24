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
package io.micronaut.web.router;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.web.router.filter.RouteMatchFilter;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives the {@link RouteSource} beans, if there are any, to the {@link DefaultRouter} bean, also
 * to one that replaces it and calls a public constructor. It runs before the listeners that
 * decorate the router, e.g. with a {@link io.micronaut.web.router.filter.FilteredRouter} for
 * versioning, so that it sees the router itself.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Singleton
@Internal
@Requires(beans = RouteSource.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
final class RouteSourcesListener implements BeanCreatedEventListener<DefaultRouter> {

    private final BeanProvider<RouteSource> routeSources;
    private final BeanProvider<RouteMatchFilter> routeMatchFilters;

    RouteSourcesListener(BeanProvider<RouteSource> routeSources, BeanProvider<RouteMatchFilter> routeMatchFilters) {
        this.routeSources = routeSources;
        this.routeMatchFilters = routeMatchFilters;
    }

    @Override
    public DefaultRouter onCreated(BeanCreatedEvent<DefaultRouter> event) {
        DefaultRouter router = event.getBean();
        // resolved on first use: a route source may itself depend on beans that need the router
        router.useRouteSources(SupplierUtil.memoized(() -> {
            List<RouteSource> sources = new ArrayList<>(routeSources.stream().toList());
            OrderUtil.sort(sources);
            return List.copyOf(sources);
        }), SupplierUtil.memoized(() -> routeMatchFilters.stream().toList()));
        return router;
    }
}
