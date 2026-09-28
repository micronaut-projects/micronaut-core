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
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;

import java.util.Map;

/**
 * Configures every application context an application builds while a {@link DevRuntime} runs the
 * process: the reloadable class loader, the {@code dev} environment, the retained beans of the
 * previous generation, and the runtime itself as a bean. Service-loaded through the thread context
 * loader, which the launcher points at the reloadable tier; does nothing outside development mode.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class DevApplicationContextConfigurer implements ApplicationContextConfigurer {

    @Override
    public void configure(ApplicationContextBuilder builder) {
        DevRuntime runtime = DevRuntime.current();
        if (runtime == null) {
            return;
        }
        // the generation loader itself, never the facade: the JVM records the loader Class.forName was called
        // with as the initiating loader of a class, and would hand the next generation the previous classes
        builder.classLoader(runtime.classLoader().current());
        builder.environments(Environment.DEVELOPMENT);
        builder.properties(Map.of(
            DevelopmentMode.PROPERTY, true,
            // the engine watches; the context's own watcher must neither restart the JVM nor run beside it
            "micronaut.io.watch.restart", false,
            "micronaut.io.watch.enabled", false
        ));
        builder.trackBeanDependencies(true);
        builder.retainedRegistrations(runtime.takeRetainedRegistrations());
        builder.singletons(runtime);
    }

    @Override
    public void configure(ApplicationContext applicationContext) {
        DevRuntime runtime = DevRuntime.current();
        if (runtime != null) {
            runtime.contextCreated(applicationContext);
        }
    }

    @Override
    public int getOrder() {
        // after the application's own configurer, so that the reloadable loader and the environment win
        return LOWEST_PRECEDENCE;
    }
}
