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
package io.micronaut.aop.beandefinition;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;

import java.util.List;

/**
 * An intercepted lifecycle method of a bean that knows the methods of the bean it stands for.
 *
 * <p>Implemented by the executable method that stands for one intercepted {@code @PostConstruct} or
 * {@code @PreDestroy} callback, which reports itself, and by the ones that stand for a whole phase of a bean
 * without callbacks, which report nothing, so that the interceptor chain can answer
 * {@link io.micronaut.aop.MethodInvocationContext#getExecutableMethods()}.</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public interface LifecycleInterceptedMethod<T> {

    /**
     * Returns the methods of the bean represented by this lifecycle method.
     *
     * @return The callback this method stands for, or an empty list for a phase without callbacks
     */
    List<ExecutableMethod<T, ?>> getExecutableMethods();
}
