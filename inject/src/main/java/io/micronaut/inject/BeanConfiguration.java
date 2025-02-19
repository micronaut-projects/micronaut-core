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

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.NonNull;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A BeanConfiguration is a grouping of several {@link BeanDefinition} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanConfiguration extends AnnotationMetadataProvider, BeanContextConditional {

    /**
     * @return The package for the bean configuration
     */
    Package getPackage();

    /**
     * @return The package name this configuration
     */
    String getName();

    /**
     * The version of this configuration. Note: returns null when called on a configuration not provided by a JAR.
     *
     * @return The version or null
     */
    String getVersion();

    /**
     * Check whether the specified bean definition class is within this bean configuration.
     *
     * @param beanDefinitionReference The bean definition class
     * @return True if it is
     */
    default boolean isWithin(BeanDefinitionReference beanDefinitionReference) {
        return isWithin(beanDefinitionReference.getBeanType());
    }

    /**
     * Check whether the specified class is within this bean configuration.
     *
     * @param className The class name
     * @return True if it is
     */
    default boolean isWithin(String className) {
        String packageName = getName();
        final int i = className.lastIndexOf('.');
        String pkgName = i > -1 ? className.substring(0, i) : className;
        return pkgName.equals(packageName) || pkgName.startsWith(packageName + '.');
    }

    /**
     * Check whether the specified class is within this bean configuration.
     *
     * @param cls The class
     * @return True if it is
     */
    default boolean isWithin(Class cls) {
        return isWithin(cls.getName());
    }

    /**
     * Programmatically create a bean configuration for the given package.
      * @param thePackage The package
     * @param condition The condition
     * @return The bean configuration
     * @since 4.8.0
     */
    static @NonNull BeanConfiguration of(@NonNull Package thePackage, @NonNull Predicate<BeanContext> condition) {
        return of(thePackage.getName(), condition);
    }

    /**
     * Programmatically create a bean configuration for the given package.
     * @param thePackage The package
     * @param condition The condition
     * @return The bean configuration
     * @since 4.8.0
     */
    static @NonNull BeanConfiguration of(@NonNull String thePackage, @NonNull Predicate<BeanContext> condition) {
        return new ConditionalBeanConfiguration(thePackage, condition);
    }

    /**
     * Programmatically disable beans within a package.
     *
     * @param thePackage The package name
     * @return The bean configuration
     * @since 4.8.0
     */
    static @NonNull BeanConfiguration disabled(@NonNull String thePackage) {
        return new ConditionalBeanConfiguration(thePackage, (beanContext -> false));
    }
}
