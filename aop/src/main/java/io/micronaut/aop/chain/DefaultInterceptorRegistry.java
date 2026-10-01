/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.aop.ConstructorInterceptor;
import io.micronaut.aop.Intercepted;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanContextConfigurable;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.EnvironmentConfigurable;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.naming.Described;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of the interceptor registry interface.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
@NullMarked
public final class DefaultInterceptorRegistry implements InterceptorRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(InterceptorChain.class);
    private static final MethodInterceptor<?, ?>[] ZERO_METHOD_INTERCEPTORS = new MethodInterceptor[0];
    private static final Interceptor[] ZERO_INTERCEPTORS = new Interceptor[0];
    private final BeanContext beanContext;

    public DefaultInterceptorRegistry(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public <T> Interceptor<T, ?>[] resolveInterceptors(
        Executable<T, ?> method,
        Collection<BeanRegistration<Interceptor<T, ?>>> interceptors,
        InterceptorKind interceptorKind) {
        final AnnotationMetadata annotationMetadata = method.getAnnotationMetadata();
        if (interceptors.isEmpty()) {
            return resolveToNone((ExecutableMethod<?, ?>) method, interceptorKind, annotationMetadata);
        }
        instrumentAnnotationMetadata(beanContext, method);
        final Collection<AnnotationValue<?>> applicableBindings
            = AbstractInterceptorChain.resolveInterceptorValues(
            annotationMetadata,
            interceptorKind
        );
        if (applicableBindings.isEmpty()) {
            return resolveToNone((ExecutableMethod<?, ?>) method, interceptorKind, annotationMetadata);
        }
        final Interceptor<T, ?>[] resolvedInterceptors = findInterceptors(
            method.getDeclaringType(),
            interceptors,
            interceptorKind,
            applicableBindings,
            annotationMetadata,
            true,
            false
        );
        if (LOG.isTraceEnabled()) {
            LOG.trace("Resolved {} {} interceptors out of a possible {} for method: {} - {}", resolvedInterceptors.length, interceptorKind, interceptors.size(), method.getDeclaringType(), method instanceof Described d ? d.getDescription(true) : method.toString());
            for (int i = 0; i < resolvedInterceptors.length; i++) {
                Interceptor<?, ?> resolvedInterceptor = resolvedInterceptors[i];
                LOG.trace("Interceptor {} - {}", i, resolvedInterceptor);
            }
        }
        return resolvedInterceptors;
    }

    @SuppressWarnings("rawtypes")
    private Interceptor[] resolveToNone(ExecutableMethod<?, ?> method,
                                        InterceptorKind interceptorKind,
                                        AnnotationMetadata annotationMetadata) {
        if (interceptorKind == InterceptorKind.INTRODUCTION) {
            if (annotationMetadata.hasStereotype(Adapter.class)) {
                return new MethodInterceptor[] {new AdapterIntroduction(beanContext, method)};
            } else {
                throw new IllegalStateException("At least one @Introduction method interceptor required, but missing for method: " + method.getDescription(true) + ". Check if your @Introduction stereotype annotation is marked with @Retention(RUNTIME) and @InterceptorBean(..) with the interceptor type. Otherwise do not load @Introduction beans if their interceptor definitions are missing!");

            }
        } else {
            return ZERO_METHOD_INTERCEPTORS;
        }
    }

    private <T> Interceptor<T, ?>[] findInterceptors(Class<?> declaringType,
                                                     Collection<BeanRegistration<Interceptor<T, ?>>> interceptors,
                                                     InterceptorKind interceptorKind,
                                                     Collection<AnnotationValue<?>> interceptPointBindings,
                                                     AnnotationMetadata interceptPointMetadata,
                                                     boolean selectMethodInterceptor,
                                                     boolean selectConstructorInterceptor) {
        List<BeanRegistration<Interceptor<T, ?>>> selectedInterceptorRegistrations = new ArrayList<>(interceptors.size());
        for (BeanRegistration<Interceptor<T, ?>> beanRegistration : interceptors) {
            if (selectInterceptor(declaringType, interceptorKind, interceptPointBindings, interceptPointMetadata, beanRegistration)) {
                selectedInterceptorRegistrations.add(beanRegistration);
            }
        }
        selectedInterceptorRegistrations.sort(OrderUtil.ORDERED_COMPARATOR);

        List<Interceptor<T, ?>> selectedInterceptors = new ArrayList<>(selectedInterceptorRegistrations.size());
        for (BeanRegistration<Interceptor<T, ?>> beanRegistration : selectedInterceptorRegistrations) {
            Interceptor<T, ?> bean = beanRegistration.getBean();
            if (selectMethodInterceptor && (bean instanceof MethodInterceptor || !(bean instanceof ConstructorInterceptor))
                || selectConstructorInterceptor && (bean instanceof ConstructorInterceptor || !(bean instanceof MethodInterceptor))) {
                selectedInterceptors.add(bean);
            }
        }
        return selectedInterceptors.toArray(ZERO_INTERCEPTORS);
    }

    private <T> boolean selectInterceptor(Class<?> declaringType,
                                          InterceptorKind interceptorKind,
                                          Collection<AnnotationValue<?>> interceptPointBindings,
                                          AnnotationMetadata interceptPointMetadata,
                                          BeanRegistration<Interceptor<T, ?>> beanRegistration) {
        final List<Argument<?>> typeArgs = beanRegistration.getBeanDefinition().getTypeArguments(ConstructorInterceptor.class);
        if (!typeArgs.isEmpty()) {
            final Class<?> applicableType = typeArgs.iterator().next().getType();
            if (!applicableType.isAssignableFrom(declaringType)) {
                return false;
            }
        }

        // does the annotation metadata contain @InterceptorBinding(interceptorType=SomeInterceptor.class)
        // this behaviour is in place for backwards compatible for the old @Type(SomeInterceptor.class) approach
        // In this case we don't care about any qualifiers
        for (AnnotationValue<?> applicableValue : interceptPointBindings) {
            if (isApplicableByType(beanRegistration, applicableValue)) {
                return true;
            }
        }
        // these are the binding declared on the interceptor itself
        // an interceptor can declare one or more bindings
        final AnnotationMetadata interceptorMetadata = beanRegistration.getBeanDefinition().getAnnotationMetadata();
        final Collection<AnnotationValue<?>> interceptorValues = AbstractInterceptorChain
            .resolveInterceptorValues(interceptorMetadata, interceptorKind);
        if (interceptorValues.isEmpty()) {
            // Bean is an interceptor but no bindings???
            return false;
        }
        // loop through the bindings on the interceptor and make sure that
        // the intercept point has the same once
        boolean hasInterceptorBinding = false;
        for (AnnotationValue<?> interceptorAnnotationValue : interceptorValues) {
            if (interceptorAnnotationValue.stringValue().isEmpty()) {
                continue;
            }
            hasInterceptorBinding = true;
            if (!matches(interceptorAnnotationValue, interceptorMetadata, interceptPointBindings, interceptPointMetadata)) {
                return false;
            }
        }
        return hasInterceptorBinding;
    }

    /**
     * Whether a binding declared on an interceptor applies to one of the bindings of an interception point: the
     * binding annotation is the same and, when the interceptor binds members, one of the occurrences it was declared
     * through is the same annotation as one of the occurrences at the interception point, with the occurrence
     * declared on the method replacing the one on its class.
     */
    private boolean matches(AnnotationValue<?> interceptorAnnotationValue,
                            AnnotationMetadata interceptorMetadata,
                            Collection<AnnotationValue<?>> interceptPointBindings,
                            AnnotationMetadata interceptPointMetadata) {
        final String annotationName = interceptorAnnotationValue.stringValue().orElse(null);
        if (annotationName == null) {
            // This shouldn't happen
            return false;
        }
        final List<AnnotationValue<?>> interceptorOccurrences =
            InterceptorBindingQualifier.resolveBoundOccurrences(interceptorAnnotationValue, interceptorMetadata);
        boolean boundByOccurrences = false;
        boolean boundByNoOccurrences = false;
        for (AnnotationValue<?> applicableValue : interceptPointBindings) {
            String interceptPointAnnotation = applicableValue.stringValue().orElse(null);
            if (!annotationName.equals(interceptPointAnnotation)) {
                continue;
            }
            if (interceptorOccurrences == null) {
                return true;
            }
            final List<AnnotationValue<?>> interceptPointOccurrences =
                InterceptorBindingQualifier.resolveBoundOccurrences(applicableValue, interceptPointMetadata, true);
            if (interceptPointOccurrences == null) {
                // an annotation carrying both @Around and @InterceptorBinding(bindMembers = true) records a
                // second binding which binds no members, and it must not decide the match while a binding
                // carrying the occurrences the intercept point binds by is present
                boundByNoOccurrences = true;
                continue;
            }
            boundByOccurrences = true;
            if (InterceptorBindingQualifier.anyMatch(interceptorOccurrences, interceptPointOccurrences)) {
                return true;
            }
        }
        // an intercept point binding no members at all is one compiled before the interceptor bound by them
        return boundByNoOccurrences && !boundByOccurrences;
    }

    private <T> boolean isApplicableByType(BeanRegistration<Interceptor<T, ?>> beanRegistration,
                                           AnnotationValue<?> applicableValue) {
        AnnotationClassValue<?> interceptorType = applicableValue.annotationClassValue(InterceptorBindingQualifier.META_MEMBER_INTERCEPTOR_TYPE).orElse(null);
        if (interceptorType == null) {
            return false;
        }
        Class<?> type = interceptorType.getType().orElse(null);
        if (type != null) {
            return type.isInstance(beanRegistration.getBean());
        }
        return interceptorType.getName().equals(beanRegistration.getBean().getClass().getName());
    }

    @Override
    public <T> Interceptor<T, T>[] resolveConstructorInterceptors(
        BeanConstructor<T> constructor,
        Collection<BeanRegistration<Interceptor<T, T>>> interceptors) {
        instrumentAnnotationMetadata(beanContext, constructor);
        final Collection<AnnotationValue<?>> applicableBindings
            = AbstractInterceptorChain.resolveInterceptorValues(
            constructor.getAnnotationMetadata(),
            InterceptorKind.AROUND_CONSTRUCT
        );
        final Interceptor<T, T>[] resolvedInterceptors = findInterceptors(
            constructor.getDeclaringBeanType(),
            (Collection) interceptors,
            InterceptorKind.AROUND_CONSTRUCT,
            applicableBindings,
            constructor.getAnnotationMetadata(),
            false,
            true
        );
        if (LOG.isTraceEnabled()) {
            LOG.trace("Resolved {} {} interceptors out of a possible {} for constructor: {} - {}", resolvedInterceptors.length, InterceptorKind.AROUND_CONSTRUCT, interceptors.size(), constructor.getDeclaringBeanType(), constructor.getDescription(true));
            for (int i = 0; i < resolvedInterceptors.length; i++) {
                Interceptor<?, ?> resolvedInterceptor = resolvedInterceptors[i];
                LOG.trace("Interceptor {} - {}", i, resolvedInterceptor);
            }
        }
        return resolvedInterceptors;
    }

    private static void instrumentAnnotationMetadata(BeanContext beanContext, Object method) {
        if (method instanceof BeanContextConfigurable ctxConfigurable) {
            ctxConfigurable.configure(beanContext);
        }
        if (beanContext instanceof ApplicationContext applicationContext && method instanceof EnvironmentConfigurable environmentConfigurable) {
            // ensure metadata is environment aware
            if (environmentConfigurable.hasPropertyExpressions()) {
                environmentConfigurable.configure(applicationContext.getEnvironment());
            }
        }
    }

    /**
     * Implements lifecycle invocation for the registry default method without requiring a separate service bean.
     * @param registry The registry selected by the bean context, including custom implementations
     * @param resolutionContext The lifecycle resolution context
     * @param definition The bean definition
     * @param interceptedMethod The lifecycle method
     * @param bean The bean instance
     * @param kind The lifecycle kind
     * @param shared Explicit candidates from an older generated caller, or {@code null}
     * @param <T1> The bean type
     * @return The lifecycle result
     * @since 5.3.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T1> @Nullable T1 interceptLifecycle(
        InterceptorRegistry registry,
        BeanResolutionContext resolutionContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> interceptedMethod,
        T1 bean,
        InterceptorKind kind,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> shared) {
        // Current generated definitions publish one complete candidate set during creation. The same set is
        // installed on the destruction context. Empty means resolved, not a request to discover candidates again.
        Collection<BeanRegistration<Interceptor<?, ?>>> resolved = shared != null && !shared.isEmpty()
            ? shared : (Collection) resolutionContext.getBeanInterceptors(definition);
        if (resolved == null) {
            resolved = resolveLifecycleInterceptorRegistrations(resolutionContext, interceptedMethod, bean, kind);
        }
        final Interceptor[] resolvedInterceptors = registry.resolveInterceptors(
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
     * Candidate discovery for older generated definitions and direct callers without retained lifecycle state.
     * @param resolutionContext The resolution context
     * @param method The lifecycle method
     * @param bean The bean instance
     * @param kind The lifecycle kind
     * @return The legacy interceptor candidates
     * @since 5.3.0
     */
    private static Collection<BeanRegistration<Interceptor<?, ?>>> resolveLifecycleInterceptorRegistrations(
        BeanResolutionContext resolutionContext,
        ExecutableMethod<?, ?> method,
        Object bean,
        InterceptorKind kind) {
        if (bean instanceof Intercepted intercepted && !intercepted.$interceptorRegistrations().isEmpty()) {
            return intercepted.$interceptorRegistrations();
        }
        Collection<AnnotationValue<?>> binding = AbstractInterceptorChain.resolveInterceptorValues(method.getAnnotationMetadata(), kind);
        return kind == InterceptorKind.PRE_DESTROY
            ? resolveLifecycleInterceptors(resolutionContext, binding)
            : resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, Qualifiers.byInterceptorBindingValues(binding));
    }

    /**
     * Resolves the interceptor candidates for pre-destroy interception.
     *
     * <p>The interceptors the container hands over when the bean was constructed with some are the candidate set.
     * Otherwise the interceptor instances among the dependents of the bean are, together with the singletons bound to
     * the event, and when the bean has none the interceptors are resolved by binding as the bean's own.</p>
     *
     * @param resolutionContext The resolution context
     * @param binding           The binding of the interception point
     * @return The interceptor registrations to select from
     * @since 5.2.0
     */
    @SuppressWarnings("unchecked")
    private static Collection<BeanRegistration<Interceptor<?, ?>>> resolveLifecycleInterceptors(
        BeanResolutionContext resolutionContext,
        Collection<AnnotationValue<?>> binding) {

        Object attribute = resolutionContext.getAttribute(BeanResolutionContext.EXISTING_INTERCEPTOR_REGISTRATIONS);
        if (attribute instanceof List<?> existing) {
            return (List<BeanRegistration<Interceptor<?, ?>>>) existing;
        }
        Qualifier<Interceptor<?, ?>> qualifier = Qualifiers.byInterceptorBindingValues(binding);
        List<BeanRegistration<Interceptor<?, ?>>> existing = findExistingInterceptors(resolutionContext);
        if (existing.isEmpty()) {
            // resolved as the bean's own, which finds those created for an earlier interception point of the bean
            return resolutionContext.getInterceptorRegistrations(Interceptor.ARGUMENT, qualifier);
        }
        // The interceptor instances among the dependents of the bean, such as those a proxy retained and handed to
        // the target it destroys, are the candidates, as before. A singleton bound to the event is no dependent of
        // the bean, so it is added.
        List<BeanRegistration<Interceptor<?, ?>>> candidates = new ArrayList<>(existing);
        BeanContext beanContext = resolutionContext.getContext();
        for (BeanDefinition<Interceptor<?, ?>> definition : beanContext.getBeanDefinitions(Interceptor.ARGUMENT, qualifier)) {
            if (definition.isSingleton()) {
                candidates.add(beanContext.getBeanRegistration(definition));
            }
        }
        return candidates;
    }

    /**
     * Finds interceptor registrations already associated with a legacy disposal path. New bean registrations carry
     * the exact selected set through {@link BeanResolutionContext#EXISTING_INTERCEPTOR_REGISTRATIONS}; this fallback
     * remains for generated factory definitions that cannot transfer that set during construction.
     *
     * @param resolutionContext The resolution context
     * @return Existing interceptor registrations
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
        return interceptors == null ? Collections.emptyList() : interceptors;
    }
}
