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

import io.micronaut.aop.*;
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
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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
            if (interceptorKind == InterceptorKind.INTRODUCTION) {
                if (annotationMetadata.hasStereotype(Adapter.class)) {
                    return new MethodInterceptor[] { new AdapterIntroduction(beanContext, (ExecutableMethod<?, ?>) method) };
                } else {
                    throw new IllegalStateException("At least one @Introduction method interceptor required, but missing. Check if your @Introduction stereotype annotation is marked with @Retention(RUNTIME) and @Type(..) with the interceptor type. Otherwise do not load @Introduction beans if their interceptor definitions are missing!");

                }
            } else {
                //noinspection unchecked
                return (Interceptor<T, ?>[]) ZERO_METHOD_INTERCEPTORS;
            }
        } else {
            instrumentAnnotationMetadata(beanContext, method);
            final List<AnnotationValue<InterceptorBinding>> applicableBindings
                    = annotationMetadata.getAnnotationValuesByType(InterceptorBinding.class)
                    .stream()
                    .filter(ann ->
                            ann.enumValue("kind", InterceptorKind.class)
                                    .orElse(InterceptorKind.AROUND) == interceptorKind)
                    .collect(Collectors.toList());
            final Interceptor[] resolvedInterceptors = interceptors.stream()
                    .filter(beanRegistration -> {
                        final List<Argument<?>> typeArgs = beanRegistration.getBeanDefinition().getTypeArguments(ConstructorInterceptor.class);
                        if (typeArgs.isEmpty()) {
                            return true;
                        } else {
                            final Class<?> applicableType = typeArgs.iterator().next().getType();
                            return applicableType.isAssignableFrom(method.getDeclaringType());
                        }
                    })
                    .filter(beanRegistration -> applicableBindings.stream().anyMatch(annotationValue -> {
                        // does the annotation metadata contain @InterceptorBinding(interceptorType=SomeInterceptor.class)
                        // that matches the list of interceptors ?
                        final boolean isApplicableByType = annotationValue.classValue("interceptorType")
                                .map(t -> t.isInstance(beanRegistration.getBean())).orElse(false);

                        // does the annotation metadata of the interceptor definition contain
                        // @InterceptorBinding(SomeAnnotation.class) ?
                        final boolean isApplicationByBinding = annotationValue.stringValue()
                                .map(annotationName -> InterceptorBindingQualifier
                                        .resolveInterceptorValues(beanRegistration.getBeanDefinition().getAnnotationMetadata(), interceptorKind.name())
                                        .contains(annotationName)
                                )
                                .orElse(false);
                        return isApplicableByType || isApplicationByBinding;
                    })).sorted(OrderUtil.COMPARATOR)
                    .map(BeanRegistration::getBean)
                    .filter(bean -> (bean instanceof MethodInterceptor) || !(bean instanceof ConstructorInterceptor))
                    .toArray(Interceptor[]::new);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Resolved {} {} interceptors out of a possible {} for method: {} - {}", resolvedInterceptors.length, interceptorKind, interceptors.size(), method.getDeclaringType(), method instanceof Described ? ((Described) method).getDescription(true) : method.toString());
                for (int i = 0; i < resolvedInterceptors.length; i++) {
                    Interceptor resolvedInterceptor = resolvedInterceptors[i];
                    LOG.trace("Interceptor {} - {}", i, resolvedInterceptor);
                }
            }
            //noinspection unchecked
            return resolvedInterceptors;
        }
    }

    @Override
    @NonNull
    public <T> Interceptor<T, T>[]  resolveConstructorInterceptors(
            @NonNull BeanConstructor<T> constructor,
            @NonNull Collection<BeanRegistration<Interceptor<T, T>>> interceptors) {
        instrumentAnnotationMetadata(beanContext, constructor);
        final List<AnnotationValue<InterceptorBinding>> applicableBindings
                = constructor.getAnnotationMetadata().getAnnotationValuesByType(InterceptorBinding.class)
                .stream()
                .filter(ann ->
                        ann.enumValue("kind", InterceptorKind.class)
                                .orElse(InterceptorKind.AROUND) == InterceptorKind.AROUND_CONSTRUCT)
                .collect(Collectors.toList());
        final Interceptor[] resolvedInterceptors = interceptors.stream()
                .filter(beanRegistration -> {
                    final List<Argument<?>> typeArgs = beanRegistration.getBeanDefinition().getTypeArguments(ConstructorInterceptor.class);
                    if (typeArgs.isEmpty()) {
                        return true;
                    } else {
                        final Class<?> applicableType = typeArgs.iterator().next().getType();
                        return applicableType.isAssignableFrom(constructor.getDeclaringBeanType());
                    }
                })
                .filter(beanRegistration -> applicableBindings.stream().anyMatch(annotationValue -> {
                    // does the annotation metadata contain @InterceptorBinding(interceptorType=SomeInterceptor.class)
                    // that matches the list of interceptors ?
                    final boolean isApplicableByType = annotationValue.classValue("interceptorType")
                            .map(t -> t.isInstance(beanRegistration.getBean())).orElse(false);

                    // does the annotation metadata of the interceptor definition contain
                    // @InterceptorBinding(SomeAnnotation.class) ?
                    final boolean isApplicationByBinding = annotationValue.stringValue()
                            .map(annotationName -> InterceptorBindingQualifier.resolveInterceptorValues(beanRegistration
                                    .getBeanDefinition()
                                    .getAnnotationMetadata())
                                    .contains(annotationName))
                            .orElse(false);
                    return isApplicableByType || isApplicationByBinding;
                })).sorted(OrderUtil.COMPARATOR)
                .map(BeanRegistration::getBean)
                .filter(bean -> (bean instanceof ConstructorInterceptor) || !(bean instanceof MethodInterceptor))
                .toArray(Interceptor[]::new);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Resolved {} {} interceptors out of a possible {} for constructor: {} - {}", resolvedInterceptors.length, InterceptorKind.AROUND_CONSTRUCT, interceptors.size(), constructor.getDeclaringBeanType(), constructor.getDescription(true));
            for (int i = 0; i < resolvedInterceptors.length; i++) {
                Interceptor resolvedInterceptor = resolvedInterceptors[i];
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
