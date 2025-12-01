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
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.chain.InterceptorChain;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The default {@link RuntimeProxyDefinition}.
 *
 * @param proxyBeanDefinition The proxy bean definition
 * @param resolutionContext   The bean resolution context
 * @param interceptedMethods  The intercepted methods
 * @param introduction        Whether the proxy is an introduction
 * @param proxyTarget         Whether the proxy is a proxy target bean
 * @param constructorValues   The constructor values
 * @param <T>                 The proxy type
 */
@Internal
@NullMarked
public record DefaultRuntimeProxyDefinition<T>(BeanDefinition<T> proxyBeanDefinition,
                                               BeanResolutionContext resolutionContext,
                                               List<InterceptedMethod<T>> interceptedMethods,
                                               boolean introduction,
                                               boolean proxyTarget,
                                               Object[] constructorValues) implements RuntimeProxyDefinition<T> {

    /**
     * Creates a new instance for around advice.
     *
     * @param resolutionContext   The resolution context
     * @param proxyBeanDefinition The proxy bean definition
     * @param isProxyTarget       Is proxy target bean
     * @param constructorValues   The constructor values
     * @param <T>                 The proxy type
     * @return The definition
     */
    public static <T> DefaultRuntimeProxyDefinition<T> around(BeanResolutionContext resolutionContext,
                                                              BeanDefinition<T> proxyBeanDefinition,
                                                              boolean isProxyTarget,
                                                              Object[] constructorValues) {

        Collection<ExecutableMethod<T, ?>> executableMethods;
        if (isProxyTarget) {
            Class<T> beanType = proxyBeanDefinition.getBeanType();
            BeanContext beanContext = resolutionContext
                .getContext();
            Argument<T> argument = Argument.of(beanType);
            Qualifier<T> qualifier = (Qualifier<T>) resolutionContext.getCurrentQualifier();
            executableMethods = beanContext
                .getProxyTargetBeanDefinition(argument, qualifier)
                .getExecutableMethods();
        } else {
            executableMethods = proxyBeanDefinition.getExecutableMethods();
        }
        InterceptorRegistry interceptorRegistry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        Qualifier<Object> binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(executableMethods.toArray(new ExecutableMethod[0])));

        List<BeanRegistration<Interceptor<T, ?>>> interceptors = new ArrayList<>(resolutionContext.getBeanRegistrations(
            (Argument) Argument.of(Interceptor.class),
            binding
        ));
        List<InterceptedMethod<T>> interceptedMethods = new ArrayList<>(executableMethods.size());
        for (ExecutableMethod<T, ?> executableMethod : executableMethods) {
            Interceptor<T, ?>[] methodInterceptors = InterceptorChain.resolveAroundInterceptors(interceptorRegistry, executableMethod, interceptors);
            if (methodInterceptors.length > 0) {
                interceptedMethods.add(new InterceptedMethod<>((ExecutableMethod) executableMethod, (Interceptor[]) methodInterceptors));
            }
        }
        return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods, false, isProxyTarget, constructorValues);
    }

    /**
     * Creates a new instance for introduction advice.
     *
     * @param resolutionContext   The resolution context
     * @param proxyBeanDefinition The proxy bean definition
     * @param <T>                 The proxy type
     * @return The definition
     */
    public static <T> DefaultRuntimeProxyDefinition<T> introduction(BeanResolutionContext resolutionContext,
                                                                    BeanDefinition<T> proxyBeanDefinition) {

        Collection<ExecutableMethod<T, ?>> executableMethods = proxyBeanDefinition.getExecutableMethods();
        InterceptorRegistry interceptorRegistry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        Qualifier<Object> binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(executableMethods.toArray(new ExecutableMethod[0])));

        List<BeanRegistration<Interceptor<T, ?>>> interceptors = new ArrayList<>(resolutionContext.getBeanRegistrations(
            (Argument) Argument.of(Interceptor.class),
            binding
        ));
        List<InterceptedMethod<T>> interceptedMethods = new ArrayList<>(executableMethods.size());
        for (ExecutableMethod<T, ?> executableMethod : executableMethods) {
            Interceptor<T, ?>[] methodInterceptors = InterceptorChain.resolveIntroductionInterceptors(interceptorRegistry, executableMethod, interceptors);
            if (methodInterceptors.length > 0) {
                interceptedMethods.add(new InterceptedMethod<>((ExecutableMethod) executableMethod, (Interceptor[]) methodInterceptors));
            }
        }
        return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods, true, false, new Object[0]);
    }

    @Override
    public BeanContext beanContext() {
        return resolutionContext.getContext();
    }

    @Override
    public T targetBean() {
        if (!proxyTarget) {
            throw new IllegalStateException("Cannot get target bean for non-proxy target bean");
        }
        Class<T> beanType = proxyBeanDefinition.getBeanType();
        BeanContext beanContext = resolutionContext
            .getContext();
        Argument<T> argument = Argument.of(beanType);
        Qualifier<T> qualifier = (Qualifier<T>) resolutionContext.getCurrentQualifier();
        BeanDefinition<T> proxyTargetBeanDefinition = beanContext
            .getProxyTargetBeanDefinition(argument, qualifier);
        return resolutionContext.getProxyTargetBean(proxyTargetBeanDefinition, argument, qualifier);
    }

    @Override
    public Argument<?>[] constructorArguments() {
        return proxyBeanDefinition.getConstructor().getArguments();
    }
}
