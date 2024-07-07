/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject.annotation.internal;

import io.micronaut.aop.Around;
import io.micronaut.aop.AroundConstruct;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.AnnotationRemapper;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The remapped for various interceptor annotation stereotypes.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class InterceptorBindingMembers implements AnnotationRemapper {

    private static final Set<String> SKIP_ANNOTATIONS = Set.of(
        Around.class.getName(), AroundConstruct.class.getName(), InterceptorBinding.class.getName(), Introduction.class.getName()
    );

    @NonNull
    @Override
    public String getPackageName() {
        return ALL_PACKAGES;
    }

    @NonNull
    @Override
    public List<AnnotationValue<?>> remap(AnnotationValue<?> annotationValue, VisitorContext visitorContext) {
        if (annotationValue.getStereotypes() == null) {
            return List.of(annotationValue);
        }
        String annotationName = annotationValue.getAnnotationName();
        if (SKIP_ANNOTATIONS.contains(annotationName)) {
            return List.of(annotationValue.mutate().replaceStereotypes(Collections.emptyList()).build());
        }

        var interceptorBindings = new ArrayList<AnnotationValueBuilder<?>>();
        for (AnnotationValue<?> stereotype : annotationValue.getStereotypes()) {
            String stereotypeName = stereotype.getAnnotationName();
            AnnotationValueBuilder<?> newInterceptorBinding = null;
            if (InterceptorBinding.class.getName().equals(stereotypeName)) {
                newInterceptorBinding = stereotype.mutate()
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(annotationName));

                if (stereotype.booleanValue(InterceptorBinding.META_BIND_MEMBERS).orElse(false)) {
                    String[] nonBinding = annotationValue.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE);
                    Map<CharSequence, Object> bindingValues = annotationValue.getValues();
                    bindingValues = new LinkedHashMap<>(bindingValues);
                    Arrays.asList(nonBinding).forEach(bindingValues.keySet()::remove);

                    AnnotationValue<Annotation> binding = AnnotationValue.builder(annotationValue.getAnnotationName())
                        .members(bindingValues)
                        .build();
                    newInterceptorBinding.member(InterceptorBindingQualifier.META_BINDING_VALUES, binding);
                }
            } else if (Around.class.getName().equals(stereotypeName)) {
                newInterceptorBinding = AnnotationValue.builder(InterceptorBinding.class)
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(annotationName))
                    .member("kind", InterceptorKind.AROUND);
            } else if (Introduction.class.getName().equals(stereotypeName)) {
                newInterceptorBinding = AnnotationValue.builder(InterceptorBinding.class)
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(annotationName))
                    .member("kind", InterceptorKind.INTRODUCTION);
            } else if (AroundConstruct.class.getName().equals(stereotypeName)) {
                newInterceptorBinding = AnnotationValue.builder(InterceptorBinding.class)
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(annotationName))
                    .member("kind", InterceptorKind.AROUND_CONSTRUCT);
            }
            if (newInterceptorBinding != null) {
                interceptorBindings.add(newInterceptorBinding);
            }
        }
        final boolean hasInterceptorBinding = !interceptorBindings.isEmpty();
        if (hasInterceptorBinding) {
            for (AnnotationValue<?> av : annotationValue.getStereotypes()) {
                if (Type.class.getName().equals(av.getAnnotationName())) {
                    final Object o = av.getValues().get(AnnotationMetadata.VALUE_MEMBER);
                    AnnotationClassValue<?> interceptorType = null;
                    if (o instanceof AnnotationClassValue<?> annotationClassValue) {
                        interceptorType = annotationClassValue;
                    } else if (o instanceof AnnotationClassValue<?>[] annotationClassValues) {
                        if (annotationClassValues.length > 0) {
                            interceptorType = annotationClassValues[0];
                        }
                    }
                    if (interceptorType != null) {
                        for (AnnotationValueBuilder<?> interceptorBinding : interceptorBindings) {
                            interceptorBinding.member("interceptorType", interceptorType);
                        }
                    }
                    break;
                }
            }
        }

        if (!interceptorBindings.isEmpty()) {
            AnnotationValue<?>[] interceptors = interceptorBindings.stream().map(AnnotationValueBuilder::build).toArray(AnnotationValue<?>[]::new);
            AnnotationValue<Annotation> interceptorsContainer = AnnotationValue.builder(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)
                .values(interceptors)
                .build();
            annotationValue = annotationValue.mutate()
                .stereotype(interceptorsContainer)
                .build();
        }
        return List.of(annotationValue);
    }

}
