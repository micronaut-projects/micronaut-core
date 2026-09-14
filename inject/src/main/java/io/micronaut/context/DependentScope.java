/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DelegatingBeanDefinition;
import org.jspecify.annotations.Nullable;

/**
 * The dependent scope: the beans a bean owns, which are the dependents its resolution context carries.
 *
 * <p>Where the {@link SingletonScope} holds one instance of a definition for the whole context, this scope holds one
 * per bean, among the dependents of that bean. It has no storage of its own: a lookup reads the dependents of the
 * resolution context resolving for the bean, and a bean created after a miss joins them as every dependent does,
 * {@linkplain BeanRegistration#markInDependentScope() marked} as a member of the scope. A dependency
 * injected into the bean is a dependent as well but not a member, so an interceptor a bean injects is not the
 * instance that intercepts it. Only a bean with no scope, or a prototype, is of this scope; a singleton belongs to
 * the singleton scope and a bean of a custom scope to that scope.</p>
 *
 * @author Denis Stepanov
 * @see DependentBeanContext
 * @since 5.3.0
 */
@Internal
final class DependentScope {

    /**
     * Whether a bean of the given definition belongs to the bean it is created for.
     *
     * @param definition The definition
     * @return {@code true} for a prototype or a bean with no scope
     */
    boolean isDependent(BeanDefinition<?> definition) {
        if (definition.isSingleton()) {
            return false;
        }
        String scope = definition.getAnnotationMetadata().getAnnotationNameByStereotype(AnnotationUtil.SCOPE).orElse(null);
        return scope == null || Prototype.class.getName().equals(scope);
    }

    /**
     * Finds the bean of the given definition that the bean the context resolves for already owns.
     *
     * @param resolutionContext The resolution context, whose dependents are the bean's
     * @param definition        The definition
     * @param <T>               The bean type
     * @return The registration among the dependents, or {@code null} when the bean owns none of the definition or
     * the definition is not of this scope
     */
    @SuppressWarnings("unchecked")
    @Nullable
    <T> BeanRegistration<T> find(@Nullable BeanResolutionContext resolutionContext, BeanDefinition<T> definition) {
        if (resolutionContext == null || !isDependent(definition)) {
            return null;
        }
        BeanDefinition<?> unwrapped = unwrap(definition);
        for (BeanRegistration<?> dependent : resolutionContext.getDependentBeans()) {
            if (dependent.isInDependentScope() && dependent.bean != null && unwrap(dependent.beanDefinition).equals(unwrapped)) {
                return (BeanRegistration<T>) dependent;
            }
        }
        return null;
    }

    /**
     * Admits a bean the scope has just created, after a miss, as a member.
     *
     * @param registration The registration of the created bean
     * @param <T>          The bean type
     * @return The same registration
     */
    <T> BeanRegistration<T> created(BeanRegistration<T> registration) {
        if (isDependent(registration.beanDefinition)) {
            registration.markInDependentScope();
        }
        return registration;
    }

    /**
     * The definition an iterable bean is wrapped in is not the one a lookup names, so both are compared unwrapped.
     */
    private static BeanDefinition<?> unwrap(BeanDefinition<?> definition) {
        BeanDefinition<?> unwrapped = definition;
        while (unwrapped instanceof DelegatingBeanDefinition<?> delegating) {
            unwrapped = delegating.getTarget();
        }
        return unwrapped;
    }
}
