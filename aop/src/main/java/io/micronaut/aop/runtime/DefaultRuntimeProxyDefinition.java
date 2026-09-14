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
import io.micronaut.aop.chain.ProxyTargetInterceptors;
import io.micronaut.aop.beandefinition.SharedInterceptorRegistrations;
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
import java.util.Map;

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
     * The target resolved while the definition of a proxy target proxy was created, keyed by the proxy definition, so
     * that {@link #targetBean()} returns the target the interceptors were selected for.
     */
    private static final String PROXY_TARGET_ATTRIBUTE = "io.micronaut.aop.runtimeProxyTarget";

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

        if (isProxyTarget) {
            return aroundProxyTarget(resolutionContext, proxyBeanDefinition, constructorValues);
        }
        Collection<ExecutableMethod<T, ?>> executableMethods = proxyBeanDefinition.getExecutableMethods();
        InterceptorRegistry interceptorRegistry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        Qualifier<Object> binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(executableMethods.toArray(new ExecutableMethod[0])));

        List<BeanRegistration<Interceptor<T, ?>>> interceptors = new ArrayList<>(resolutionContext.getBeanRegistrations(
            (Argument) Argument.of(Interceptor.class),
            binding
        ));
        SharedInterceptorRegistrations.store(resolutionContext, proxyBeanDefinition, interceptors);
        List<InterceptedMethod<T>> interceptedMethods = new ArrayList<>(executableMethods.size());
        for (ExecutableMethod<T, ?> executableMethod : executableMethods) {
            Interceptor<T, ?>[] methodInterceptors = InterceptorChain.resolveAroundInterceptors(interceptorRegistry, executableMethod, interceptors);
            if (methodInterceptors.length > 0) {
                interceptedMethods.add(new InterceptedMethod<>((ExecutableMethod) executableMethod, (Interceptor[]) methodInterceptors));
            }
        }
        return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods, false, false, constructorValues);
    }

    /**
     * Creates the definition of an around proxy that holds its target separately.
     *
     * <p>The target is the intercepted bean, so a non-singleton interceptor is one instance per target, shared with the
     * target's post-construct and pre-destroy interception. The proxy is given only the singleton interceptors, the
     * target is resolved here, created with its own instances of the others, and the methods are intercepted with
     * those, see {@link ProxyTargetInterceptors}.</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> DefaultRuntimeProxyDefinition<T> aroundProxyTarget(BeanResolutionContext resolutionContext,
                                                                          BeanDefinition<T> proxyBeanDefinition,
                                                                          Object[] constructorValues) {
        BeanContext beanContext = resolutionContext.getContext();
        Argument<T> argument = Argument.of(proxyBeanDefinition.getBeanType());
        Qualifier<T> qualifier = (Qualifier<T>) resolutionContext.getCurrentQualifier();
        BeanDefinition<T> targetDefinition = beanContext.getProxyTargetBeanDefinition(argument, qualifier);
        ExecutableMethod<T, ?>[] executableMethods = targetDefinition.getExecutableMethods().toArray(new ExecutableMethod[0]);
        InterceptorRegistry interceptorRegistry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);

        List<BeanRegistration<?>> singletons = new ArrayList<>();
        if (executableMethods.length > 0) {
            // the hierarchy reverses the array it is given
            Qualifier<Interceptor<?, ?>> binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(executableMethods.clone()));
            for (BeanDefinition<Interceptor<?, ?>> definition : beanContext.getBeanDefinitions(Interceptor.ARGUMENT, binding)) {
                if (definition.isSingleton()) {
                    singletons.add(beanContext.getBeanRegistration(definition));
                }
            }
        }
        SharedInterceptorRegistrations.store(resolutionContext, proxyBeanDefinition, singletons);

        ProxyTargetInterceptors proxyTargetInterceptors = new ProxyTargetInterceptors(
            beanContext,
            interceptorRegistry,
            targetDefinition,
            executableMethods,
            singletons
        );
        T target = proxyTargetInterceptors.getProxyTargetBean(resolutionContext, argument, qualifier);
        resolutionContext.setAttribute(PROXY_TARGET_ATTRIBUTE, Map.entry(proxyBeanDefinition, target));
        Interceptor<?, ?>[][] interceptors = proxyTargetInterceptors.resolve(target);

        List<InterceptedMethod<T>> interceptedMethods = new ArrayList<>(executableMethods.length);
        for (int i = 0; i < executableMethods.length; i++) {
            if (interceptors[i].length > 0) {
                interceptedMethods.add(new InterceptedMethod<>((ExecutableMethod) executableMethods[i], (Interceptor[]) interceptors[i]));
            }
        }
        return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods, false, true, constructorValues);
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
        return introduction(resolutionContext, proxyBeanDefinition, new Object[0]);
    }

    /**
     * Creates a new instance for introduction advice.
     *
     * @param resolutionContext   The resolution context
     * @param proxyBeanDefinition The proxy bean definition
     * @param constructorValues   The constructor values
     * @param <T>                 The proxy type
     * @return The definition
     */
    public static <T> DefaultRuntimeProxyDefinition<T> introduction(BeanResolutionContext resolutionContext,
                                                                    BeanDefinition<T> proxyBeanDefinition,
                                                                    Object[] constructorValues) {

        Collection<ExecutableMethod<T, ?>> executableMethods = proxyBeanDefinition.getExecutableMethods();
        InterceptorRegistry interceptorRegistry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        Qualifier<Object> binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(executableMethods.toArray(new ExecutableMethod[0])));

        List<BeanRegistration<Interceptor<T, ?>>> interceptors = new ArrayList<>(resolutionContext.getBeanRegistrations(
            (Argument) Argument.of(Interceptor.class),
            binding
        ));
        SharedInterceptorRegistrations.store(resolutionContext, proxyBeanDefinition, interceptors);
        List<InterceptedMethod<T>> interceptedMethods = new ArrayList<>(executableMethods.size());
        for (ExecutableMethod<T, ?> executableMethod : executableMethods) {
            // Only the abstract methods are implemented by the introduction advice,
            // the concrete ones are intercepted and proceed to the actual implementation
            Interceptor<T, ?>[] methodInterceptors = executableMethod.isAbstract()
                ? InterceptorChain.resolveIntroductionInterceptors(interceptorRegistry, executableMethod, interceptors)
                : InterceptorChain.resolveAroundInterceptors(interceptorRegistry, executableMethod, interceptors);
            if (methodInterceptors.length > 0) {
                interceptedMethods.add(new InterceptedMethod<>((ExecutableMethod) executableMethod, (Interceptor[]) methodInterceptors));
            }
        }
        return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods, true, false, constructorValues);
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
        if (resolutionContext.getAttribute(PROXY_TARGET_ATTRIBUTE) instanceof Map.Entry<?, ?> entry
            && entry.getKey() == proxyBeanDefinition) {
            return (T) entry.getValue();
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
