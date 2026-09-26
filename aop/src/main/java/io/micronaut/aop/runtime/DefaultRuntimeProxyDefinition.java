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

import io.micronaut.aop.Around;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.chain.InterceptorChain;
import io.micronaut.aop.chain.LifecycleInterception;
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
        // the creator is not known to a definition compiled before 5.3, so it is taken to read the methods alone
        return around(resolutionContext, proxyBeanDefinition, isProxyTarget, constructorValues, false);
    }

    /**
     * Creates a new instance for around advice, for the creator that makes the proxy.
     *
     * @param resolutionContext   The resolution context
     * @param proxyBeanDefinition The proxy bean definition
     * @param isProxyTarget       Is proxy target bean
     * @param constructorValues   The constructor values
     * @param creator             The creator the definition is for
     * @param <T>                 The proxy type
     * @return The definition
     * @since 5.3.0
     */
    public static <T> DefaultRuntimeProxyDefinition<T> around(BeanResolutionContext resolutionContext,
                                                              BeanDefinition<T> proxyBeanDefinition,
                                                              boolean isProxyTarget,
                                                              Object[] constructorValues,
                                                              RuntimeProxyCreator creator) {
        return around(resolutionContext, proxyBeanDefinition, isProxyTarget, constructorValues, creator.selectsInterceptorsPerTarget());
    }

    private static <T> DefaultRuntimeProxyDefinition<T> around(BeanResolutionContext resolutionContext,
                                                               BeanDefinition<T> proxyBeanDefinition,
                                                               boolean isProxyTarget,
                                                               Object[] constructorValues,
                                                               boolean perTarget) {
        if (isProxyTarget) {
            return aroundTarget(resolutionContext, proxyBeanDefinition, constructorValues, perTarget);
        }
        // the proxy is the bean: its interceptors are the bean's own, resolved through the context creating it
        return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods(proxyBeanDefinition, resolutionContext, false), false, false, constructorValues);
    }

    /**
     * Creates the definition of a proxy fronting a separate target.
     *
     * <p>The proxy holds the singleton interceptors bound to the target's methods only. The non-singleton
     * interceptors of a target are the target's own, created with it as dependents of its registration, and the
     * interceptors of a method are selected from the target's registration. A singleton target is one to one with
     * the proxy, so it is resolved now and the selection is made once, unless an interceptor of a custom scope is
     * bound, whose instance is its scope's at the time of each call. Any other target is resolved by each call,
     * through {@link #targetBean()}, so the selection is made for the target of the call, through
     * {@link #interceptors(InterceptedMethod, Object)}; the intercepted methods then list the methods an interceptor
     * is bound to, with the interceptors that do not depend on the target.</p>
     *
     * <p>For a creator that reads the intercepted methods alone, they list every interceptor, the non-singleton ones
     * created once for the proxy and owned by no target, as before 5.3.</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> DefaultRuntimeProxyDefinition<T> aroundTarget(BeanResolutionContext resolutionContext,
                                                                     BeanDefinition<T> proxyBeanDefinition,
                                                                     Object[] constructorValues,
                                                                     boolean perTarget) {
        BeanContext beanContext = resolutionContext.getContext();
        Argument<T> argument = Argument.of(proxyBeanDefinition.getBeanType());
        Qualifier<T> qualifier = (Qualifier<T>) resolutionContext.getCurrentQualifier();
        BeanDefinition<T> targetDefinition = beanContext.getProxyTargetBeanDefinition(argument, qualifier);
        ExecutableMethod<T, ?>[] methods = targetDefinition.getExecutableMethods().toArray(new ExecutableMethod[0]);
        List<InterceptedMethod<T>> interceptedMethods = new ArrayList<>(methods.length);
        // a lazy proxy leaves the target to the first call, so even a singleton one is not resolved here
        boolean lazy = proxyBeanDefinition.getAnnotationMetadata().isTrue(Around.class, "lazy");
        if (targetDefinition.isSingleton() && !lazy && (!perTarget || !hasScopedInterceptors(resolutionContext, methods))) {
            BeanRegistration<T> target = resolutionContext.getProxyTargetBeanRegistration(targetDefinition, argument, qualifier);
            for (ExecutableMethod<T, ?> method : methods) {
                Interceptor<?, ?>[] interceptors = LifecycleInterception.resolveTargetInterceptors(beanContext, targetDefinition, target, method, false);
                if (interceptors.length > 0) {
                    interceptedMethods.add(new InterceptedMethod<>((ExecutableMethod) method, (Interceptor[]) interceptors));
                }
            }
            return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods, false, true, constructorValues);
        }
        // each intercepted method lists the interceptors that do not depend on the target, which is all of them when
        // no non-singleton is bound, and a creator that asks per target gets the target's own on top; a creator
        // that reads the methods alone gets them all, resolved as the proxy's own and destroyed with it, as in 5.2,
        // rather than losing the non-singletons
        Interceptor<?, ?>[][] shared = perTarget ? singletons(resolutionContext, methods) : LifecycleInterception.resolveInterceptors(resolutionContext, methods, false);
        for (int i = 0; i < methods.length; i++) {
            if (shared[i].length > 0 || !beanContext.getBeanDefinitions(Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(methods[i].getAnnotationMetadata())).isEmpty()) {
                interceptedMethods.add(new InterceptedMethod<>((ExecutableMethod) methods[i], (Interceptor[]) shared[i]));
            }
        }
        return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods, false, true, constructorValues, new TargetSelection<>(targetDefinition));
    }

    /**
     * The singleton interceptors of each method, which do not depend on the target.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Interceptor<?, ?>[][] singletons(BeanResolutionContext resolutionContext, ExecutableMethod<?, ?>[] methods) {
        Interceptor<?, ?>[][] result = new Interceptor[methods.length][];
        // the hierarchy reverses the array it is given, so it gets a copy
        List<BeanRegistration<Interceptor<?, ?>>> registrations = new ArrayList<>(resolutionContext.getInterceptorRegistrations(
            Interceptor.ARGUMENT,
            Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(methods.clone()), true)
        ));
        InterceptorRegistry registry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        for (int i = 0; i < methods.length; i++) {
            result[i] = InterceptorChain.resolveAroundInterceptors(registry, (ExecutableMethod) methods[i], (List) registrations);
        }
        return result;
    }

    /**
     * Whether an interceptor of a custom scope is bound to the methods, whose instance is its scope's at each call.
     */
    private static boolean hasScopedInterceptors(BeanResolutionContext resolutionContext, ExecutableMethod<?, ?>[] methods) {
        if (methods.length == 0) {
            return false;
        }
        for (BeanDefinition<Interceptor<?, ?>> definition : resolutionContext.getContext().getBeanDefinitions(
            Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(methods.clone())))) {
            if (!definition.isSingleton() && resolutionContext.isScopedInterceptor(definition)) {
                return true;
            }
        }
        return false;
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

        // Only the abstract methods are implemented by the introduction advice, the concrete ones are intercepted
        // and proceed to the actual implementation
        return new DefaultRuntimeProxyDefinition<>(proxyBeanDefinition, resolutionContext, interceptedMethods(proxyBeanDefinition, resolutionContext, true), true, false, constructorValues);
    }

    /**
     * The methods of a proxy that is the bean, with the interceptors selected for each, leaving out the methods no
     * interceptor applies to.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> List<InterceptedMethod<T>> interceptedMethods(BeanDefinition<T> proxyBeanDefinition,
                                                                    BeanResolutionContext resolutionContext,
                                                                    boolean introduction) {
        ExecutableMethod<T, ?>[] methods = proxyBeanDefinition.getExecutableMethods().toArray(new ExecutableMethod[0]);
        Interceptor<?, ?>[][] interceptors = LifecycleInterception.resolveInterceptors(resolutionContext, methods, introduction);
        List<InterceptedMethod<T>> interceptedMethods = new ArrayList<>(methods.length);
        for (int i = 0; i < methods.length; i++) {
            if (interceptors[i].length > 0) {
                interceptedMethods.add(new InterceptedMethod<>((ExecutableMethod) methods[i], (Interceptor[]) interceptors[i]));
            }
        }
        return interceptedMethods;
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
        return targetSelection == null ? method.interceptors() : targetSelection.interceptors(resolutionContext.getContext(), method, target);
    }

    @Override
    public Argument<?>[] constructorArguments() {
        return proxyBeanDefinition.getConstructor().getArguments();
    }

    /**
     * The selection of the interceptors of each call for the target of the call, which is kept on the registration of
     * the target.
     *
     * @param targetDefinition The definition of the target
     * @param <T>              The proxy type
     */
    @Internal
    public record TargetSelection<T>(BeanDefinition<T> targetDefinition) {

        @SuppressWarnings("unchecked")
        Interceptor<T, Object>[] interceptors(BeanContext beanContext, InterceptedMethod<T> method, T target) {
            return (Interceptor<T, Object>[]) LifecycleInterception.resolveTargetInterceptors(beanContext, targetDefinition, null, target, method.executableMethod(), false);
        }
    }
}
