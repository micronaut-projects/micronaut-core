/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.aop.Intercepted;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.Introduced;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.exceptions.UnimplementedAdviceException;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static io.micronaut.core.util.ArrayUtils.EMPTY_OBJECT_ARRAY;

/**
 * An internal representation of the {@link Interceptor} chain. This class implements {@link MethodInvocationContext} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 * @param <T> type
 * @param <R> result
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@UsedByGeneratedCode
public final class MethodInterceptorChain<T, R> extends InterceptorChain<T, R> implements MethodInvocationContext<T, R> {

    private final @Nullable InterceptorKind kind;

    /**
     * Constructor for empty parameters.
     *
     * @param interceptors array of interceptors
     * @param target target
     * @param executionHandle executionHandle
     */
    @UsedByGeneratedCode
    public MethodInterceptorChain(Interceptor<T, R>[] interceptors, T target, ExecutableMethod<T, R> executionHandle) {
        this(interceptors, target, executionHandle, (InterceptorKind) null);
    }

    /**
     * Constructor for empty parameters.
     *
     * @param interceptors array of interceptors
     * @param target target
     * @param executionHandle executionHandle
     * @param kind The interception kind
     */
    public MethodInterceptorChain(
        Interceptor<T, R>[] interceptors,
        T target,
        ExecutableMethod<T, R> executionHandle,
        @Nullable InterceptorKind kind) {
        super(interceptors, target, executionHandle, EMPTY_OBJECT_ARRAY);
        this.kind = kind;
    }

    /**
     * Constructor.
     *
     * @param interceptors array of interceptors
     * @param target target
     * @param executionHandle executionHandle
     * @param originalParameters originalParameters
     */
    @UsedByGeneratedCode
    public MethodInterceptorChain(Interceptor<T, R>[] interceptors, T target, ExecutableMethod<T, R> executionHandle, @Nullable Object... originalParameters) {
        super(interceptors, target, executionHandle, originalParameters);
        this.kind = null;
    }

    @Override
    public InterceptorKind getKind() {
        return this.kind != null ? kind : target instanceof Introduced ? InterceptorKind.INTRODUCTION : InterceptorKind.AROUND;
    }

    @Override
    @Nullable
    public R invoke(T instance, @Nullable Object... arguments) {
        return new MethodInterceptorChain<>(interceptors, instance, executionHandle, originalParameters).proceed();
    }

    @Override
    public boolean isSuspend() {
        return executionHandle.isSuspend();
    }

    @Override
    public boolean isAbstract() {
        return executionHandle.isAbstract();
    }

    @Override
    @Nullable
    public R proceed() throws RuntimeException {
        Interceptor<T, R> interceptor;
        if (interceptorCount == 0 || index == interceptorCount) {
            if (target instanceof Introduced && executionHandle.isAbstract()) {
                throw new UnimplementedAdviceException(executionHandle);
            } else {
                return executionHandle.invoke(target, getParameterValues());
            }
        } else {
            interceptor = this.interceptors[index++];
            if (LOG.isTraceEnabled()) {
                LOG.trace("Proceeded to next interceptor [{}] in chain for method invocation: {}", interceptor, executionHandle);
            }

            if (interceptor instanceof MethodInterceptor<T, R> methodInterceptor) {
                return methodInterceptor.intercept(this);
            } else {
                return interceptor.intercept(this);
            }
        }
    }

    @Override
    public String getMethodName() {
        return executionHandle.getMethodName();
    }

    @Override
    public Class<?>[] getArgumentTypes() {
        return executionHandle.getArgumentTypes();
    }

    @Override
    public Method getTargetMethod() {
        return executionHandle.getTargetMethod();
    }

    @Override
    public ReturnType<R> getReturnType() {
        return executionHandle.getReturnType();
    }

    @Override
    public Class<T> getDeclaringType() {
        return executionHandle.getDeclaringType();
    }

    @Override
    public String toString() {
        return executionHandle.toString();
    }

    @Override
    public ExecutableMethod<T, R> getExecutableMethod() {
        return executionHandle;
    }

    /**
     * Internal method that handles the logic for executing {@link InterceptorKind#POST_CONSTRUCT} interception.
     *
     * <p>Superseded by
     * {@link #initialize(BeanResolutionContext, BeanContext, BeanDefinition, ExecutableMethod, Object, Collection)},
     * which is what the framework calls now so that post-construct interception can reuse the interceptors already
     * resolved while the bean was constructed. This form resolves interceptors by binding, and nothing in the
     * framework or in currently generated code calls it. It is kept because it is part of the generated-code surface:
     * bean definitions compiled by earlier versions call it directly.</p>
     *
     * @param resolutionContext The resolution context
     * @param beanContext The bean context
     * @param definition The definition
     * @param postConstructMethod The post construct method
     * @param bean The bean
     * @param <T1> The bean type
     * @return the bean instance
     * @since 3.0.0
     */
    @Internal
    @UsedByGeneratedCode
    @Nullable
    public static <T1> T1 initialize(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> postConstructMethod,
        T1 bean) {
        return initialize(resolutionContext, beanContext, definition, postConstructMethod, bean, null);
    }

    /**
     * Variant of {@link #initialize(BeanResolutionContext, BeanContext, BeanDefinition, ExecutableMethod, Object)}
     * that reuses registrations already resolved for this bean.
     *
     * <p>Called for a bean whose interceptors were resolved once while it was constructed, so that a
     * {@code @Prototype} interceptor which ran the constructor also runs {@code @PostConstruct}. Passing
     * {@code null} resolves interceptors by binding, which is what the five-argument form does and what generated
     * code from earlier versions continues to do.</p>
     *
     * @param resolutionContext  The resolution context
     * @param beanContext        The bean context
     * @param definition         The definition
     * @param postConstructMethod The post construct method
     * @param bean               The bean
     * @param interceptors       Registrations resolved for this bean, or {@code null} to resolve them
     * @param <T1>               The bean type
     * @return the bean instance
     * @since 5.2.0
     */
    @Internal
    @UsedByGeneratedCode
    @Nullable
    public static <T1> T1 initialize(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> postConstructMethod,
        T1 bean,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> interceptors) {
        return doIntercept(
            resolutionContext,
            beanContext,
            definition,
            postConstructMethod,
            bean,
            InterceptorKind.POST_CONSTRUCT,
            interceptors
        );
    }

    /**
     * Internal method that handles the logic for executing {@link InterceptorKind#PRE_DESTROY} interception.
     *
     * <p>Resolves interceptors by binding. Unlike post-construct, destruction has nothing resolved in advance to hand
     * over, because it runs with a fresh resolution context, so this remains the form the framework calls; the
     * overload taking registrations exists for a caller that does hold them.</p>
     *
     * @param resolutionContext The resolution context
     * @param beanContext The bean context
     * @param definition The definition
     * @param preDestroyMethod The pre destroy method
     * @param bean The bean
     * @param <T1> The bean type
     * @return the bean instance
     * @since 3.0.0
     */
    @Internal
    @UsedByGeneratedCode
    @Nullable
    public static <T1> T1 dispose(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> preDestroyMethod,
        T1 bean) {
        return dispose(resolutionContext, beanContext, definition, preDestroyMethod, bean, null);
    }

    /**
     * Variant of {@link #dispose(BeanResolutionContext, BeanContext, BeanDefinition, ExecutableMethod, Object)} that
     * reuses registrations already resolved for this bean.
     *
     * <p>Nothing supplies registrations here today: destruction runs with a fresh resolution context, so a bean
     * reaches its interceptors either through the field on its proxy or through the registrations the container
     * passes to the dispose call. The parameter exists so a caller that does hold them can hand them over.</p>
     *
     * @param resolutionContext The resolution context
     * @param beanContext       The bean context
     * @param definition        The definition
     * @param preDestroyMethod  The pre destroy method
     * @param bean              The bean
     * @param interceptors      Registrations resolved for this bean, or {@code null} to resolve them
     * @param <T1>              The bean type
     * @return the bean instance
     * @since 5.2.0
     */
    @Internal
    @UsedByGeneratedCode
    @Nullable
    public static <T1> T1 dispose(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> preDestroyMethod,
        T1 bean,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> interceptors) {
        return doIntercept(
            resolutionContext,
            beanContext,
            definition,
            preDestroyMethod,
            bean,
            InterceptorKind.PRE_DESTROY,
            interceptors
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    private static <T1> T1 doIntercept(
        BeanResolutionContext resolutionContext,
        BeanContext beanContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> interceptedMethod,
        T1 bean,
        InterceptorKind kind,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> shared) {
        final AnnotationMetadata annotationMetadata = interceptedMethod.getAnnotationMetadata();
        final Collection<AnnotationValue<?>> binding = resolveInterceptorValues(annotationMetadata, kind);

        final Collection<BeanRegistration<Interceptor<?, ?>>> resolved;
        if (shared != null && !shared.isEmpty()) {
            // Resolved once while the bean was created and handed to this interception point.
            resolved = shared;
        } else if (bean instanceof Intercepted intercepted && !intercepted.$interceptorRegistrations().isEmpty()) {
            // Retained by the generated proxy.
            resolved = intercepted.$interceptorRegistrations();
        } else if (kind == InterceptorKind.PRE_DESTROY) {
            // Destruction runs with a fresh resolution context, so a bean with lifecycle advice but no proxy has
            // nothing handed to it. Reuse the interceptor instances the bean still owns.
            resolved = resolveLifecycleInterceptors(resolutionContext, binding);
        } else {
            resolved = resolutionContext.getBeanRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBindingValues(binding));
        }
        final InterceptorRegistry interceptorRegistry = beanContext.getBean(InterceptorRegistry.ARGUMENT);
        final Interceptor[] resolvedInterceptors = interceptorRegistry
            .resolveInterceptors(
                (ExecutableMethod) interceptedMethod,
                (Collection) resolved,
                kind
            );

        if (ArrayUtils.isNotEmpty(resolvedInterceptors)) {
            final MethodInterceptorChain<T1, T1> chain = new MethodInterceptorChain<>(
                resolvedInterceptors,
                bean,
                interceptedMethod,
                kind
            );
            return Objects.requireNonNull(
                chain.proceed(),
                kind.name() + " interceptor chain illegal returned null for type: " + definition.getBeanType()
            );
        } else {
            return interceptedMethod.invoke(bean);
        }
    }

    /**
     * Resolves the interceptor candidates for pre-destroy interception.
     *
     * <p>Destruction runs with a fresh resolution context, so a bean with lifecycle advice but no retaining proxy has
     * nothing handed to it. The interceptor instances it owns are still reachable through the registrations the
     * container passes to the dispose call, and every interceptor bound to the bean's lifecycle was created while the
     * bean was, so those registrations are the candidate set. When the bean owns none, candidates are resolved by
     * binding as before.</p>
     *
     * @param resolutionContext The resolution context
     * @param binding           The binding of the interception point
     * @return The interceptor registrations to select from
     * @since 5.2.0
     */
    private static Collection<BeanRegistration<Interceptor<?, ?>>> resolveLifecycleInterceptors(
        BeanResolutionContext resolutionContext,
        Collection<AnnotationValue<?>> binding) {

        final List<BeanRegistration<Interceptor<?, ?>>> existing = findExistingInterceptors(resolutionContext);
        if (existing.isEmpty()) {
            return resolutionContext.getBeanRegistrations(
                Interceptor.ARGUMENT,
                Qualifiers.byInterceptorBindingValues(binding)
            );
        }
        return existing;
    }

    /**
     * Finds the interceptor instances that the bean being destroyed already owns.
     *
     * <p>The scenario is pre-destroy interception of a bean with lifecycle advice but no around proxy. Every
     * interceptor bound to such a bean's lifecycle was created while the bean was, and became one of its dependent
     * registrations, so those registrations are the candidate set and nothing further needs resolving.</p>
     *
     * <p>Because the resolution context at destruction is a fresh one with no dependents of its own, the
     * registrations owned by the bean arrive through {@link BeanResolutionContext#EXISTING_DEPENDENT_BEANS}.</p>
     *
     * <p>Returns empty for a bean that owns no interceptors, which includes a prototype created through
     * {@code createBean} and destroyed through {@code destroyBean(Object)}: no registration is tracked for it, so
     * nothing can be handed over and its interceptors are resolved by binding instead.</p>
     *
     * <p>Singleton interceptors are ignored because resolving them again yields the same instance.</p>
     *
     * @param resolutionContext The resolution context
     * @return The reusable interceptor registrations, never {@code null}
     * @since 5.2.0
     */
    @SuppressWarnings("unchecked")
    private static List<BeanRegistration<Interceptor<?, ?>>> findExistingInterceptors(BeanResolutionContext resolutionContext) {
        List<BeanRegistration<?>> dependents = resolutionContext.getDependentBeans();
        if (dependents.isEmpty() && resolutionContext.getAttribute(BeanResolutionContext.EXISTING_DEPENDENT_BEANS) instanceof List<?> attribute) {
            dependents = (List<BeanRegistration<?>>) attribute;
        }
        if (dependents.isEmpty()) {
            return Collections.emptyList();
        }
        List<BeanRegistration<Interceptor<?, ?>>> interceptors = null;
        for (BeanRegistration<?> dependent : dependents) {
            if (dependent.getBean() instanceof Interceptor) {
                if (interceptors == null) {
                    interceptors = new ArrayList<>(dependents.size());
                }
                interceptors.add((BeanRegistration<Interceptor<?, ?>>) dependent);
            }
        }
        return CollectionUtils.isEmpty(interceptors) ? Collections.emptyList() : interceptors;
    }
}
