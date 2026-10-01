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
package io.micronaut.context.watch;

import io.micronaut.core.annotation.Experimental;

/**
 * Receives the changes to the bean definitions of a type, registered with
 * {@link io.micronaut.context.BeanContext#watchDefinitions(io.micronaut.core.type.Argument, io.micronaut.context.Qualifier, BeanDefinitionWatcher)}.
 *
 * @param <T> The bean type watched
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface BeanDefinitionWatcher<T> {

    /**
     * Called once per batch: at startup with everything present, and afterwards whenever a definition of
     * the type is added or removed. A bean of an added definition can be obtained from the context inside
     * this call.
     *
     * @param change The change
     */
    void onChange(BeanDefinitionChange<T> change);
}
