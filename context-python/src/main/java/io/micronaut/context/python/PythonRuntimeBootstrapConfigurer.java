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
import io.micronaut.context.ApplicationContextConfigurer;
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
 *
 * @author Micronaut Team
 * @since 5.3.0
 */
@Internal
@ContextConfigurer
public final class PythonRuntimeBootstrapConfigurer implements ApplicationContextConfigurer {

    @Override
    public void configure(ApplicationContext applicationContext) {
        PythonApplicationRuntime.bootstrapFrom(applicationContext);
    }
}
