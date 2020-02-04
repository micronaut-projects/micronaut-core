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
package io.micronaut.inject;

import io.micronaut.core.annotation.AnnotationMetadataProvider;

import javax.annotation.Nonnull;

/**
 * An injection point as a point in a class definition where dependency injection is required.
 *
 * @author Graeme Rocher
 * @since 1.0
 * @param <T> the bean type
 */
public interface InjectionPoint<T> extends AnnotationMetadataProvider {

    /**
     * @return The bean that declares this injection point
     */
    @Nonnull BeanDefinition<T> getDeclaringBean();

    /**
     * @return Whether reflection is required to satisfy the injection point
     */
    boolean requiresReflection();
}
