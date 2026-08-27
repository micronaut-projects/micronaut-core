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
package io.micronaut.aop;

import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;

/**
 * Describes the bean target for which an interceptor is being created.
 *
 * <p>Inject this type into a non-singleton {@link Interceptor} when the interceptor needs to initialize state for one
 * intercepted bean. For example, an interceptor can cache the user type or inspect annotations on the target bean
 * definition:</p>
 *
 * <pre>{@code
 * @Prototype
 * @InterceptorBean(Tracked.class)
 * final class TrackingInterceptor implements Interceptor<Object, Object> {
 *     private final Class<?> targetType;
 *
 *     TrackingInterceptor(InterceptorTarget target) {
 *         this.targetType = target.getType();
 *     }
 *
 *     @Override
 *     public Object intercept(InvocationContext<Object, Object> context) {
 *         return context.proceed();
 *     }
 * }
 * }</pre>
 *
 * <p>{@code @InterceptorBean} uses singleton scope by default, so the interceptor in this pattern must explicitly use
 * {@link io.micronaut.context.annotation.Prototype @Prototype} or another suitable non-singleton scope. Injecting an
 * {@code InterceptorTarget} into a singleton interceptor is unsupported because the singleton is shared by multiple
 * intercepted beans.</p>
 *
 * <p>The interceptor is resolved eagerly while the target proxy is constructed. For proxies with lifecycle advice,
 * the registration is retained so the same interceptor instance can participate in constructor, method,
 * post-construct, and pre-destroy interception for that target. A prototype interceptor remains a dependent of the
 * target and is destroyed after the target's pre-destroy processing.</p>
 *
 * <p>This API describes the target; it does not expose the target instance, which might not have been constructed when
 * the interceptor is created. It is available only while an interceptor is being resolved for an intercepted bean.
 * Resolving it from unrelated bean creation fails with a bean-instantiation error. Because retention support is part
 * of generated proxy bytecode, intercepted application classes must be recompiled after upgrading to a version that
 * supports this API.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
public interface InterceptorTarget {

    /**
     * Returns the bean definition participating in the target's resolution.
     *
     * <p>The definition can be used to inspect the target's annotation metadata, qualifiers, scope, and other
     * compile-time bean information. For an advised bean this can be the proxy bean definition; use {@link #getType()}
     * when the original user type is required.</p>
     *
     * @return The target bean definition
     */
    BeanDefinition<?> getBeanDefinition();

    /**
     * Returns the user type being intercepted.
     *
     * <p>If the target definition implements {@link AdvisedBeanType}, this method unwraps it to the intercepted user
     * type. Otherwise it returns {@link BeanDefinition#getBeanType()}.</p>
     *
     * @return The user type being intercepted
     */
    default Class<?> getType() {
        BeanDefinition<?> beanDefinition = getBeanDefinition();
        if (beanDefinition instanceof AdvisedBeanType<?> advisedBeanType) {
            return advisedBeanType.getInterceptedType();
        }
        return beanDefinition.getBeanType();
    }
}
