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
package io.micronaut.aop.runtime;

import io.micronaut.aop.Interceptor;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The runtime proxy definition.
 *
 * @param <T> The proxy target type
 * @author Denis Stepanov
 * @since 5.0
 */
@NullMarked
@Internal
public interface RuntimeProxyDefinition<T> {

    /**
     * The constructor arguments.
     * @return The constructor arguments
     */
    Argument<?>[] constructorArguments();

    /**
     * The constructor values.
     * @return The constructor values
     */
    Object[] constructorValues();

    /**
     * @return The bean context.
     */
    BeanContext beanContext();

    /**
     * @return The intercepted methods.
     */
    List<InterceptedMethod<T>> interceptedMethods();

    /**
     * @return The proxy bean definition.
     */
    BeanDefinition<T> proxyBeanDefinition();

    /**
     * Get the target bean. Only if proxy target is enabled.
     *
     * @return The target bean.
     */
    T targetBean();

    /**
     * @return true if introduction proxy. Otherwise, around proxy.
     */
    boolean introduction();

    /**
     * @return true if around proxy with a proxy target
     */
    boolean proxyTarget();

    /**
     * The intercepted method.
     *
     * @param executableMethod The executable method
     * @param interceptors     The interceptors
     * @param <K>              The proxy target type
     */
    @NullMarked
    record InterceptedMethod<K>(ExecutableMethod<K, Object> executableMethod,
                                Interceptor<K, Object>[] interceptors) {
    }

}
