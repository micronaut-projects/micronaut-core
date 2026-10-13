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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.runtime.context.scope.refresh.RefreshScope;
import jakarta.inject.Singleton;

/**
 * Sets the log levels again on a {@link RefreshEvent} where there is no {@link RefreshScope}, as in the function and
 * Android environments. Where there is one, {@link PropertiesLoggingLevelsWatcher} sets them instead.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@BootstrapContextCompatible
@Singleton
@Requires(beans = PropertiesLoggingLevelsConfigurer.class)
@Requires(missingBeans = RefreshScope.class)
@Internal
final class PropertiesLoggingLevelsRefreshListener implements ApplicationEventListener<RefreshEvent>, Ordered {

    private final PropertiesLoggingLevelsConfigurer configurer;

    /**
     * @param configurer The configurer that sets the levels
     */
    PropertiesLoggingLevelsRefreshListener(PropertiesLoggingLevelsConfigurer configurer) {
        this.configurer = configurer;
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        configurer.refresh();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
