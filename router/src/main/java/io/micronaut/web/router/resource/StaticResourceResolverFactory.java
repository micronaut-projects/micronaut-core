/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.web.router.resource;

import java.util.List;

import io.micronaut.context.BeanContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.watch.ConfigurationWatcher;
import jakarta.inject.Singleton;

/**
 * A factory for creating the default {@link io.micronaut.web.router.resource.StaticResourceResolver}.
 *
 * @author graemerocher
 * @since 3.1.0
 * @see io.micronaut.web.router.resource.StaticResourceResolver
 */
@Factory
public class StaticResourceResolverFactory {

    /**
     * Builds the {@link io.micronaut.web.router.resource.StaticResourceResolver} instance.
     * @param configurations The configurations
     * @return The {@link io.micronaut.web.router.resource.StaticResourceResolver}
     * @deprecated Use {@link #build(List, BeanContext)}
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    protected StaticResourceResolver build(List<StaticResourceConfiguration> configurations) {
        if (configurations.isEmpty()) {
            return StaticResourceResolver.EMPTY;
        } else {
            return new StaticResourceResolver(configurations);
        }
    }

    /**
     * Builds the {@link io.micronaut.web.router.resource.StaticResourceResolver} instance. In a context
     * that can be watched the resolver follows the {@code micronaut.router.static-resources}
     * configuration: a mapping added, changed or removed applies to the next request.
     *
     * @param configurations The configurations
     * @param beanContext The bean context
     * @return The {@link io.micronaut.web.router.resource.StaticResourceResolver}
     * @since 5.3.0
     */
    @Singleton
    protected StaticResourceResolver build(List<StaticResourceConfiguration> configurations, BeanContext beanContext) {
        StaticResourceResolver built = build(configurations);
        if (beanContext instanceof WatchableBeanContext watchable) {
            // what an override of the older method builds is kept and updated in place; the empty constant cannot
            // follow a mapping added later, so a live resolver stands in for it
            StaticResourceResolver resolver = built == StaticResourceResolver.EMPTY ? new StaticResourceResolver(configurations) : built;
            watchable.watchConfiguration(StaticResourceConfiguration.PREFIX, change -> {
                resolver.update(beanContext.getBeansOfType(StaticResourceConfiguration.class).stream().toList());
                return ConfigurationWatcher.Outcome.APPLIED;
            });
            return resolver;
        }
        return built;
    }
}
