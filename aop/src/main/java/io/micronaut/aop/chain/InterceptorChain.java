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

import io.micronaut.aop.Adapter;
import io.micronaut.aop.Around;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.InvocationContext;
import io.micronaut.aop.exceptions.UnimplementedAdviceException;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.RegisteredBeanInterceptors;
import io.micronaut.context.EnvironmentConfigurable;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.EvaluatedAnnotationMetadata;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An internal representation of the {@link Interceptor} chain. This class implements {@link InvocationContext} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 * @param <B> The declaring type
 * @param <R> The result of the method call
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class InterceptorChain<B, R> extends AbstractInterceptorChain<B, R> implements InvocationContext<B, R> {

    // the key of the selection of interceptors kept on the registration of a target
    private static final Object TARGET_SELECTION = new Object();
    // the selection for a target the context holds no registration for, by the definition of the target
    private static final Map<BeanDefinition<?>, TargetSelection> UNOWNED = new WeakHashMap<>();

    protected final B target;
    protected final ExecutableMethod<B, R> executionHandle;
    private final AnnotationMetadata annotationMetadata;

    /**
     * Constructor.
     *
     * @param interceptors array of interceptors
     * @param target target type
     * @param method result method
     * @param originalParameters parameters
     */
    public InterceptorChain(Interceptor<B, R>[] interceptors,
                            B target,
                            ExecutableMethod<B, R> method,
                            @Nullable Object... originalParameters) {
        super(interceptors, originalParameters);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Intercepted method [{}] invocation on target: {}", method, target);
        }
        this.target = target;
        this.executionHandle = method;
        AnnotationMetadata metadata = executionHandle.getAnnotationMetadata();
        if (metadata instanceof EvaluatedAnnotationMetadata eam) {
            this.annotationMetadata = eam.withArguments(target, originalParameters);
        } else {
            this.annotationMetadata = metadata;
        }
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public Argument<?>[] getArguments() {
        return executionHandle.getArguments();
    }

    @Override
    @Nullable
    public R invoke(B instance, @Nullable Object... arguments) {
        return proceed();
    }

    @Override
    public B getTarget() {
        return target;
    }

    @Override
    @Nullable
    public R proceed() throws RuntimeException {
        Interceptor<B, R> interceptor;
        if (interceptorCount == 0 || index == interceptorCount) {
            try {
                return executionHandle.invoke(target, getParameterValues());
            } catch (AbstractMethodError e) {
                throw new UnimplementedAdviceException(executionHandle);
            }
        } else {
            interceptor = this.interceptors[index++];
            if (LOG.isTraceEnabled()) {
                LOG.trace("Proceeded to next interceptor [{}] in chain for method invocation: {}", interceptor, executionHandle);
            }

            return interceptor.intercept(this);
        }
    }

    /**
     * Resolves the {@link Around} interceptors for a method.
     *
     * @param beanContext bean context passed in
     * @param method The method
     * @param interceptors The array of interceptors
     * @param <T> The intercepted type
     * @return The filtered array of interceptors
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static <T> Interceptor<T, ?>[] resolveAroundInterceptors(BeanContext beanContext,
                                                                    ExecutableMethod<T, ?> method,
                                                                    List<BeanRegistration<Interceptor<T, ?>>> interceptors) {
        return resolveInterceptors(beanContext, method, interceptors, InterceptorKind.AROUND);
    }

    /**
     * Resolves the {@link Around} interceptors for a method.
     *
     * @param interceptorRegistry the interceptor registry
     * @param method The method
     * @param interceptors The array of interceptors
     * @param <T> The intercepted type
     * @return The filtered array of interceptors
     * @since 4.3.0
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static <T> Interceptor<T, ?>[] resolveAroundInterceptors(InterceptorRegistry interceptorRegistry,
                                                                    ExecutableMethod<T, ?> method,
                                                                    List<BeanRegistration<Interceptor<T, ?>>> interceptors) {
        return resolveInterceptors(interceptorRegistry, method, interceptors, InterceptorKind.AROUND);
    }

    /**
     * Resolves the {@link Introduction} interceptors for a method.
     *
     * @param beanContext bean context passed in
     * @param method The method
     * @param interceptors The array of interceptors
     * @param <T> The intercepted type
     * @return The filtered array of interceptors
     * @since 4.3.0
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static <T> Interceptor<T, ?>[] resolveIntroductionInterceptors(BeanContext beanContext,
                                                                          ExecutableMethod<T, ?> method,
                                                                          List<BeanRegistration<Interceptor<T, ?>>> interceptors) {
        final Interceptor<T, ?>[] introductionInterceptors = resolveInterceptors(beanContext, method, interceptors, InterceptorKind.INTRODUCTION);
        final Interceptor<T, ?>[] aroundInterceptors = resolveInterceptors(beanContext, method, interceptors, InterceptorKind.AROUND);
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }

    /**
     * Resolves the {@link Introduction} interceptors for a method.
     *
     * @param interceptorRegistry the interceptor registry
     * @param method The method
     * @param interceptors The array of interceptors
     * @param <T> The intercepted type
     * @return The filtered array of interceptors
     * @since 4.3.0
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static <T> Interceptor<T, ?>[] resolveIntroductionInterceptors(InterceptorRegistry interceptorRegistry,
                                                                          ExecutableMethod<T, ?> method,
                                                                          List<BeanRegistration<Interceptor<T, ?>>> interceptors) {
        final Interceptor<T, ?>[] introductionInterceptors = resolveInterceptors(interceptorRegistry, method, interceptors, InterceptorKind.INTRODUCTION);
        final Interceptor<T, ?>[] aroundInterceptors = resolveInterceptors(interceptorRegistry, method, interceptors, InterceptorKind.AROUND);
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }

    /**
     * Resolves the {@link Around} interceptors for a method.
     *
     * @param beanContext bean context passed in
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     * @deprecated Replaced by {@link #resolveAroundInterceptors(BeanContext, ExecutableMethod, List)}
     */
    // IMPLEMENTATION NOTE: This method is deprecated but should not be removed as it would break binary compatibility
    @SuppressWarnings({"WeakerAccess", "rawtypes"})
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    public static Interceptor[] resolveAroundInterceptors(@Nullable BeanContext beanContext, ExecutableMethod<?, ?> method, Interceptor... interceptors) {
        instrumentAnnotationMetadata(beanContext, method);
        return resolveInterceptorsInternal(method, Around.class, interceptors, beanContext != null ? beanContext.getClassLoader() : InterceptorChain.class.getClassLoader());
    }

    /**
     * Resolves the interceptors for a method for {@link Introduction} advise. For {@link Introduction} advise
     * any {@link Around} advise interceptors are applied first
     *
     * @param beanContext Bean Context
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     */
    // IMPLEMENTATION NOTE: This method is deprecated but should not be removed as it would break binary compatibility
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    public static Interceptor[] resolveIntroductionInterceptors(@Nullable BeanContext beanContext,
                                                                ExecutableMethod<?, ?> method,
                                                                Interceptor... interceptors) {
        instrumentAnnotationMetadata(beanContext, method);
        Interceptor[] introductionInterceptors = resolveInterceptorsInternal(method, Introduction.class, interceptors, beanContext != null ? beanContext.getClassLoader() : InterceptorChain.class.getClassLoader());
        if (introductionInterceptors.length == 0) {
            if (method.hasStereotype(Adapter.class)) {
                Objects.requireNonNull(beanContext);
                introductionInterceptors = new Interceptor[] {new AdapterIntroduction(beanContext, method)};
            } else {
                throw new IllegalStateException("At least one @Introduction method interceptor required, but missing. Check if your @Introduction stereotype annotation is marked with @Retention(RUNTIME) and @Type(..) with the interceptor type. Otherwise do not load @Introduction beans if their interceptor definitions are missing!");

            }
        }
        Interceptor[] aroundInterceptors = resolveAroundInterceptors(beanContext, method, interceptors);
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }

    private static <T> Interceptor<T, ?>[] resolveInterceptors(BeanContext beanContext,
                                                               ExecutableMethod<T, ?> method,
                                                               List<BeanRegistration<Interceptor<T, ?>>> interceptors,
                                                               InterceptorKind interceptorKind) {
        return resolveInterceptors(beanContext.getBean(InterceptorRegistry.class), method, interceptors, interceptorKind);
    }

    private static <T> Interceptor<T, ?>[] resolveInterceptors(InterceptorRegistry interceptorRegistry,
                                                               ExecutableMethod<T, ?> method,
                                                               List<BeanRegistration<Interceptor<T, ?>>> interceptors,
                                                               InterceptorKind interceptorKind) {
        return interceptorRegistry.resolveInterceptors(
            method,
            interceptors,
            interceptorKind
        );
    }

    private static void instrumentAnnotationMetadata(@Nullable BeanContext beanContext, ExecutableMethod<?, ?> method) {
        if (beanContext instanceof ApplicationContext context && method instanceof EnvironmentConfigurable m) {
            if (m.hasPropertyExpressions()) {
                m.configure(context.getEnvironment());
            }
        }
    }

    private static <T> Interceptor<T, ?>[] resolveInterceptorsInternal(ExecutableMethod<?, ?> method,
                                                                       Class<? extends Annotation> annotationType,
                                                                       Interceptor<T, ?>[] interceptors,
 ClassLoader classLoader) {
        List<Class<? extends Annotation>> annotations = method.getAnnotationTypesByStereotype(annotationType, classLoader);

        Set<Class<?>> applicableClasses = new HashSet<>();

        for (Class<? extends Annotation> aClass : annotations) {
            if (annotationType == Around.class && aClass.getAnnotation(Around.class) == null && aClass.getAnnotation(Introduction.class) != null) {
                continue;
            } else if (annotationType == Introduction.class && aClass.getAnnotation(Introduction.class) == null && aClass.getAnnotation(Around.class) != null) {
                continue;
            }
            Type typeAnn = aClass.getAnnotation(Type.class);
            if (typeAnn != null) {
                applicableClasses.addAll(Arrays.asList(typeAnn.value()));
            }
        }

        Interceptor<T, ?>[] interceptorArray = Arrays.stream(interceptors)
            .filter(i -> applicableClasses.stream().anyMatch(t -> t.isInstance(i)))
            .toArray(Interceptor[]::new);
        OrderUtil.sort(interceptorArray);
        return interceptorArray;
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

    private static TargetSelection unowned(BeanContext beanContext, BeanDefinition<?> targetDefinition) {
        synchronized (UNOWNED) {
            TargetSelection selection = UNOWNED.get(targetDefinition);
            if (selection == null) {
                Qualifier<Interceptor<?, ?>> binding = targetBinding(targetDefinition);
                selection = selection(beanContext, binding == null ? List.of() : new ArrayList<>(beanContext.getBeanRegistrations(Interceptor.ARGUMENT, binding)));
                UNOWNED.put(targetDefinition, selection);
            }
            return selection;
        }
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
            ? resolveIntroductionInterceptors(registry, (ExecutableMethod) method, (List) registrations)
            : resolveAroundInterceptors(registry, (ExecutableMethod) method, (List) registrations);
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
