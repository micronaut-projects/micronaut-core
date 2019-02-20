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

import io.micronaut.core.beans.exceptions.IntrospectionException;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Primary interface for obtaining bean introspections that are computed at compilation time.
 *
 * @author graemerocher
 * @since 1.0
 * @see io.micronaut.core.annotation.Introspected
 */
public interface BeanIntrospector {

    /**
     * The default shared {@link BeanIntrospector}.
     */
    BeanIntrospector SHARED = new DefaultBeanIntrospector();

    /**
     * Find a {@link BeanIntrospection} for the given bean type.
     *
     * @param beanType The bean type
     * @param <T> The bean generic type
     * @return An optional introspection
     */
    @Nonnull <T> Optional<BeanIntrospection<T>> findIntrospection(@Nonnull Class<T> beanType);

    /**
     * Retrieves an introspection for the given type.
     * @param beanType The bean type
     * @param <T> The bean generic type
     * @return The introspection
     * @throws IntrospectionException If no introspection data is found and the bean is not annotated with {@link io.micronaut.core.annotation.Introspected}
     */
    default @Nonnull <T> BeanIntrospection<T> getIntrospection(@Nonnull Class<T> beanType) {
        return findIntrospection(beanType).orElseThrow(() -> new IntrospectionException("No bean introspection available for type [" + beanType + "]. Ensure the class is annotated with io.micronaut.core.annotation.Introspected"));
    }
}
