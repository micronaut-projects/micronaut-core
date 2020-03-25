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
package io.micronaut.inject;

import io.micronaut.core.annotation.AnnotationMetadataProvider;

/**
 * A reference to a bean. Implemented by bother {@link BeanDefinitionReference} and {@link BeanDefinition}.
 *
 * @param <T> The bean type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanType<T> extends AnnotationMetadataProvider, BeanContextConditional {

    /**
     * @return Whether the bean definition is the {@link io.micronaut.context.annotation.Primary}
     */
    boolean isPrimary();

    /**
     * Returns the bean type.
     *
     * @return The underlying bean type
     */
    Class<T> getBeanType();

    /**
     * @return The class name
     */
    default String getName() {
        return getBeanType().getName();
    }

    /**
     * By default, when the {@link io.micronaut.context.BeanContext} is started, the
     * {@link BeanDefinition#getExecutableMethods()} are not processed by registered
     * {@link io.micronaut.context.processor.ExecutableMethodProcessor} instances unless this method returns true.
     *
     * @return Whether the bean definition requires method processing
     * @see io.micronaut.context.annotation.Executable#processOnStartup()
     */
    default boolean requiresMethodProcessing() {
        return false;
    }
}
