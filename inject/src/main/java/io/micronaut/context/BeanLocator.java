/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.core.reflect.InstantiationUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * <p>Core interface for locating and discovering {@link io.micronaut.context.annotation.Bean} instances.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanLocator {

    /**
     * Obtains a Bean for the given type and qualifier.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return An instanceof said bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @see io.micronaut.inject.qualifiers.Qualifiers
     */
    <T> T getBean(Class<T> beanType, Qualifier<T> qualifier);

    /**
     * Finds a Bean for the given type and qualifier.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return An instance of {@link Optional} that is either empty or containing the specified bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     * @see io.micronaut.inject.qualifiers.Qualifiers
     */
    <T> Optional<T> findBean(Class<T> beanType, Qualifier<T> qualifier);

    /**
     * Get all beans of the given type.
     *
     * @param beanType The bean type
     * @param <T>      The bean type parameter
     * @return The found beans
     */
    <T> Collection<T> getBeansOfType(Class<T> beanType);

    /**
     * Get all beans of the given type.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type parameter
     * @return The found beans
     */
    <T> Collection<T> getBeansOfType(Class<T> beanType, Qualifier<T> qualifier);

    /**
     * Obtain a stream of beans of the given type.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean concrete type
     * @return A stream of instances
     * @see io.micronaut.inject.qualifiers.Qualifiers
     */
    <T> Stream<T> streamOfType(Class<T> beanType, Qualifier<T> qualifier);

    /**
     * Resolves the proxy target for a given bean type. If the bean has no proxy then the original bean is returned.
     *
     * @param beanType  The bean type
     * @param qualifier The bean qualifier
     * @param <T>       The generic type
     * @return The proxied instance
     */
    <T> T getProxyTargetBean(Class<T> beanType, Qualifier<T> qualifier);

    /**
     * Obtain a stream of beans of the given type.
     *
     * @param beanType The bean type
     * @param <T>      The bean concrete type
     * @return A stream
     */
    default <T> Stream<T> streamOfType(Class<T> beanType) {
        return streamOfType(beanType, null);
    }

    /**
     * Obtains a Bean for the given type.
     *
     * @param beanType The bean type
     * @param <T>      The bean type parameter
     * @return An instanceof said bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    default <T> T getBean(Class<T> beanType) {
        return getBean(beanType, null);
    }

    /**
     * Finds a Bean for the given type.
     *
     * @param beanType The bean type
     * @param <T>      The bean type parameter
     * @return An instance of {@link Optional} that is either empty or containing the specified bean
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    default <T> Optional<T> findBean(Class<T> beanType) {
        return findBean(beanType, null);
    }

    /**
     * Finds a Bean for the given type or attempts to instantiate the given instance.
     *
     * @param beanType The bean type
     * @param <T>      The bean type parameter
     * @return An instance of {@link Optional} that is either empty or containing the specified bean if it could not
     * be found or instantiated
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException When multiple possible bean definitions exist
     *                                                                for the given type
     */
    default <T> Optional<T> findOrInstantiateBean(Class<T> beanType) {
        Optional<T> bean = findBean(beanType, null);
        if (bean.isPresent()) {
            return bean;
        } else {
            return InstantiationUtils.tryInstantiate(beanType);
        }
    }
}
