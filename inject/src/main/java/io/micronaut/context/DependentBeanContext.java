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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * The dependent scope of one bean, seen as a context.
 *
 * <p>The beans created for a bean are its dependents: they belong to that bean alone and are destroyed with it. This
 * context answers the lookups of the {@link BeanContext} within that scope. A singleton, or a bean of a custom scope,
 * comes from its own scope as always. Any other bean, a prototype or a bean with no scope, comes from the dependents
 * the bean already has when one of the definition is among them, and is otherwise created as a new dependent of the
 * bean: within the dependent scope of a bean such a definition has one instance, like a singleton whose scope is that
 * one bean.</p>
 *
 * <p>This is how every interception point of a bean reaches the same instance of a non-singleton interceptor. The
 * interceptors bound to a bean are resolved through the dependent context of the bean: at construction, which creates
 * the non-singleton ones as dependents, at post-construct through the same context that is creating the bean, at
 * pre-destroy through a context opened for the bean's registration, and by a proxy that fronts the bean as a separate
 * target through a context opened for the target's registration.</p>
 *
 * @author Denis Stepanov
 * @see BeanResolutionContext#getDependentContext()
 * @since 5.3.0
 */
@Internal
public interface DependentBeanContext {

    /**
     * Obtains the registrations of the beans of the given type and qualifier within the dependent scope.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type
     * @return The registrations: singletons and scoped beans from their scope, every other bean the dependent the
     * bean has of that definition, created for it when it has none
     */
    <T> Collection<BeanRegistration<T>> getBeanRegistrations(Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Obtains the registration of the bean of the given definition within the dependent scope.
     *
     * @param definition The definition
     * @param <T>        The bean type
     * @return The registration: the bean's own dependent of that definition, created for it when it has none
     */
    <T> BeanRegistration<T> getBeanRegistration(BeanDefinition<T> definition);

    /**
     * Obtains the bean of the given type and qualifier within the dependent scope.
     *
     * @param beanType  The bean type
     * @param qualifier The qualifier
     * @param <T>       The bean type
     * @return The bean
     * @throws io.micronaut.context.exceptions.NoSuchBeanException If no bean of the type exists
     */
    default <T> T getBean(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return getBeanRegistration(getContext().getBeanDefinition(beanType.getType(), qualifier)).getBean();
    }

    /**
     * @return The bean context the dependent scope belongs to
     */
    BeanContext getContext();
}
