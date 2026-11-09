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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefreshedEvent;
import jakarta.inject.Singleton;

/**
 * Restarts the application for a configuration refresh the {@link DevRuntime} did not run itself, such as one of the
 * {@code /refresh} endpoint or a {@link io.micronaut.runtime.context.scope.refresh.RefreshEvent} of a configuration
 * client, when the context kept a bean because recreating it would have stopped the running application: the bean
 * keeps the configuration it had until a restart, which only the runtime applies. A refresh whose result
 * {@link io.micronaut.runtime.context.scope.refresh.RefreshResult#requiresRestart() requires a restart}, for a kept bean
 * or a watch that answered so, restarts as a changed configuration file does. The restart is queued rather than
 * awaited, since the request that refreshed is one the restart drains, and is skipped when a batch restarted already.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@DevelopmentActive
@Requires(beans = DevRuntime.class)
final class KeptBeanRestarter implements ApplicationEventListener<ConfigurationRefreshedEvent> {

    private final DevRuntime runtime;
    private final ApplicationContext context;

    KeptBeanRestarter(DevRuntime runtime, ApplicationContext context) {
        this.runtime = runtime;
        this.context = context;
    }

    @Override
    public void onApplicationEvent(ConfigurationRefreshedEvent event) {
        InPlaceUpdates updates = runtime.inPlaceUpdates();
        if (event.result().requiresRestart()) {
            // told by the refresh's own result, not by the context's count, which another thread may move meanwhile
            updates.requestRestart(context);
        }
        if (updates.restartRequired(context)) {
            runtime.enqueue(Pending.restartIfRequired());
        }
    }
}
