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
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

/**
 * Tells the {@link DevRuntime} that the generation's context started, and prints the banner.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(condition = DevelopmentMode.Active.class)
@Requires(beans = DevRuntime.class)
final class DevStartupListener implements ApplicationEventListener<StartupEvent> {

    private final DevRuntime runtime;

    DevStartupListener(DevRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        if (event.getSource() instanceof ApplicationContext context) {
            runtime.contextStarted(context);
        }
    }
}
