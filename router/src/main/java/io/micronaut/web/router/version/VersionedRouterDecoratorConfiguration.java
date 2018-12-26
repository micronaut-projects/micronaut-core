/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.web.router.Router;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Configuration to decorate {@link Router} with version matching logic.
 *
 * @author Bogdan Oros
 * @since 1.1.0
 */
@Singleton
@Requires(beans = RoutesVersioningConfiguration.class)
public class VersionedRouterDecoratorConfiguration implements BeanCreatedEventListener<Router> {

    private final RouteMatchesFilter versionedUrlFilter;

    /**
     * Creates a configuration to decorate existing {@link Router} beans with a {@link VersionedRouter}.
     *
     * @param filter A {@link RouteMatchesFilter} to delegate routes filtering
     */
    @Inject
    public VersionedRouterDecoratorConfiguration(RouteMatchesFilter filter) {
        this.versionedUrlFilter = filter;
    }

    /**
     * Returns a wrapped {@link Router} to {@link VersionedRouter}.
     *
     * @param event The {@link Router} bean created event
     * @return The wrapper router bean
     */
    @Override
    public Router onCreated(BeanCreatedEvent<Router> event) {
        return new VersionedRouter(
                event.getBean(), versionedUrlFilter
        );
    }
}
