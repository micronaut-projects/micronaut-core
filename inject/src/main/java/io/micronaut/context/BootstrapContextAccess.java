/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * Interface for accessing aspects of the main context from the bootstrap context.
 *
 * @since 4.6.0
 * @author Jonas Konrad
 */
@Experimental
@Internal
public interface BootstrapContextAccess {
    /**
     * Get the {@link BeanDefinitionRegistry} of the main context. Note that when the bootstrap
     * context is active, much of this registry will not be initialized yet, so most beans will be
     * missing. However, you can register your own beans.
     *
     * @return The registry
     */
    @NonNull
    BeanDefinitionRegistry getMainRegistry();
}
