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
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanContextConfigurable;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.EnvironmentConfigurable;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.naming.Described;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Default implementation of the interceptor registry interface.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public final class DefaultInterceptorRegistry implements InterceptorRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(InterceptorChain.class);
    private static final MethodInterceptor<?, ?>[] ZERO_METHOD_INTERCEPTORS = new MethodInterceptor[0];
    private static final Interceptor[] ZERO_INTERCEPTORS = new Interceptor[0];
    private final BeanContext beanContext;

    public DefaultInterceptorRegistry(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    @NonNull
    public <T> Interceptor<T, ?>[] resolveInterceptors(
        @NonNull Executable<T, ?> method,
        @NonNull Collection<BeanRegistration<Interceptor<T, ?>>> interceptors,
        @NonNull InterceptorKind interceptorKind) {
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
                                                     boolean selectMethodInterceptor,
                                                     boolean selectConstructorInterceptor) {
        List<BeanRegistration<Interceptor<T, ?>>> selectedInterceptorRegistrations = new ArrayList<>(interceptors.size());
        for (BeanRegistration<Interceptor<T, ?>> beanRegistration : interceptors) {
            if (selectInterceptor(declaringType, interceptorKind, interceptPointBindings, beanRegistration)) {
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
        final Collection<AnnotationValue<?>> interceptorValues = AbstractInterceptorChain
            .resolveInterceptorValues(
                beanRegistration.getBeanDefinition().getAnnotationMetadata(), interceptorKind
            );
        if (interceptorValues.isEmpty()) {
            // Bean is an interceptor but no bindings???
            return false;
        }
        // loop through the bindings on the interceptor and make sure that
        // the intercept point has the same once
        for (AnnotationValue<?> interceptorAnnotationValue : interceptorValues) {
            if (!matches(interceptorAnnotationValue, interceptPointBindings)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(AnnotationValue<?> interceptorAnnotationValue, Collection<AnnotationValue<?>> interceptPointBindings) {
        final AnnotationValue<Annotation> memberBinding = interceptorAnnotationValue
            .getAnnotation(InterceptorBindingQualifier.META_BINDING_VALUES).orElse(null);
        final String annotationName = interceptorAnnotationValue.stringValue().orElse(null);
        if (annotationName == null) {
            // This shouldn't happen
            return false;
        }
        for (AnnotationValue<?> applicableValue : interceptPointBindings) {
            String interceptPointAnnotation = applicableValue.stringValue().orElse(null);
            if (!annotationName.equals(interceptPointAnnotation)) {
                continue;
            }
            if (memberBinding == null) {
                return true;
            }
            AnnotationValue<Annotation> otherMembers =
                applicableValue.getAnnotation(InterceptorBindingQualifier.META_BINDING_VALUES).orElse(null);
            if (!memberBinding.equals(otherMembers)) {
                continue;
            }
            return true;
        }
        return false;
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
    @NonNull
    public <T> Interceptor<T, T>[] resolveConstructorInterceptors(
        @NonNull BeanConstructor<T> constructor,
        @NonNull Collection<BeanRegistration<Interceptor<T, T>>> interceptors) {
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
}
