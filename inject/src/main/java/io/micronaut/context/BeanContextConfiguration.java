/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context;

import javax.annotation.Nonnull;

/**
 * Configuration for the {@link BeanContext}.
 *
 * @author graemerocher
 * @since 1.1
 */
public interface BeanContextConfiguration {

    /**
     * The class loader to use.
     * @return The class loader.
     */
    default @Nonnull ClassLoader getClassLoader() {
        return ApplicationContextConfiguration.class.getClassLoader();
    }
}
