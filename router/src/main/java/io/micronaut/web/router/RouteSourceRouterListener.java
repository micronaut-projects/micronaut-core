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
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.web.router.filter.RouteMatchFilter;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorates the {@link Router} to consult the {@link RouteSource} beans, if there are any.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Singleton
@Internal
@Requires(beans = RouteSource.class)
final class RouteSourceRouterListener implements BeanCreatedEventListener<Router> {

    private final BeanProvider<RouteSource> routeSources;
    private final BeanProvider<RouteMatchFilter> routeMatchFilters;

    RouteSourceRouterListener(BeanProvider<RouteSource> routeSources, BeanProvider<RouteMatchFilter> routeMatchFilters) {
        this.routeSources = routeSources;
        this.routeMatchFilters = routeMatchFilters;
    }

    @Override
    public Router onCreated(BeanCreatedEvent<Router> event) {
        // resolved on first use: a route source may itself depend on beans that need the router
        return new RouteSourceRouter(event.getBean(), SupplierUtil.memoized(() -> {
            List<RouteSource> sources = new ArrayList<>(routeSources.stream().toList());
            OrderUtil.sort(sources);
            return List.copyOf(sources);
        }), SupplierUtil.memoized(() -> routeMatchFilters.stream().toList()));
    }
}
