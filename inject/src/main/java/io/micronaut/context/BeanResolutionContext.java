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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.ValueResolver;
import io.micronaut.inject.*;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Deque;
import java.util.Optional;

/**
 * Represents the resolution context for a current resolve of a given bean.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public interface BeanResolutionContext extends ValueResolver<CharSequence>, AutoCloseable {

    @Override
    default void close() {
        // no-op
    }

    /**
     * @return The context
     */
    BeanContext getContext();

    /**
     * @return The class requested at the root of this resolution context
     */
    BeanDefinition getRootDefinition();

    /**
     * @return The path that this resolution has taken so far
     */
    Path getPath();

    /**
     * Store a value within the context.
     * @param key The key
     * @param value The value
     * @return The previous value or null
     */
    Object setAttribute(CharSequence key, Object value);

    /**
     * @param key The key
     * @return The attribute value
     */
    Object getAttribute(CharSequence key);

    /**
     * Remove the attribute for the given key.
     * @param key the key
     */
    void removeAttribute(CharSequence key);

    /**
     * Adds a bean that is created as part of the resolution. This is used to store references to instances passed to {@link BeanContext#inject(Object)}
     * @param beanIdentifier The bean identifier
     * @param instance The instance
     * @param <T> THe instance type
     */
    <T> void addInFlightBean(BeanIdentifier beanIdentifier, T instance);

    /**
     * Removes a bean that is in the process of being created. This is used to store references to instances passed to {@link BeanContext#inject(Object)}
     * @param beanIdentifier The bean identifier
     * @param <T> THe instance type
     */
    <T> void removeInFlightBean(BeanIdentifier beanIdentifier);

    /**
     * Obtains an inflight bean for the given identifier. An "In Flight" bean is one that is currently being
     * created but has not finished construction and been registered as a singleton just yet. For example
     * in the case whereby a bean as a {@code PostConstruct} method that also triggers bean resolution of the same bean.
     *
     * @param beanIdentifier The bean identifier
     * @param <T> The bean type
     * @return The bean
     */
    @Nullable <T> T getInFlightBean(BeanIdentifier beanIdentifier);

    /**
     * @return The current bean identifier
     */
    @Nullable Qualifier<?> getCurrentQualifier();

    /**
     * Sets the current qualifier.
     * @param qualifier The qualifier
     */
    void setCurrentQualifier(@Nullable Qualifier<?> qualifier);

    /**
     * Represents a path taken to resolve a bean definitions dependencies.
     */
    interface Path extends Deque<Segment<?>> {
        /**
         * Push an unresolved constructor call onto the queue.
         *
         * @param declaringType The type
         * @param argument      The unresolved argument
         * @return This path
         */
        Path pushConstructorResolve(BeanDefinition declaringType, Argument argument);

        /**
         * Push an unresolved method call onto the queue.
         *
         * @param declaringType        The type
         * @param methodInjectionPoint The method injection point
         * @param argument             The unresolved argument
         * @return This path
         */
        Path pushMethodArgumentResolve(BeanDefinition declaringType, MethodInjectionPoint methodInjectionPoint, Argument argument);

        /**
         * Push an unresolved field onto the queue.
         *
         * @param declaringType       declaring type
         * @param fieldInjectionPoint The field injection point
         * @return This path
         */
        Path pushFieldResolve(BeanDefinition declaringType, FieldInjectionPoint fieldInjectionPoint);

        /**
         * Converts the path to a circular string.
         *
         * @return The circular string
         */
        String toCircularString();

        /**
         * @return The current path segment
         */
        Optional<Segment<?>> currentSegment();
    }

    /**
     * A segment in a path.
     *
     * @param <T> the bean type
     */
    interface Segment<T> {

        /**
         * @return The type requested
         */
        BeanDefinition<T> getDeclaringType();

        /**
         * @return The inject point
         */
        InjectionPoint<T> getInjectionPoint();

        /**
         * @return The name of the segment. For a field this is the field name, for a method the method name and for a constructor the type name
         */
        String getName();

        /**
         * @return The argument to create the type. For a field this will be empty
         */
        Argument getArgument();
    }
}
