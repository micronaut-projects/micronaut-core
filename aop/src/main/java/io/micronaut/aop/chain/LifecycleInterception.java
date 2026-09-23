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

import io.micronaut.aop.Intercepted;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.RegisteredBeanInterceptors;
import io.micronaut.context.exceptions.ConstructorAdviceException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Resolves the interceptors of a bean, and runs the interception of its lifecycle events: its construction, its
 * post-construct and its pre-destroy.
 *
 * <p>Each event resolves the interceptors bound to it as the bean's own, through
 * {@link BeanResolutionContext#getInterceptorRegistrations(io.micronaut.core.type.Argument, io.micronaut.context.Qualifier)},
 * selects the ones that apply through the {@link InterceptorRegistry}, and runs the chain of the event. The chains
 * themselves, {@link ConstructorInterceptorChain} and {@link MethodInterceptorChain}, only run.</p>
 *
 * <p>The methods of a generated proxy are selected for here too: a proxy that is the bean resolves them once in its
 * constructor, and a proxy that fronts a separate target asks for the target of each call, whose own interceptors
 * are the ones to run.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class LifecycleInterception {

    /**
     * The internal constructor parameters a generated proxy appends after the bean's own: the resolution context,
     * the bean context and the qualifier.
     */
    public static final int PROXY_CONSTRUCTOR_PARAMETERS = 3;

    // the key of the selection of interceptors kept on the registration of a target
    private static final Object TARGET_SELECTION = new Object();

    private LifecycleInterception() {
    }

    /**
     * The interceptors a bean's construction, post-construct and pre-destroy interception all select from: the
     * bean's own, resolved once for construction by every binding the bean declares, whatever kind each was declared
     * for, so that the non-singleton ones are created with the bean and found again by its later interception points.
     *
     * <p>A proxy fronting a separate target never reaches here: the definition writer intercepts the construction of
     * the target alone, as CDI never intercepts the construction of a client proxy.</p>
     *
     * @param resolutionContext The resolution context
     * @param constructor       The constructor, whose metadata is the bean's combined with the constructor's
     * @param <T>               The bean type
     * @return The interceptors, or {@code null} when the bean binds none
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    public static <T> List<BeanRegistration<Interceptor<T, T>>> constructionInterceptors(BeanResolutionContext resolutionContext,
                                                                                        AnnotationMetadataProvider constructor) {
        AnnotationMetadata metadata = constructor.getAnnotationMetadata();
        if (metadata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING).isEmpty()) {
            return null;
        }
        return new ArrayList(resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(metadata)));
    }

    /**
     * Instantiates a bean whose construction is intercepted, a generated proxy with the three internal constructor
     * parameters it appends.
     *
     * @param resolutionContext The resolution context
     * @param beanContext       The bean context
     * @param interceptors      The interceptors, as
     *                          {@link #constructionInterceptors(BeanResolutionContext, AnnotationMetadataProvider)}
     *                          resolves them, or {@code null} to resolve them here
     * @param definition        The definition
     * @param constructor       The bean constructor
     * @param parameters        The resolved parameters
     * @param <T>               The bean type
     * @return The instantiated bean
     */
    public static <T> T instantiate(BeanResolutionContext resolutionContext,
                                    BeanContext beanContext,
                                    @Nullable List<BeanRegistration<Interceptor<T, T>>> interceptors,
                                    BeanDefinition<T> definition,
                                    BeanConstructor<T> constructor,
                                    @Nullable Object... parameters) {
        return instantiate(resolutionContext, beanContext, interceptors, definition, constructor, PROXY_CONSTRUCTOR_PARAMETERS, parameters);
    }

    /**
     * Instantiates a bean whose construction is intercepted, with the given number of internal constructor
     * parameters. Only the deprecated entry points on the chains, which bean definitions compiled by earlier
     * versions call with the count they were built with, need a count other than the default.
     *
     * @param resolutionContext                         The resolution context
     * @param beanContext                               The bean context
     * @param interceptors                              The interceptors, or {@code null} to resolve them as the
     *                                                  bean's own
     * @param definition                                The definition
     * @param constructor                               The bean constructor
     * @param additionalProxyConstructorParametersCount The internal constructor parameters the proxy appends
     * @param parameters                                The resolved parameters
     * @param <T>                                       The bean type
     * @return The instantiated bean
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> T instantiate(BeanResolutionContext resolutionContext,
                             BeanContext beanContext,
                             @Nullable List<BeanRegistration<Interceptor<T, T>>> interceptors,
                             BeanDefinition<T> definition,
                             BeanConstructor<T> constructor,
                             int additionalProxyConstructorParametersCount,
                             @Nullable Object... parameters) {
        if (interceptors == null) {
            interceptors = constructionInterceptors(resolutionContext, constructor);
        }
        if (interceptors == null) {
            // no binding at all, so nothing can qualify: the constructor runs unadvised
            interceptors = List.of();
        }
        final InterceptorRegistry interceptorRegistry = beanContext.getBean(InterceptorRegistry.ARGUMENT);
        final Interceptor<T, T>[] resolvedInterceptors = interceptorRegistry.resolveConstructorInterceptors(constructor, interceptors);
        ConstructorInterceptorChain<T> chain = new ConstructorInterceptorChain<>(
            definition,
            constructor,
            resolvedInterceptors,
            additionalProxyConstructorParametersCount,
            parameters
        );
        T bean;
        try {
            bean = chain.proceed();
        } catch (ConstructorAdviceException e) {
            // Already carried, by the advice around a bean this one's construction depends on
            throw e;
        } catch (RuntimeException e) {
            if (e == chain.bodyFailure) {
                // The constructor's own body threw. Keep the wrapping an unadvised constructor's throwable gets
                throw e;
            }
            // The advice around the constructor threw. Advice around a method reaches its caller as it was
            // thrown; carry this one so that construction advice does too
            throw new ConstructorAdviceException(e);
        }
        return Objects.requireNonNull(bean, "Constructor interceptor chain illegally returned null for constructor: " + constructor.getDescription());
    }

    /**
     * Runs the {@link InterceptorKind#POST_CONSTRUCT} interception of a bean.
     *
     * @param resolutionContext   The resolution context
     * @param beanContext         The bean context
     * @param definition          The definition
     * @param postConstructMethod The post construct method
     * @param bean                The bean
     * @param <T>                 The bean type
     * @return The bean instance
     */
    @Nullable
    public static <T> T initialize(BeanResolutionContext resolutionContext,
                                   BeanContext beanContext,
                                   BeanDefinition<T> definition,
                                   ExecutableMethod<T, T> postConstructMethod,
                                   T bean) {
        return intercept(resolutionContext, beanContext, definition, postConstructMethod, bean, InterceptorKind.POST_CONSTRUCT, null);
    }

    /**
     * Runs the {@link InterceptorKind#POST_CONSTRUCT} interception of a bean with registrations a caller resolved,
     * as a definition compiled by 5.2 hands them; the deprecated entry point on the chain delegates here.
     */
    @Nullable
    static <T> T initialize(BeanResolutionContext resolutionContext,
                            BeanContext beanContext,
                            BeanDefinition<T> definition,
                            ExecutableMethod<T, T> postConstructMethod,
                            T bean,
                            @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> handed) {
        return intercept(resolutionContext, beanContext, definition, postConstructMethod, bean, InterceptorKind.POST_CONSTRUCT, handed);
    }

    /**
     * Runs the {@link InterceptorKind#PRE_DESTROY} interception of a bean.
     *
     * @param resolutionContext The resolution context
     * @param beanContext       The bean context
     * @param definition        The definition
     * @param preDestroyMethod  The pre destroy method
     * @param bean              The bean
     * @param <T>               The bean type
     * @return The bean instance
     */
    @Nullable
    public static <T> T dispose(BeanResolutionContext resolutionContext,
                                BeanContext beanContext,
                                BeanDefinition<T> definition,
                                ExecutableMethod<T, T> preDestroyMethod,
                                T bean) {
        return intercept(resolutionContext, beanContext, definition, preDestroyMethod, bean, InterceptorKind.PRE_DESTROY, null);
    }

    /**
     * Runs the {@link InterceptorKind#PRE_DESTROY} interception of a bean with registrations a caller resolved; the
     * deprecated entry point on the chain delegates here.
     */
    @Nullable
    static <T> T dispose(BeanResolutionContext resolutionContext,
                         BeanContext beanContext,
                         BeanDefinition<T> definition,
                         ExecutableMethod<T, T> preDestroyMethod,
                         T bean,
                         @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> handed) {
        return intercept(resolutionContext, beanContext, definition, preDestroyMethod, bean, InterceptorKind.PRE_DESTROY, handed);
    }

    @SuppressWarnings({"unchecked", "rawtypes", "removal"})
    @Nullable
    private static <T> T intercept(BeanResolutionContext resolutionContext,
                                   BeanContext beanContext,
                                   BeanDefinition<T> definition,
                                   ExecutableMethod<T, T> interceptedMethod,
                                   T bean,
                                   InterceptorKind kind,
                                   @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> handed) {
        final AnnotationMetadata annotationMetadata = interceptedMethod.getAnnotationMetadata();
        final Collection<AnnotationValue<?>> binding = AbstractInterceptorChain.resolveInterceptorValues(annotationMetadata, kind);

        final Collection<BeanRegistration<Interceptor<?, ?>>> resolved;
        if (handed != null && !handed.isEmpty()) {
            // resolved by a caller compiled against 5.2 and handed over
            resolved = handed;
        } else if (bean instanceof Intercepted intercepted && !intercepted.$interceptorRegistrations().isEmpty()) {
            // A proxy generated before 5.3 retained the registrations it was constructed with; a newer proxy returns
            // none here, its interceptors being dependents of the bean like everyone else's.
            resolved = intercepted.$interceptorRegistrations();
        } else {
            // Resolved by binding as the bean's own: a non-singleton interceptor is the instance among the bean's
            // dependents, which the context carries while the bean is created and again while it is destroyed, or is
            // created there the first time.
            resolved = resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBindingValues(binding));
        }
        final InterceptorRegistry interceptorRegistry = beanContext.getBean(InterceptorRegistry.ARGUMENT);
        final Interceptor[] resolvedInterceptors = interceptorRegistry.resolveInterceptors((ExecutableMethod) interceptedMethod, (Collection) resolved, kind);
        if (ArrayUtils.isNotEmpty(resolvedInterceptors)) {
            final MethodInterceptorChain<T, T> chain = new MethodInterceptorChain<>(resolvedInterceptors, bean, interceptedMethod, kind);
            return Objects.requireNonNull(
                chain.proceed(),
                kind.name() + " interceptor chain illegal returned null for type: " + definition.getBeanType()
            );
        }
        return interceptedMethod.invoke(bean);
    }

    /**
     * Resolves the interceptors of the methods of a proxy that is the bean, for its constructor: the bean's own,
     * through the context creating it, selected for each method through the registry.
     *
     * @param resolutionContext The resolution context the bean is created in
     * @param methods           The intercepted methods, in the proxy's order
     * @param introduction      Whether the proxy introduces methods; an abstract one is then implemented by its
     *                          introduction interceptors and every other one is intercepted around
     * @return The interceptors, by method
     * @since 5.3.0
     */
    @Internal
    @UsedByGeneratedCode
    public static Interceptor<?, ?>[][] resolveInterceptors(BeanResolutionContext resolutionContext,
                                                            ExecutableMethod<?, ?>[] methods,
                                                            boolean introduction) {
        Interceptor<?, ?>[][] result = new Interceptor[methods.length][];
        if (methods.length == 0) {
            return result;
        }
        InterceptorRegistry registry = resolutionContext.getBean(InterceptorRegistry.ARGUMENT);
        // the hierarchy reverses the array it is given, so it gets a copy
        List<BeanRegistration<?>> registrations = new ArrayList<>(resolutionContext.getInterceptorRegistrations(
            Interceptor.ARGUMENT,
            Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(methods.clone()))
        ));
        for (int i = 0; i < methods.length; i++) {
            result[i] = select(registry, methods[i], registrations, introduction);
        }
        return result;
    }

    /**
     * The binding of the interceptors of the methods a proxy intercepts on a target: those of the methods of the target
     * that carry interceptor bindings. A binding may come from the method, from its class, or from the factory method
     * that produced the target, which gives the methods of the product no around stereotype of their own.
     *
     * @param target The definition of the target
     * @return The binding, or {@code null} when no method of the target is bound
     * @since 5.3.0
     */
    @Internal
    public static @Nullable Qualifier<Interceptor<?, ?>> targetBinding(BeanDefinition<?> target) {
        List<AnnotationMetadata> advised = new ArrayList<>();
        for (ExecutableMethod<?, ?> method : target.getExecutableMethods()) {
            if (method.hasAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)
                || !method.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING).isEmpty()) {
                advised.add(method);
            }
        }
        return advised.isEmpty() ? null : Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(advised.toArray(AnnotationMetadata[]::new)));
    }

    /**
     * Resolves the interceptors of one method of a proxy that fronts a separate target, for the target of the call.
     *
     * <p>The non-singleton interceptors of a target are the target's own: created with it as dependents of its
     * registration, and destroyed with it. The selection for the methods of the target is made once per target and
     * kept on its registration, so that it lives as long as the target does and every proxy fronting the target uses
     * it. An interceptor of a custom scope is obtained from its scope on every call. A target the context holds no
     * registration for, such as an object handed to {@code swap}, is intercepted with instances created once for its
     * definition and destroyed with nothing.</p>
     *
     * @param beanContext      The bean context
     * @param targetDefinition The definition of the target
     * @param target           The registration of the target, or {@code null} when the context holds none
     * @param method           The method, one of the target definition
     * @param introduction     Whether the proxy introduces methods
     * @return The interceptors
     * @since 5.3.0
     */
    @Internal
    @UsedByGeneratedCode
    public static Interceptor<?, ?>[] resolveTargetInterceptors(BeanContext beanContext,
                                                                BeanDefinition<?> targetDefinition,
                                                                @Nullable BeanRegistration<?> target,
                                                                ExecutableMethod<?, ?> method,
                                                                boolean introduction) {
        TargetSelection selection = selectionFor(beanContext, targetDefinition, target);
        if (selection.scoped().isEmpty()) {
            return selection.selected().computeIfAbsent(method, m -> select(selection.registry(), m, selection.registrations(), introduction));
        }
        // only the method called is selected again, with the instances its scopes hold now
        return select(selection.registry(), method, current(beanContext, selection, target), introduction);
    }

    /**
     * Resolves the interceptors of one method for the target of a call, found by its registration: for a proxy whose
     * target can change hands, such as a hot-swappable one. The registration the proxy holds is used when it is the
     * target's, and the context is asked for the registration of the target otherwise.
     *
     * @param beanContext      The bean context
     * @param targetDefinition The definition of the target
     * @param held             The registration the proxy holds, or {@code null}
     * @param target           The target of the call
     * @param method           The method, one of the target definition
     * @param introduction     Whether the proxy introduces methods
     * @return The interceptors
     * @since 5.3.0
     */
    @Internal
    @UsedByGeneratedCode
    public static Interceptor<?, ?>[] resolveTargetInterceptors(BeanContext beanContext,
                                                                BeanDefinition<?> targetDefinition,
                                                                @Nullable BeanRegistration<?> held,
                                                                @Nullable Object target,
                                                                ExecutableMethod<?, ?> method,
                                                                boolean introduction) {
        BeanRegistration<?> registration;
        if (held != null && held.getBean() == target) {
            registration = held;
        } else {
            registration = target == null ? null : beanContext.findBeanRegistration(target).orElse(null);
        }
        return resolveTargetInterceptors(beanContext, targetDefinition, registration, method, introduction);
    }

    private static TargetSelection selectionFor(BeanContext beanContext, BeanDefinition<?> targetDefinition, @Nullable BeanRegistration<?> target) {
        if (target == null || target.getBean() == null) {
            return unowned(beanContext, targetDefinition);
        }
        try {
            // the singletons, and for each non-singleton the instance the target owns, or one created for it that
            // joins its dependents
            return RegisteredBeanInterceptors.getState(target, TARGET_SELECTION, () -> {
                Qualifier<Interceptor<?, ?>> binding = targetBinding(targetDefinition);
                return selection(beanContext, binding == null ? List.of() : new ArrayList<>(RegisteredBeanInterceptors.getInterceptorRegistrations(target, Interceptor.ARGUMENT, binding)));
            });
        } catch (UnsupportedOperationException e) {
            // a registration a custom scope built by hand, which the context did not create and owns nothing through
            return unowned(beanContext, targetDefinition);
        }
    }

    /**
     * The selection for a target the context holds no registration for, which the context keeps by the definition of
     * the target: the instances belong to no target, and live as long as the context that created them.
     */
    private static TargetSelection unowned(BeanContext beanContext, BeanDefinition<?> targetDefinition) {
        return RegisteredBeanInterceptors.getUnownedState(beanContext, targetDefinition, () -> {
            Qualifier<Interceptor<?, ?>> binding = targetBinding(targetDefinition);
            return selection(beanContext, binding == null ? List.of() : new ArrayList<>(beanContext.getBeanRegistrations(Interceptor.ARGUMENT, binding)));
        });
    }

    private static TargetSelection selection(BeanContext beanContext, List<BeanRegistration<?>> registrations) {
        Set<BeanDefinition<?>> scoped = new HashSet<>(2);
        for (BeanRegistration<?> registration : registrations) {
            if (RegisteredBeanInterceptors.isScopedInterceptor(beanContext, registration.getBeanDefinition())) {
                scoped.add(registration.getBeanDefinition());
            }
        }
        return new TargetSelection(
            beanContext.getBean(InterceptorRegistry.ARGUMENT),
            registrations,
            new ConcurrentHashMap<>(),
            scoped.isEmpty() ? Set.of() : scoped
        );
    }

    /**
     * The registrations of a selection with the instance of each interceptor of a custom scope replaced by the one its
     * scope holds now: through the target, as its interceptor, or through the context for no target.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<BeanRegistration<?>> current(BeanContext beanContext, TargetSelection selection, @Nullable BeanRegistration<?> target) {
        List<BeanRegistration<?>> current = new ArrayList<>(selection.registrations().size());
        for (BeanRegistration<?> registration : selection.registrations()) {
            BeanDefinition definition = registration.getBeanDefinition();
            if (selection.scoped().contains(definition)) {
                current.add(target != null && target.getBean() != null
                    ? RegisteredBeanInterceptors.getInterceptorRegistration(target, definition)
                    : beanContext.getBeanRegistration(definition));
            } else {
                current.add(registration);
            }
        }
        return current;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Interceptor<?, ?>[] select(InterceptorRegistry registry,
                                              ExecutableMethod<?, ?> method,
                                              List<BeanRegistration<?>> registrations,
                                              boolean introduction) {
        return introduction && method.isAbstract()
            ? InterceptorChain.resolveIntroductionInterceptors(registry, (ExecutableMethod) method, (List) registrations)
            : InterceptorChain.resolveAroundInterceptors(registry, (ExecutableMethod) method, (List) registrations);
    }

    /**
     * The interceptors of the methods of one target.
     *
     * @param registry      The registry selecting the interceptors of a method
     * @param registrations The registrations of the interceptors bound to the methods
     * @param selected      The interceptors selected for each method so far
     * @param scoped        The interceptors of a custom scope among them, obtained for every call
     */
    private record TargetSelection(InterceptorRegistry registry,
                                   List<BeanRegistration<?>> registrations,
                                   Map<ExecutableMethod<?, ?>, Interceptor<?, ?>[]> selected,
                                   Set<BeanDefinition<?>> scoped) {
    }
}
