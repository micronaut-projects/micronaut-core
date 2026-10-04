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
package io.micronaut.aop.chain;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDependencyGroup;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a proxy that fronts a separate target delegates to: it finds the target of a call, selects the
 * interceptors of the call and runs them. A generated proxy is injected with one, of the kind its declaration
 * asks for, and has no logic of its own.
 *
 * <p>A handler is an unscoped bean, so each proxy gets its own, as a dependent that is destroyed with it. The
 * kinds differ in where the target comes from:</p>
 * <ul>
 *     <li>{@link FixedProxyTargetHandler}: one target, resolved as the proxy is created;</li>
 *     <li>{@link LazyProxyTargetHandler}: looked up for every call;</li>
 *     <li>{@link CachedProxyTargetHandler}: resolved on the first call and kept;</li>
 *     <li>{@link HotSwapProxyTargetHandler}: resolved as the proxy is created, and replaceable.</li>
 * </ul>
 *
 * <p>Each kind takes the interceptors either from the proxy, which is the default, or from the target of the
 * call, when the proxy is declared with {@code lazyInterceptorsPerTarget}.</p>
 *
 * @param <T> The target type
 * @since 5.3.0
 */
@Internal
@SuppressWarnings({"NullAway.Init", "rawtypes", "unchecked"})
public abstract class ProxyTargetHandler<T> {
    /**
     * The name of the annotation on the handler parameter of a proxy constructor that holds the interceptor
     * binding of the proxy. It is not a qualifier: every proxy is injected with the same handler bean.
     */
    public static final String BINDING = "io.micronaut.aop.chain.ProxyTargetHandler.Binding";

    private final InterceptorChainFactory chainFactory;
    private final InterceptorCandidateResolver resolver;
    private final BeanContext beanContext;
    /** The binding the interceptors of the proxy are qualified by, or null when it binds none. */
    private final @Nullable Qualifier<Interceptor<?, ?>> binding;
    /** The interceptors the proxy was created with; empty when they are those of the target. */
    private final List<BeanRegistration<Interceptor<?, ?>>> registrations;
    /** The context the proxy is created in, released once the handler is bound. */
    private @Nullable BeanResolutionContext creationContext;
    /** The definition, the type and the qualifier of the target. */
    private BeanDefinition<T> targetDefinition;
    private Argument<T> targetType;
    private @Nullable Qualifier<T> qualifier;
    /** The dependencies of the proxy. */
    private @Nullable BeanDependencyGroup dependencies;
    /** The proxied methods; the proxy refers to each by its index. */
    private ExecutableMethod<T, ?>[] methods;
    private boolean introduction;
    /** Whether the interceptors of a call are those of its target and not those of the proxy. */
    private boolean perTarget;
    /** The interceptors of each method selected from those of the proxy, unless they are those of the target. */
    private Interceptor[] @Nullable [] interceptors;

    ProxyTargetHandler(Creation creation) {
        this.chainFactory = creation.chainFactory();
        this.resolver = chainFactory.candidateResolver();
        this.binding = creation.binding();
        this.creationContext = creation.resolutionContext();
        this.beanContext = creation.beanContext();
        this.qualifier = (Qualifier<T>) creation.qualifier();
        // The interceptors of the proxy are created here, in the resolution of the proxy, so they are its dependents.
        // They are needed before the proxy exists, to intercept its construction. A binding of nothing, which a proxy
        // that takes its interceptors from its targets has, qualifies none.
        this.registrations = binding == null
            ? List.of()
            : new ArrayList(creationContext.getBeanRegistrations(Interceptor.ARGUMENT, binding));
    }

    /**
     * Binds the handler to the proxy it was injected into. Called once, from the constructor of the proxy.
     *
     * @param targetType The type of the target
     * @param introduction Whether the proxy is an introduction
     * @param perTarget Whether the interceptors of a call are those of its target
     * @param methodNames The names of the proxied methods
     * @param methodArguments The argument types of the proxied methods
     */
    @UsedByGeneratedCode
    public final void bind(Argument<T> targetType,
                                            boolean introduction,
                                            boolean perTarget,
                                            String[] methodNames,
                                            Class<?>[][] methodArguments) {
        BeanResolutionContext resolutionContext = creationContext;
        if (resolutionContext == null) {
            throw new IllegalStateException("The handler is already bound to a proxy");
        }
        creationContext = null;
        this.targetType = targetType;
        this.introduction = introduction;
        this.perTarget = perTarget;
        this.dependencies = resolutionContext.getBeanDependencyGroup();
        this.targetDefinition = beanContext.getProxyTargetBeanDefinition(targetType, qualifier);
        if (!perTarget) {
            // an unscoped target created for this proxy is intercepted with the interceptors of the proxy
            resolutionContext.prepareProxyTarget(targetDefinition, registrations);
        }
        initTarget(resolutionContext);
        ExecutableMethod<T, ?>[] proxied = new ExecutableMethod[methodNames.length];
        for (int i = 0; i < proxied.length; i++) {
            proxied[i] = targetDefinition.getRequiredMethod(methodNames[i], methodArguments[i]);
        }
        this.methods = proxied;
        if (!perTarget) {
            interceptors = selectFromProxy();
        }
        initInterceptors();
    }

    /**
     * Prepares the target, or the means of finding it later.
     *
     * @param resolutionContext The context the proxy is created in
     */
    abstract void initTarget(BeanResolutionContext resolutionContext);

    /** Prepares the interceptors of targets, for a proxy that takes them from its targets. Runs after the target is prepared. */
    void initInterceptors() {
    }

    private Interceptor[][] selectFromProxy() {
        InterceptorKind kind = introduction ? InterceptorKind.INTRODUCTION : InterceptorKind.AROUND;
        Interceptor[][] selected = new Interceptor[methods.length][];
        for (int i = 0; i < selected.length; i++) {
            selected[i] = resolver.selectMethodInterceptors((ExecutableMethod) methods[i], (List) registrations, kind);
        }
        return selected;
    }

    /**
     * @return Whether the interceptors of a call are those of its target and not those of the proxy
     */
    final boolean isPerTarget() {
        return perTarget;
    }

    /**
     * @param context The context to resolve through
     * @return The target, with its registration
     */
    final BeanRegistration<T> resolveTargetRegistration(BeanResolutionContext context) {
        return context.getProxyTargetBeanRegistration(targetDefinition, targetType, qualifier);
    }

    /**
     * @param context The context to resolve through
     * @return The target
     */
    final T resolveTarget(BeanResolutionContext context) {
        return context.getProxyTargetBean(targetDefinition, targetType, qualifier);
    }

    /**
     * @param context The context the proxy is created in
     * @return A copy of it to look the target up through after the proxy is created
     */
    final BeanResolutionContext lookupContext(BeanResolutionContext context) {
        return context.copyForLazyProxyTarget(targetDefinition);
    }

    /**
     * @param bean A target the proxy was given
     * @return Its registration, if the context holds one
     */
    final @Nullable BeanRegistration<T> findRegistration(T bean) {
        return (BeanRegistration<T>) resolver.findProxyTargetRegistration(beanContext, bean);
    }

    /**
     * @return A holder of the interceptors of each target the proxy fronts
     */
    final TargetInterceptors interceptorsOfTargets() {
        return resolver.targetInterceptors(beanContext, methods, introduction, dependencies);
    }

    /**
     * @param registration The registration of the one target of the proxy
     * @return The interceptors of that target, which the caller keeps
     */
    final Interceptor[][] interceptorsOfTarget(BeanRegistration<T> registration) {
        return resolver.resolveTargetInterceptors(beanContext, methods, introduction, registration, registration.getBean(), dependencies);
    }

    /**
     * Runs a call with the interceptors of the proxy.
     *
     * @param target The target of the call
     * @param index The index of the proxied method
     * @param arguments The arguments of the call
     * @return What the call returns
     */
    final @Nullable Object proceed(Object target, int index, Object[] arguments) {
        Interceptor[][] selected = interceptors;
        if (selected == null) {
            throw new IllegalStateException("The proxy takes its interceptors from its targets");
        }
        return proceed(target, index, selected[index], arguments);
    }

    /**
     * Runs a call with the given interceptors.
     *
     * @param target The target of the call
     * @param index The index of the proxied method
     * @param selected The interceptors of the method
     * @param arguments The arguments of the call
     * @return What the call returns
     */
    final @Nullable Object proceed(Object target, int index, Interceptor[] selected, Object[] arguments) {
        return chainFactory.buildMethodChain(target, (ExecutableMethod) methods[index], selected, arguments).proceed();
    }

    /**
     * Runs one call of the proxy.
     *
     * @param index The index of the proxied method
     * @param arguments The arguments of the call
     * @return What the call returns
     */
    @UsedByGeneratedCode
    public abstract @Nullable Object invoke(int index, Object[] arguments);

    /**
     * @return The target of a call made now
     */
    @UsedByGeneratedCode
    public abstract T target();

    /**
     * @return Whether the handler holds a target
     */
    @UsedByGeneratedCode
    public boolean hasCachedTarget() {
        return false;
    }

    /** Forgets the target the handler holds, if it can resolve another. */
    @UsedByGeneratedCode
    public void clearCachedTarget() {
    }

    /**
     * @return The registration of the target the handler holds, or null
     */
    @UsedByGeneratedCode
    public @Nullable BeanRegistration<T> targetRegistration() {
        return null;
    }

    /**
     * @param qualifier The qualifier later lookups of the target use
     */
    @UsedByGeneratedCode
    public final void withQualifier(@Nullable Qualifier<T> qualifier) {
        this.qualifier = qualifier;
    }

    /**
     * @return The dependencies of the proxy
     */
    @UsedByGeneratedCode
    public final @Nullable BeanDependencyGroup dependencies() {
        return dependencies;
    }

    /**
     * @return A copy of the proxied methods
     */
    @UsedByGeneratedCode
    public final ExecutableMethod<?, ?>[] interceptedMethods() {
        return methods.clone();
    }

    /**
     * @return The interceptors the proxy was created with
     */
    @UsedByGeneratedCode
    public final List<BeanRegistration<Interceptor<?, ?>>> interceptorRegistrations() {
        return registrations;
    }

    /**
     * What a handler is created from: the injection point it is injected at, which is the constructor of a proxy.
     *
     * @param chainFactory The chain factory of the context
     * @param binding The binding the interceptors of the proxy are qualified by, or null when it binds none
     * @param resolutionContext The context the proxy is created in
     * @param beanContext The bean context
     * @param qualifier The qualifier the proxy is created with, which is that of its target
     */
    public record Creation(InterceptorChainFactory chainFactory,
                           @Nullable Qualifier<Interceptor<?, ?>> binding,
                           BeanResolutionContext resolutionContext,
                           BeanContext beanContext,
                           @Nullable Qualifier<?> qualifier) {
    }
}
