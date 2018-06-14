/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.function.executor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.function.LocalFunctionRegistry;
import io.micronaut.inject.ExecutableMethod;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * An abstract executor implementation.
 *
 * @param <C> Type of the context
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class AbstractExecutor<C> {
    /**
     * Resolve a function from the {@link LocalFunctionRegistry}.
     *
     * @param localFunctionRegistry The {@link LocalFunctionRegistry}
     * @param functionName          The function name
     * @return The method
     */
    protected ExecutableMethod<Object, Object> resolveFunction(LocalFunctionRegistry localFunctionRegistry, String functionName) {
        Optional<? extends ExecutableMethod<Object, Object>> registeredMethod;
        if (functionName == null) {
            registeredMethod = localFunctionRegistry.findFirst();
        } else {
            registeredMethod = localFunctionRegistry.find(functionName);
        }
        return registeredMethod
            .orElseThrow(() -> new IllegalStateException("No function found for name: " + functionName));
    }

    /**
     * Resolves the function name to execution for the environment.
     *
     * @param env The environment
     * @return The function name
     */
    protected String resolveFunctionName(Environment env) {
        return env.getProperty(LocalFunctionRegistry.FUNCTION_NAME, String.class, (String) null);
    }

    /**
     * @param context A platform specific context object
     * @return Build the {@link ApplicationContext} to use
     */
    protected ApplicationContext buildApplicationContext(@Nullable C context) {
        return ApplicationContext.build(Environment.FUNCTION).build();
    }

    /**
     * Start the environment specified.
     * @param applicationContext the application context with the environment
     * @return The environment within the context
     */
    protected Environment startEnvironment(ApplicationContext applicationContext) {
        if (this instanceof PropertySource) {
            applicationContext.getEnvironment().addPropertySource((PropertySource) this);
        }

        return applicationContext
            .start()
            .getEnvironment();
    }
}
