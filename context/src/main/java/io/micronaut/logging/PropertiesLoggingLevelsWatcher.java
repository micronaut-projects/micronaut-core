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
package io.micronaut.logging;

import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.context.scope.refresh.RefreshScope;

/**
 * Sets the log levels again when the configuration under {@code logger} changes, where a {@link RefreshScope} hands
 * refresh events to the configuration refresher. The levels are set after the refresher rebound the configuration
 * beans, so that the levels read are the new ones, and only for a change under {@code logger}. Without a refresh
 * scope, {@link PropertiesLoggingLevelsRefreshListener} sets them on the refresh event instead.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Context
@Requires(beans = PropertiesLoggingLevelsConfigurer.class)
@Requires(beans = RefreshScope.class)
@Internal
final class PropertiesLoggingLevelsWatcher {

    /**
     * @param configurer The configurer that sets the levels
     * @param beanContext The context to watch the logger configuration on
     */
    PropertiesLoggingLevelsWatcher(PropertiesLoggingLevelsConfigurer configurer, WatchableBeanContext beanContext) {
        beanContext.configuration(PropertiesLoggingLevelsConfigurer.LOGGER_PROPERTY_PREFIX)
            .watch(change -> configurer.refresh());
    }
}
