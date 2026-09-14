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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
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
 * @param targetSelection     The selection of the interceptors of each call for the target of the call, for a proxy
 *                            fronting a target that is not a singleton; {@code null} for every other proxy
 * @param <T>                 The proxy type
 */
@Internal
@NullMarked
public record DefaultRuntimeProxyDefinition<T>(BeanDefinition<T> proxyBeanDefinition,
                                               BeanResolutionContext resolutionContext,
                                               List<InterceptedMethod<T>> interceptedMethods,
                                               boolean introduction,
                                               boolean proxyTarget,
                                               Object[] constructorValues,
                                               @Nullable TargetSelection<T> targetSelection) implements RuntimeProxyDefinition<T> {

    /**
     * Creates a definition whose intercepted methods carry their interceptors.
     *
     * @param proxyBeanDefinition The proxy bean definition
     * @param resolutionContext   The bean resolution context
     * @param interceptedMethods  The intercepted methods
     * @param introduction        Whether the proxy is an introduction
     * @param proxyTarget         Whether the proxy is a proxy target bean
     * @param constructorValues   The constructor values
     */
    public DefaultRuntimeProxyDefinition(BeanDefinition<T> proxyBeanDefinition,
                                         BeanResolutionContext resolutionContext,
                                         List<InterceptedMethod<T>> interceptedMethods,
                                         boolean introduction,
                                         boolean proxyTarget,
                                         Object[] constructorValues) {
        this(proxyBeanDefinition, resolutionContext, interceptedMethods, introduction, proxyTarget, constructorValues, null);
    }

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
            return aroundTarget(resolutionContext, proxyBeanDefinition, constructorValues);
        }
        Collection<ExecutableMethod<T, ?>> executableMethods = proxyBeanDefinition.getExecutableMethods();
        InterceptorRegistry interceptorRegistry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        Qualifier<Object> binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(executableMethods.toArray(new ExecutableMethod[0])));

        // the bean's own: a non-singleton interceptor is created as a dependent of the proxy, which is the bean, and
        // is the instance its lifecycle interception uses as well
        List<BeanRegistration<Interceptor<T, ?>>> interceptors = new ArrayList<>(resolutionContext.getInterceptorRegistrations(
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
        return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods, false, false, constructorValues);
    }

    /**
     * Creates the definition of a proxy fronting a separate target.
     *
     * <p>The proxy holds the singleton interceptors bound to the target's methods only. The non-singleton
     * interceptors of a target are the target's own, created with it as dependents of its registration, and the
     * interceptors of a method are selected from the target's registration. A singleton target is one to one with
     * the proxy, so it is resolved now and the selection is made once. Any other target is resolved by each call,
     * through {@link #targetBean()}, so the selection is made for the target of the call, through
     * {@link #interceptors(InterceptedMethod, Object)}; the intercepted methods then list the methods an interceptor
     * is bound to, with no interceptors of their own.</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> DefaultRuntimeProxyDefinition<T> aroundTarget(BeanResolutionContext resolutionContext,
                                                                     BeanDefinition<T> proxyBeanDefinition,
                                                                     Object[] constructorValues) {
        BeanContext beanContext = resolutionContext.getContext();
        Argument<T> argument = Argument.of(proxyBeanDefinition.getBeanType());
        Qualifier<T> qualifier = (Qualifier<T>) resolutionContext.getCurrentQualifier();
        BeanDefinition<T> targetDefinition = beanContext.getProxyTargetBeanDefinition(argument, qualifier);
        ExecutableMethod<T, ?>[] methods = targetDefinition.getExecutableMethods().toArray(new ExecutableMethod[0]);
        InterceptorRegistry interceptorRegistry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        List<BeanRegistration<?>> singletons = new ArrayList<>();
        if (methods.length > 0) {
            Qualifier<Interceptor<?, ?>> binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(methods.clone()));
            for (BeanDefinition<Interceptor<?, ?>> definition : beanContext.getBeanDefinitions(Interceptor.ARGUMENT, binding)) {
                if (definition.isSingleton()) {
                    singletons.add(beanContext.getBeanRegistration(definition));
                }
            }
        }
        ProxyTargetInterceptors selection = new ProxyTargetInterceptors(beanContext, interceptorRegistry, methods, singletons, false);
        List<InterceptedMethod<T>> interceptedMethods = new ArrayList<>(methods.length);
        if (targetDefinition.isSingleton()) {
            BeanRegistration<T> target = resolutionContext.getProxyTargetBeanRegistration(targetDefinition, argument, qualifier);
            Interceptor<?, ?>[][] interceptors = selection.resolve(target);
            for (int i = 0; i < methods.length; i++) {
                if (interceptors[i].length > 0) {
                    interceptedMethods.add(new InterceptedMethod<>((ExecutableMethod) methods[i], (Interceptor[]) interceptors[i]));
                }
            }
            return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods, false, true, constructorValues);
        }
        Map<ExecutableMethod<?, ?>, Integer> indexes = new IdentityHashMap<>();
        for (int i = 0; i < methods.length; i++) {
            if (selection.intercepted(i)) {
                interceptedMethods.add(new InterceptedMethod<>((ExecutableMethod) methods[i], new Interceptor[0]));
                indexes.put(methods[i], i);
            }
        }
        return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods, false, true, constructorValues, new TargetSelection<>(selection, indexes));
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

        List<BeanRegistration<Interceptor<T, ?>>> interceptors = new ArrayList<>(resolutionContext.getInterceptorRegistrations(
            (Argument) Argument.of(Interceptor.class),
            binding
        ));
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
        Class<T> beanType = proxyBeanDefinition.getBeanType();
        BeanContext beanContext = resolutionContext
            .getContext();
        Argument<T> argument = Argument.of(beanType);
        Qualifier<T> qualifier = (Qualifier<T>) resolutionContext.getCurrentQualifier();
        BeanDefinition<T> proxyTargetBeanDefinition = beanContext
            .getProxyTargetBeanDefinition(argument, qualifier);
        // with its registration, so that a target created for no scope is found again from the instance by the
        // selection of the interceptors of a call
        return resolutionContext.getProxyTargetBeanRegistration(proxyTargetBeanDefinition, argument, qualifier).getBean();
    }

    @Override
    public Interceptor<T, Object>[] interceptors(InterceptedMethod<T> method, T target) {
        return targetSelection == null ? method.interceptors() : targetSelection.interceptors(method, target);
    }

    @Override
    public Argument<?>[] constructorArguments() {
        return proxyBeanDefinition.getConstructor().getArguments();
    }

    /**
     * The selection of the interceptors of each call for the target of the call.
     *
     * @param selection The selection, by the index of the method
     * @param indexes   The index of each intercepted method
     * @param <T>       The proxy type
     */
    @Internal
    public record TargetSelection<T>(ProxyTargetInterceptors selection, Map<ExecutableMethod<?, ?>, Integer> indexes) {

        @SuppressWarnings("unchecked")
        Interceptor<T, Object>[] interceptors(InterceptedMethod<T> method, T target) {
            Integer index = indexes.get(method.executableMethod());
            if (index == null) {
                return method.interceptors();
            }
            return (Interceptor<T, Object>[]) selection.get(index, (Object) target);
        }
    }
}
