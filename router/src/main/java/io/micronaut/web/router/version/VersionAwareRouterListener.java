/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.web.router.version;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.filter.FilteredRouter;
import javax.inject.Singleton;

import java.util.function.Predicate;

import static io.micronaut.web.router.version.RoutesVersioningConfiguration.PREFIX;

/**
 * Configuration to decorate {@link Router} with version matching logic.
 *
 * @author Bogdan Oros
 * @since 1.1.0
 */
@Singleton
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
@Requires(beans = RoutesVersioningConfiguration.class)
public class VersionAwareRouterListener implements BeanCreatedEventListener<Router> {

    private final VersionRouteMatchFilter routeVersionFilter;

    /**
     * Creates a configuration to decorate existing {@link Router} beans with a {@link FilteredRouter}.
     *
     * @param filter A {@link VersionRouteMatchFilter} to delegate routes filtering
     */
    public VersionAwareRouterListener(VersionRouteMatchFilter filter) {
        this.routeVersionFilter = filter;
    }

    /**
     * Creates a configuration to decorate existing {@link Router} beans with a {@link FilteredRouter}.
     *
     * @param filter A {@link io.micronaut.web.router.filter.RouteMatchFilter} to delegate routes filtering
     * @deprecated Use {@link VersionAwareRouterListener(VersionRouteMatchFilter)} instead.
     */
    @Deprecated
    public VersionAwareRouterListener(RouteVersionFilter filter) {
        this.routeVersionFilter = new VersionRouteMatchFilter() {
            @Override
            public <T, R> Predicate<UriRouteMatch<T, R>> filter(HttpRequest<?> request) {
                return filter.filter(request);
            }
        };
    }

    /**
     * Returns a wrapped {@link Router} to {@link FilteredRouter}.
     *
     * @param event The {@link Router} bean created event
     * @return The wrapper router bean
     */
    @Override
    public Router onCreated(BeanCreatedEvent<Router> event) {
        return new FilteredRouter(event.getBean(), routeVersionFilter);
    }
}
