/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.core.beans;

import io.micronaut.core.annotation.Internal;

import javax.annotation.Nonnull;

/**
 * A reference to a {@link BeanIntrospection} that may or may not be present on the classpath.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 1.1
 */
@Internal
public interface BeanIntrospectionReference<T> {

    /**
     * @return Is the introspection present?
     */
    boolean isPresent();

    /**
     * The type. The method {@link #isPresent()} should be checked first.
     * @return The type
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if the introspection cannot be loaded
     */
    @Nonnull Class<T> getType();

    /**
     * Loads the introspection.
     * @return The loaded introspection.
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if the introspection cannot be loaded
     */
    BeanIntrospection<T> load();
}
