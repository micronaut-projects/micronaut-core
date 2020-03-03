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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;

import javax.annotation.Nonnull;

/**
 * A reference to a {@link BeanIntrospection} that may or may not be present on the classpath.
 *
 * <p>This interface allows soft loading a {@link BeanIntrospection} without knowing if the class is present on the classpath or not. It also ensures that less memory is occupied as only a reference to the annotation metadata is retained and the full bean can be loaded via the {@link #load()} method.</p>
 *
 * <p>In general results of the {@link #load()} do not need to be cached as object creation is cheap and no runtime analysis is performed so it is extremely fast.</p>
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 1.1
 */
@Internal
public interface BeanIntrospectionReference<T> extends AnnotationMetadataProvider, Named {

    /**
     * @return Is the introspection present?
     */
    boolean isPresent();

    /**
     * The type. The method {@link #isPresent()} should be checked first.
     * @return The type
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if the introspection cannot be loaded
     */
    @Nonnull Class<T> getBeanType();

    /**
     * Loads the introspection.
     * @return The loaded introspection.
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if the introspection cannot be loaded
     */
    @Nonnull BeanIntrospection<T> load();
}
