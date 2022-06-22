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

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import io.micronaut.aop.Adapter;
import io.micronaut.aop.ConstructorInterceptor;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.EnvironmentConfigurable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.naming.Described;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.ExecutableMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.inject.qualifiers.InterceptorBindingQualifier.META_MEMBER_MEMBERS;

/**
 * Default implementation of the interceptor registry interface.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public class DefaultInterceptorRegistry implements InterceptorRegistry {
    protected static final Logger LOG = LoggerFactory.getLogger(InterceptorChain.class);
    private static final MethodInterceptor<?, ?>[] ZERO_METHOD_INTERCEPTORS = new MethodInterceptor[0];
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
        } else {
            instrumentAnnotationMetadata(beanContext, method);
            final Collection<AnnotationValue<?>> applicableBindings
                    = AbstractInterceptorChain.resolveInterceptorValues(
                    annotationMetadata,
                    interceptorKind
            );
            if (applicableBindings.isEmpty()) {
                return resolveToNone((ExecutableMethod<?, ?>) method, interceptorKind, annotationMetadata);
            } else {
                @SuppressWarnings({"unchecked",
                        "rawtypes"}) final Interceptor[] resolvedInterceptors =
                        (Interceptor[]) interceptorStream(
                                method.getDeclaringType(),
                                (Collection) interceptors,
                                interceptorKind,
                                applicableBindings
                        ).filter(bean -> (bean instanceof MethodInterceptor) || !(bean instanceof ConstructorInterceptor))
                                .toArray(Interceptor[]::new);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Resolved {} {} interceptors out of a possible {} for method: {} - {}", resolvedInterceptors.length, interceptorKind, interceptors.size(), method.getDeclaringType(), method instanceof Described ? ((Described) method).getDescription(true) : method.toString());
                    for (int i = 0; i < resolvedInterceptors.length; i++) {
                        Interceptor<?, ?> resolvedInterceptor = resolvedInterceptors[i];
                        LOG.trace("Interceptor {} - {}", i, resolvedInterceptor);
                    }
                }
                //noinspection unchecked
                return resolvedInterceptors;
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private Interceptor[] resolveToNone(ExecutableMethod<?, ?> method,
                                        InterceptorKind interceptorKind,
                                        AnnotationMetadata annotationMetadata) {
        if (interceptorKind == InterceptorKind.INTRODUCTION) {
            if (annotationMetadata.hasStereotype(Adapter.class)) {
                return new MethodInterceptor[] { new AdapterIntroduction(beanContext, method) };
            } else {
                throw new IllegalStateException("At least one @Introduction method interceptor required, but missing for method: " + method.getDescription(true) + ". Check if your @Introduction stereotype annotation is marked with @Retention(RUNTIME) and @InterceptorBean(..) with the interceptor type. Otherwise do not load @Introduction beans if their interceptor definitions are missing!");

            }
        } else {
            return ZERO_METHOD_INTERCEPTORS;
        }
    }

    private <T, R> Stream<? extends Interceptor<T, R>> interceptorStream(Class<?> declaringType,
                                                                         Collection<BeanRegistration<Interceptor<T, R>>> interceptors,
                                                                         InterceptorKind interceptorKind,
                                                                         Collection<AnnotationValue<?>> applicableBindings) {
        return interceptors.stream()
                .filter(beanRegistration -> {
                    final List<Argument<?>> typeArgs = beanRegistration.getBeanDefinition().getTypeArguments(ConstructorInterceptor.class);
                    if (typeArgs.isEmpty()) {
                        return true;
                    } else {
                        final Class<?> applicableType = typeArgs.iterator().next().getType();
                        return applicableType.isAssignableFrom(declaringType);
                    }
                })
                .filter(beanRegistration -> {
                    // these are the binding declared on the interceptor itself
                    // an interceptor can declare one or more bindings
                    final Collection<AnnotationValue<?>> interceptorValues = AbstractInterceptorChain
                            .resolveInterceptorValues(
                                    beanRegistration.getBeanDefinition().getAnnotationMetadata(), interceptorKind
                            );
                    // if there are no binding try simply match by interceptor type
                    if (interceptorValues.isEmpty()) {
                        // does the annotation metadata contain @InterceptorBinding(interceptorType=SomeInterceptor.class)
                        // that matches the list of interceptors ?
                        // this behaviour is in place for backwards compatible for the old @Type(SomeInterceptor.class) approach
                        for (AnnotationValue<?> applicableValue : applicableBindings) {
                            if (isApplicableByType(beanRegistration, applicableValue)) {
                                return true;
                            }
                        }
                        return false;
                    } else {
                        if (interceptorValues.size() == 1) {
                            // single interceptor binding, fast path
                            final AnnotationValue<?> interceptorBinding = interceptorValues.iterator().next();
                            final AnnotationValue<Annotation> memberBinding = interceptorBinding
                                    .getAnnotation(META_MEMBER_MEMBERS).orElse(null);
                            final String annotationName = interceptorBinding.stringValue().orElse(null);
                            if (annotationName != null) {
                                // any match
                                for (AnnotationValue<?> applicableBinding : applicableBindings) {
                                    if (isApplicableByType(beanRegistration,
                                                           applicableBinding)) {
                                        return true;
                                    } else if (annotationName.equals(applicableBinding.stringValue().orElse(null))) {
                                        if (memberBinding != null) {
                                            final AnnotationValue<Annotation> otherMembers =
                                                    applicableBinding.getAnnotation(META_MEMBER_MEMBERS).orElse(null);
                                            if (memberBinding.equals(otherMembers)) {
                                                return true;
                                            }
                                        } else {
                                           return true;
                                        }
                                    }
                                }
                            }
                            return false;
                        } else {
                            // multiple binding case.
                            boolean isApplicationByBinding = true;
                            // loop through the bindings on the interceptor and make sure that
                            // the binding on the injection point matches up
                            for (AnnotationValue<?> annotationValue : applicableBindings) {
                                final AnnotationValue<Annotation> memberBinding = annotationValue
                                        .getAnnotation(META_MEMBER_MEMBERS).orElse(null);
                                final String annotationName = annotationValue.stringValue().orElse(null);

                                if (annotationName != null) {
                                    boolean interceptorApplicable = true;
                                    for (AnnotationValue<?> applicableValue : interceptorValues) {
                                        if (isApplicableByType(beanRegistration,
                                                               applicableValue)) {
                                            return true;
                                        }

                                        // does the annotation metadata of the interceptor definition contain
                                        // @InterceptorBinding(SomeAnnotation.class) ?
                                        if (annotationName.equals(applicableValue.stringValue().orElse(null))) {
                                            // if it does do we need to bind the members
                                            if (memberBinding != null) {
                                                final AnnotationValue<Annotation> otherMembers =
                                                        applicableValue.getAnnotation(META_MEMBER_MEMBERS).orElse(null);
                                                interceptorApplicable = memberBinding.equals(otherMembers);
                                                if (interceptorApplicable) {
                                                    break;
                                                }
                                            } else {
                                                interceptorApplicable = true;
                                                break;
                                            }
                                        } else {
                                            interceptorApplicable = false;
                                        }
                                    }
                                    isApplicationByBinding = interceptorApplicable;
                                    if (!isApplicationByBinding) {
                                        break;
                                    }
                                }
                            }
                            return isApplicationByBinding;
                        }

                    }
                }).sorted(OrderUtil.COMPARATOR)
                .map(BeanRegistration::getBean);
    }

    private <T, R> boolean isApplicableByType(BeanRegistration<Interceptor<T, R>> beanRegistration,
                                              AnnotationValue<?> applicableValue) {
        return applicableValue.classValue("interceptorType")
                .map(t -> t.isInstance(beanRegistration.getBean())).orElse(false);
    }

    @Override
    @NonNull
    public <T> Interceptor<T, T>[]  resolveConstructorInterceptors(
            @NonNull BeanConstructor<T> constructor,
            @NonNull Collection<BeanRegistration<Interceptor<T, T>>> interceptors) {
        instrumentAnnotationMetadata(beanContext, constructor);
        final Collection<AnnotationValue<?>> applicableBindings
                = AbstractInterceptorChain.resolveInterceptorValues(
                constructor.getAnnotationMetadata(),
                InterceptorKind.AROUND_CONSTRUCT
        );
        final Interceptor[] resolvedInterceptors = interceptorStream(
                constructor.getDeclaringBeanType(),
                interceptors,
                InterceptorKind.AROUND_CONSTRUCT,
                applicableBindings
        )
                .filter(bean -> (bean instanceof ConstructorInterceptor) || !(bean instanceof MethodInterceptor))
                .toArray(Interceptor[]::new);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Resolved {} {} interceptors out of a possible {} for constructor: {} - {}", resolvedInterceptors.length, InterceptorKind.AROUND_CONSTRUCT, interceptors.size(), constructor.getDeclaringBeanType(), constructor.getDescription(true));
            for (int i = 0; i < resolvedInterceptors.length; i++) {
                Interceptor<?, ?> resolvedInterceptor = resolvedInterceptors[i];
                LOG.trace("Interceptor {} - {}", i, resolvedInterceptor);
            }
        }
        //noinspection unchecked
        return resolvedInterceptors;
    }

    private static void instrumentAnnotationMetadata(BeanContext beanContext, Object method) {
        if (beanContext instanceof ApplicationContext && method instanceof EnvironmentConfigurable) {
            // ensure metadata is environment aware
            final EnvironmentConfigurable m = (EnvironmentConfigurable) method;
            if (m.hasPropertyExpressions()) {
                m.configure(((ApplicationContext) beanContext).getEnvironment());
            }
        }
    }
}
