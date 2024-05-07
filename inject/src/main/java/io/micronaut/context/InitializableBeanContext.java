/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;

/**
 * A marker interface for {@link BeanContext} implementations that can be introspected,
 * that is to say for context which can be created and need to be fully configured,
 * but not necessarily started yet.
 *
 * @since 3.2.2
 * @deprecated Use {@link ConfigurableBeanContext} instead
 */
@Internal
@Deprecated
public interface InitializableBeanContext extends BeanContext {
    /**
     * Performs operations required before starting the application
     * context, such as reading bean configurations.
     */
    void finalizeConfiguration();
}
