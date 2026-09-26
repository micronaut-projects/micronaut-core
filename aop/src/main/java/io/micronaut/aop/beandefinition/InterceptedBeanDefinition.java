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
package io.micronaut.aop.beandefinition;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.chain.LifecycleInterception;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.inject.InstantiatableBeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;
/**
 * Intercepted {@link InstantiatableBeanDefinition}.
 *
 * @param <T> The bean definition type
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Internal
public interface InterceptedBeanDefinition<T> extends InstantiatableBeanDefinition<T> {

    /**
     * Resolve the construction values.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @return the construction values
     */
    @Nullable Object[] resolveInstantiationValues(BeanResolutionContext resolutionContext, BeanContext context);

    /**
     * Resolves the interceptors that construction, post-construct and pre-destroy interception of this bean all
     * select from.
     *
     * <p>The qualifier is built from the bean's own annotation metadata, so it covers every {@code @InterceptorBinding}
     * the bean declares whatever kind each was declared for. That makes the result a superset of what any one phase
     * needs; each interception point filters it again by its own binding and kind. The non-singleton interceptors
     * this creates become dependents of the bean, where its post-construct and pre-destroy interception find them
     * again, so one instance serves every phase of one bean.</p>
     *
     * <p>Override to supply the set another way. Returning {@code null} leaves each interception point to resolve its
     * own, which is the behaviour of a bean that declares no interceptor binding at all.</p>
     *
     * @param resolutionContext The resolution context
     * @param constructor       The constructor, whose metadata is this bean's combined with the constructor's
     * @return The interceptors, or {@code null} when the bean binds none
     * @since 5.2.0
     */
    default @Nullable List<BeanRegistration<Interceptor<T, T>>> resolveInterceptors(BeanResolutionContext resolutionContext,
                                                                                   AnnotationMetadataProvider constructor) {
        return LifecycleInterception.constructionInterceptors(resolutionContext, constructor);
    }

    @Override
    default T instantiate(BeanResolutionContext resolutionContext, BeanContext context) {
        InterceptedConstructor<T> constructor = new InterceptedConstructor<>(this, resolutionContext, context);
        // Resolve the constructor values first, as before, so that resolving interceptors cannot change the order in
        // which this bean's own dependencies are created.
        Object[] values = resolveInstantiationValues(resolutionContext, context);
        // Resolved once for construction: the non-singleton interceptors this creates are dependents of this bean,
        // and its post-construct and pre-destroy interception reuse them from there.
        List<BeanRegistration<Interceptor<T, T>>> interceptors = resolveInterceptors(resolutionContext, constructor);
        return LifecycleInterception.instantiate(
            resolutionContext,
            context,
            interceptors,
            this,
            constructor,
            values
        );
    }

    /**
     * The original {@link #instantiate(BeanResolutionContext, BeanContext)} call that should be intercepted.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param parameterValues   The construction values
     * @return The intercepted result
     */
    T doInstantiate(BeanResolutionContext resolutionContext, BeanContext context, @Nullable Object[] parameterValues);
}
