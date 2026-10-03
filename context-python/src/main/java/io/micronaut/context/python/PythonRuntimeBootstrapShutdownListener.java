/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.python;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

/**
 * Stops installing the Python runtime from an application context that shuts down; see
 * {@link PythonRuntimeBootstrapConfigurer}.
 *
 * @author Micronaut Team
 * @since 5.3.0
 */
@Internal
@Singleton
final class PythonRuntimeBootstrapShutdownListener implements ApplicationEventListener<ShutdownEvent> {

    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        PythonApplicationRuntime.forgetBootstrap(event.getSource());
    }
}
