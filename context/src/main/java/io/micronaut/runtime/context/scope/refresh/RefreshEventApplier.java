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
package io.micronaut.runtime.context.scope.refresh;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullMarked;

/**
 * Hands a {@link RefreshEvent} something else published to the refresher where there is no
 * {@link RefreshScope} to do it, as in the function and Android environments. The components that
 * listened for the event before the refresher, such as the logger levels configurer, now watch the
 * configuration, and the refresher is what tells the watches.
 *
 * <p>Where there is a refresh scope it hands the event over itself, so this bean does not exist and
 * the event is applied once.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@NullMarked
@Requires(missingBeans = RefreshScope.class)
final class RefreshEventApplier implements ApplicationEventListener<RefreshEvent>, Ordered {

    private final DefaultConfigurationRefresher refresher;

    RefreshEventApplier(DefaultConfigurationRefresher refresher) {
        this.refresher = refresher;
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        // an event the refresher itself published is done with: its phases already ran
        if (!refresher.isApplying()) {
            refresher.applyEvent(event);
        }
    }

    @Override
    public int getOrder() {
        // where the scope would have run: before the listeners that read the refreshed configuration
        return RefreshScope.POSITION;
    }
}
