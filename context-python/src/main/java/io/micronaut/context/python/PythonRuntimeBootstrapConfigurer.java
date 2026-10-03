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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.BootstrapContextAccess;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.core.annotation.Internal;

/**
 * Records the application context that is starting, so generated Python code that runs before the
 * {@code @Context} GraalPy context bean is initialized can create that bean on demand.
 * <p>
 * Type converters are created before the eager beans, and the beans of {@code processOnStartup}
 * executable methods (message listeners, scheduled jobs, ...) are instantiated by their processors
 * before the eager beans too. A Python bean created that early needs the runtime; with the context
 * recorded here, {@link PythonContextRuntime} creates the GraalPy context bean of this application
 * instead of failing because the bean does not exist yet. The record is cleared when the application
 * context shuts down (see {@link PythonRuntimeBootstrapShutdownListener}).
 * <p>
 * The bootstrap context of an application (see {@link BootstrapContextAccess}) runs the configurer
 * too but is never recorded: it only holds {@code BootstrapContextCompatible} beans, so it cannot
 * provide the GraalPy context bean, and it publishes no {@code ShutdownEvent} that would clear the
 * record again, which matters when a refresh recreates it while the application is running.
 * <p>
 * The builder is configured before the application context exists at all, which is where the
 * platform entry points of the application run ({@code TestPropertyProvider.getProperties()} is
 * called while the builder is being filled in). Generated Python code reached from one of them finds
 * neither a runtime nor a recorded application context, so {@link #configure(ApplicationContextBuilder)}
 * records that an application is on its way and a default context may be bootstrapped for it. A call
 * that arrives when no application is on its way and none is recorded either is a leftover reference
 * of an application that shut down, and keeps failing.
 *
 * @author Micronaut Team
 * @since 5.3.0
 */
@Internal
@ContextConfigurer
public final class PythonRuntimeBootstrapConfigurer implements ApplicationContextConfigurer {

    @Override
    public void configure(ApplicationContextBuilder builder) {
        PythonApplicationRuntime.expectApplication();
    }

    @Override
    public void configure(ApplicationContext applicationContext) {
        if (applicationContext.containsBean(BootstrapContextAccess.class)) {
            return;
        }
        PythonApplicationRuntime.bootstrapFrom(applicationContext);
    }
}
