/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.beans;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DisabledBean;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.QualifiedBeanType;

import java.util.List;
import java.util.function.Predicate;

/**
 * The API to access bean definitions and bean references.
 * Internal implementation is responsible for loading bean definitions and checking if it's enabled.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public sealed interface BeanDefinitionService permits DefaultBeanDefinitionService {

    /**
     * Registers a runtime bean definition so it becomes visible to the provider alongside precompiled definitions.
     *
     * @param definition the runtime definition to register, never null
     */
    void addBeanDefinition(@NonNull RuntimeBeanDefinition<?> definition);

    /**
     * Unregisters a runtime bean definition that was previously added.
     *
     * @param definition the runtime definition to remove, never null
     */
    void removeBeanDefinition(@NonNull RuntimeBeanDefinition<?> definition);

    /**
     * Records that a qualified bean type has been disabled, along with human-readable reasons.
     *
     * @param beanType the qualified bean type that has been disabled
     * @param reasons  the reasons explaining why the bean is disabled
     */
    void trackDisabled(QualifiedBeanType<?> beanType, List<String> reasons);

    /**
     * Returns bean definitions that should be initialized eagerly.
     *
     * @param beanContext the bean context to eliminate disabled beans
     * @return an iterable of bean definitions that should be eagerly instantiated
     */
    Iterable<BeanDefinition<Object>> getEagerInitBeans(@NonNull BeanContext beanContext);

    /**
     * Returns bean definitions that require contextual processing during startup.
     *
     * @param beanContext the bean context to eliminate disabled beans
     * @return an iterable of bean definitions that require processing at startup
     */
    Iterable<BeanDefinition<Object>> getProcessedBeans(@NonNull BeanContext beanContext);

    /**
     * Returns bean definitions that are safe to be instantiated in parallel during startup.
     *
     * @param beanContext the bean context to eliminate disabled beans
     * @return an iterable of bean definitions eligible for parallel creation
     */
    Iterable<BeanDefinition<Object>> getParallelBeans(@NonNull BeanContext beanContext);

    /**
     * Returns target proxy bean definitions.
     *
     * @param beanContext the bean context to eliminate disabled beans
     * @return an iterable of bean definitions that should be treated as target proxy candidates
     */
    Iterable<BeanDefinition<Object>> getTargetProxyBeans(@NonNull BeanContext beanContext);

    /**
     * Returns a disabled beans.
     *
     * @param beanContext the bean context to eliminate disabled beans
     * @return a list of disabled beans with their associated reasons
     */
    List<DisabledBean<?>> getDisabledBeans(@NonNull BeanContext beanContext);

    /**
     * Returns whether at least one bean definition exists for the given raw type.
     * This is a lightweight existence check and does not consider qualifiers.
     *
     * @param beanType the raw type to check
     * @return true if at least one definition of the given type is known; false otherwise
     */
    @NonNull
    boolean exists(Class<?> beanType);

    /**
     * Resolves enabled bean definitions.
     *
     * @param beanContext  the bean context to eliminate disabled beans
     * @param defPredicate optional filter applied to definitions
     * @return a list of loaded bean definitions matching the filter
     */
    @NonNull
    default List<BeanDefinition<Object>> getBeanDefinitions(
        @NonNull BeanContext beanContext,
        @Nullable Predicate<BeanDefinition<Object>> defPredicate
    ) {
        return getBeanDefinitions(beanContext, null, defPredicate);
    }

    /**
     * Resolves enabled bean definitions.
     *
     * @param beanContext  the bean context to eliminate disabled beans
     * @param refPredicate optional filter applied to references
     * @param defPredicate optional filter applied to definitions
     * @return a list of loaded bean definitions matching the filters
     */
    @NonNull
    default List<BeanDefinition<Object>> getBeanDefinitions(
        @NonNull BeanContext beanContext,
        @Nullable Predicate<BeanDefinitionReference<Object>> refPredicate,
        @Nullable Predicate<BeanDefinition<Object>> defPredicate
    ) {
        List<BeanDefinition<Object>> beanDefinitions = new java.util.ArrayList<>(20);
        for (BeanDefinition<Object> beanDefinition : getBeanDefinitions(beanContext, Argument.OBJECT_ARGUMENT, refPredicate, defPredicate)) {
            beanDefinitions.add(beanDefinition);
        }
        return beanDefinitions;
    }

    /**
     * Resolves enabled bean definitions.
     *
     * @param beanContext  the bean context to eliminate disabled beans
     * @param beanType     the bean type to resolve
     * @param refPredicate optional filter applied to references
     * @param defPredicate optional filter applied to definitions
     * @param <B>          the bean generic type
     * @return an iterable over matching bean definitions; iteration may trigger loading
     */
    @NonNull
    <B> Iterable<BeanDefinition<B>> getBeanDefinitions(
        @NonNull BeanContext beanContext,
        @NonNull Argument<B> beanType,
        @Nullable Predicate<BeanDefinitionReference<B>> refPredicate,
        @Nullable Predicate<BeanDefinition<B>> defPredicate
    );

    /**
     * Resolves enabled bean definitions.
     *
     * @param beanResolutionContext the resolution context to eliminate disabled beans
     * @param beanType              the bean context to eliminate disabled beans
     * @param refPredicate          optional filter applied to references prior to loading; may be null
     * @param defPredicate          optional filter applied to loaded definitions; may be null
     * @param <B>                   the bean generic type
     * @return an iterable over matching bean definitions]]
     */
    @NonNull
    <B> Iterable<BeanDefinition<B>> getBeanDefinitions(
        @NonNull BeanResolutionContext beanResolutionContext,
        @NonNull Argument<B> beanType,
        @Nullable Predicate<BeanDefinitionReference<B>> refPredicate,
        @Nullable Predicate<BeanDefinition<B>> defPredicate
    );

    /**
     * Returns all enabled bean references.
     *
     * @param beanContext the context for which references are requested
     * @return an iterable of bean definition references
     */
    @NonNull
    Iterable<BeanDefinitionReference<Object>> getBeanReferences(
        @NonNull BeanContext beanContext
    );

    /**
     * Returns all bean references.
     *
     * @return a list of all known bean definition references
     */
    @NonNull
    List<BeanDefinitionReference<Object>> getBeanReferences();

    /**
     * Registers a bean configuration.
     *
     * @param configuration the configuration to register
     */
    void registerConfiguration(@NonNull BeanConfiguration configuration);

    /**
     * Resets the provider by clearing internal caches and runtime registrations.
     * Intended for container lifecycle management and test scenarios.
     */
    void reset();

    /**
     * Initializes the provider for use with the given context.
     * Implementations may perform warm-up and indexing.
     *
     * @param beanContext the bean context
     */
    void initialize(BeanContext beanContext);

}
