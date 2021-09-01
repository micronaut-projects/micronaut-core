/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;

import java.util.Collections;
import java.util.Set;

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
    default boolean isPrimary() {
        return getAnnotationMetadata().hasDeclaredStereotype(Primary.class);
    }

    /**
     * Returns the bean type.
     *
     * @return The underlying bean type
     */
    Class<T> getBeanType();

    /**
     * Checks whether the bean type is a container type.
     * @return Whether the type is a container type like {@link Iterable}.
     * @since 3.0.0
     */
    default boolean isContainerType() {
        return DefaultArgument.CONTAINER_TYPES.contains(getBeanType());
    }

    /**
     * Returns a potentially limited subset of bean types exposed by this bean.
     * The types to be exposed can be defined by the {@link io.micronaut.context.annotation.Type} annotation.
     *
     * @return The exposed types
     * @since 3.0.0
     */
    default @NonNull Set<Class<?>> getExposedTypes() {
        final AnnotationMetadata annotationMetadata = getAnnotationMetadata();
        final String beanAnn = Bean.class.getName();
        if (annotationMetadata.hasDeclaredAnnotation(beanAnn)) {
            final Class<?>[] exposedTypes = annotationMetadata.classValues(beanAnn, "typed");
            if (ArrayUtils.isNotEmpty(exposedTypes)) {
                return Collections.unmodifiableSet(CollectionUtils.setOf(exposedTypes));
            }
        }
        return Collections.emptySet();
    }

    /**
     * Return whether this bean type is a candidate for dependency injection for the passed type.
     * @param beanType The bean type
     * @return True if it is
     * @since 3.0.0
     */
    default boolean isCandidateBean(@Nullable Argument<?> beanType) {
        if (beanType == null) {
            return false;
        }
        final Set<Class<?>> exposedTypes = getExposedTypes();
        if (CollectionUtils.isNotEmpty(exposedTypes)) {
            return exposedTypes.contains(beanType.getType());
        } else {
            final Class<T> exposedType = getBeanType();
            return beanType.isAssignableFrom(exposedType) || beanType.getType() == exposedType || isContainerType();
        }
    }

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
