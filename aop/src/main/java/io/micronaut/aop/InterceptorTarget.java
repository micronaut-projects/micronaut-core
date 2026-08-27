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
 * <p>This type can be injected into a non-singleton {@link Interceptor} to initialize state that belongs to one
 * intercepted bean. The interceptor is resolved eagerly while the target proxy is constructed and is retained by
 * that proxy for method and lifecycle interception.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
public interface InterceptorTarget {

    /**
     * Returns the target bean definition.
     *
     * @return The target bean definition
     */
    BeanDefinition<?> getBeanDefinition();

    /**
     * Returns the user type being intercepted.
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
